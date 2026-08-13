from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.db import get_db
from app.routers.bitacora_uc03 import (
    _crear_bitacora_diaria,
    _crear_observacion_area_si_aplica,
    _resolve_bao_table,
)
from app.schemas.supervisor import (
    SupervisedParticipantOut,
    SupervisorIdentifyIn,
    SupervisorMovementIn,
    SupervisorMovementOut,
    SupervisorSessionOut,
    SupervisorTodayMovementOut,
)
from app.services.supervisor_service import (
    SupervisorAuthorizationError,
    identify_supervisor,
    require_supervised_participant,
    resolve_supervisor_type_code,
    supervised_participants,
)


router = APIRouter(prefix="/supervisor", tags=["supervisor"])
COLOMBIA_TIMEZONE = timezone(timedelta(hours=-5), name="America/Bogota")


def _authorization_error(error: SupervisorAuthorizationError):
    raise HTTPException(status_code=403, detail=str(error)) from error


@router.post("/identificar", response_model=SupervisorSessionOut)
def identificar(payload: SupervisorIdentifyIn, db: Session = Depends(get_db)):
    try:
        return identify_supervisor(db, payload.codigo)
    except SupervisorAuthorizationError as error:
        _authorization_error(error)


@router.get("/{codigo}/participantes", response_model=list[SupervisedParticipantOut])
def participantes(codigo: str, search: str = "", db: Session = Depends(get_db)):
    try:
        return supervised_participants(db, codigo, search)
    except SupervisorAuthorizationError as error:
        _authorization_error(error)


@router.post("/movimientos", response_model=SupervisorMovementOut)
def registrar_movimiento(payload: SupervisorMovementIn, db: Session = Depends(get_db)):
    movement_type = payload.tipo.strip().upper()
    if movement_type not in {"ENTRADA", "SALIDA"}:
        raise HTTPException(status_code=422, detail="tipo debe ser ENTRADA o SALIDA")
    try:
        session = identify_supervisor(db, payload.codigo_supervisor)
        participant = require_supervised_participant(
            db, payload.codigo_supervisor, payload.id_participante, payload.id_area
        )
        timestamp = payload.timestamp_min or int(datetime.now().timestamp() // 60)
        qr_area = f"AREA_ADMINISTRATIVA|{payload.id_area}|{participant['area']}"
        out = _crear_bitacora_diaria(
            db=db,
            id_empleado=payload.id_participante,
            id_supervisor=session["id_supervisor"],
            ts_in_min=timestamp,
            ts_out_min=timestamp if movement_type == "SALIDA" else None,
            tipo_anotacion=4 if movement_type == "ENTRADA" else 5,
            observaciones=None,
            client_uuid=payload.client_uuid,
            qr_area=qr_area,
            origen_bitacora="SUPERVISOR",
        )
        _crear_observacion_area_si_aplica(
            db, out.id_bitacora, out.id_empleado, int(out.id_supervisor),
            out.ts_in_min, qr_area, None,
        )
        db.commit()
        return {
            "id_bitacora": out.id_bitacora,
            "id_participante": out.id_empleado,
            "id_supervisor": out.id_supervisor,
            "id_area": payload.id_area,
            "tipo": movement_type,
            "timestamp_min": timestamp,
            "client_uuid": payload.client_uuid,
        }
    except SupervisorAuthorizationError as error:
        db.rollback()
        _authorization_error(error)
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Conflicto de idempotencia")
    except HTTPException:
        db.rollback()
        raise


@router.get("/{codigo}/movimientos-hoy", response_model=list[SupervisorTodayMovementOut])
def movimientos_hoy(codigo: str, db: Session = Depends(get_db)):
    try:
        session = identify_supervisor(db, codigo)
    except SupervisorAuthorizationError as error:
        _authorization_error(error)
    bao_table = _resolve_bao_table(db)
    colombia_today = datetime.now(COLOMBIA_TIMEZONE).date()
    rows = db.execute(
        text(
            f"""
            WITH RECURSIVE areas_supervisadas AS (
                SELECT ea.id_area
                FROM {settings.EMPLEADO_AREA_TABLE} ea
                WHERE ea.id_participante=:id_supervisor
                  AND ea.cargo=:supervisor_type
                  AND ea.activo=TRUE
                  AND ea.fecha_inicia<=:today
                  AND (ea.fecha_final IS NULL OR ea.fecha_final>=:today)
                UNION
                SELECT hija.id_Area_Administrativa
                FROM {settings.AREAS_TABLE} hija
                JOIN areas_supervisadas padre ON hija.nodo_padre=padre.id_area
            )
            SELECT bd.id_bitacora, bd.id_empleado id_participante,
                   bd.id_supervisor, COALESCE(bao.id_area, ea.id_area) id_area,
                   CASE WHEN bd.tipo_anotacion=4 THEN 'ENTRADA' ELSE 'SALIDA' END tipo,
                   bd.ts_in_min timestamp_min, bd.client_uuid,
                   p.identificacion_participante codigo_participante,
                   TRIM(CONCAT_WS(' ',p.nombre,p.apellido)) nombre_completo,
                   aa.descripcion area, 'SINCRONIZADO' sync_status
            FROM {settings.BITACORA_DIARIA_TABLE} bd
            LEFT JOIN {bao_table} bao ON bao.id_bitacora=bd.id_bitacora
            LEFT JOIN {settings.EMPLEADO_AREA_TABLE} ea
              ON bao.id_area IS NULL AND ea.id_participante=bd.id_empleado
             AND ea.activo=TRUE AND ea.fecha_inicia<=:today
             AND (ea.fecha_final IS NULL OR ea.fecha_final>=:today)
            JOIN areas_supervisadas ars
              ON ars.id_area=COALESCE(bao.id_area, ea.id_area)
            JOIN {settings.PARTICIPANTE_TABLE} p ON p.id_participante=bd.id_empleado
            JOIN {settings.AREAS_TABLE} aa
              ON aa.id_Area_Administrativa=COALESCE(bao.id_area, ea.id_area)
            WHERE bd.id_supervisor=:id_supervisor
              AND bd.tipo_anotacion IN (4,5)
              AND DATE(DATE_ADD('1970-01-01 00:00:00', INTERVAL bd.ts_in_min MINUTE)
                       - INTERVAL 5 HOUR)=:today
            ORDER BY bd.ts_in_min DESC, bd.id_bitacora DESC
            """
        ),
        {
            "id_supervisor": session["id_supervisor"],
            "supervisor_type": resolve_supervisor_type_code(db),
            "today": colombia_today,
        },
    ).mappings().all()
    return [dict(row) for row in rows]
