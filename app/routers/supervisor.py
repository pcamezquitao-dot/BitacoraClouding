from datetime import datetime

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
    supervised_participants,
)


router = APIRouter(prefix="/supervisor", tags=["supervisor"])


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
    rows = db.execute(
        text(
            f"""
            SELECT bd.id_bitacora, bd.id_empleado id_participante,
                   bd.id_supervisor, bao.id_area,
                   CASE WHEN bd.tipo_anotacion=4 THEN 'ENTRADA' ELSE 'SALIDA' END tipo,
                   bd.ts_in_min timestamp_min, bd.client_uuid,
                   p.identificacion_participante codigo_participante,
                   TRIM(CONCAT_WS(' ',p.nombre,p.apellido)) nombre_completo,
                   aa.descripcion area
            FROM {settings.BITACORA_DIARIA_TABLE} bd
            JOIN {bao_table} bao ON bao.id_bitacora=bd.id_bitacora
            JOIN {settings.PARTICIPANTE_TABLE} p ON p.id_participante=bd.id_empleado
            JOIN {settings.AREAS_TABLE} aa ON aa.id_Area_Administrativa=bao.id_area
            WHERE bd.id_supervisor=:id_supervisor
              AND bd.tipo_anotacion IN (4,5)
              AND bd.fecha_in=CURDATE()
            ORDER BY bd.ts_in_min DESC, bd.id_bitacora DESC
            """
        ),
        {"id_supervisor": session["id_supervisor"]},
    ).mappings().all()
    return [dict(row) for row in rows]
