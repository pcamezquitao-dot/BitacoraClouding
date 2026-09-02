from calendar import monthrange
from datetime import date, datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.db import get_db
from app.schemas.worker import WorkerIdentifyIn, WorkerSessionOut, WorkerTimeOut
from app.services.worker_time_service import COLOMBIA, calculate_days


router = APIRouter(prefix="/trabajador", tags=["trabajador"])


def _identify(db: Session, raw: str):
    code = raw.strip().upper()
    row = db.execute(text(f"""
        SELECT DISTINCT p.id_participante, p.identificacion_participante codigo,
               TRIM(CONCAT_WS(' ', p.nombre, p.apellido)) nombre_completo
        FROM {settings.PARTICIPANTE_TABLE} p
        JOIN {settings.EMPLEADO_AREA_TABLE} ea ON ea.id_participante=p.id_participante
        WHERE UPPER(p.identificacion_participante)=:code
          AND (p.fecha_salida IS NULL OR p.fecha_salida>=CURRENT_DATE)
          AND ea.activo=TRUE AND ea.fecha_inicia<=CURRENT_DATE
          AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURRENT_DATE)
        LIMIT 1
    """), {"code": code}).mappings().first()
    if not row:
        raise HTTPException(403, "El participante no tiene una asignación activa como trabajador")
    return dict(row)


@router.post("/identificar", response_model=WorkerSessionOut)
def identify_worker(payload: WorkerIdentifyIn, db: Session = Depends(get_db)):
    return _identify(db, payload.codigo)


@router.get("/{codigo}/tiempo", response_model=WorkerTimeOut)
def worker_time(codigo: str, anio: int = Query(ge=2020, le=2100),
                mes: int = Query(ge=1, le=12), db: Session = Depends(get_db)):
    worker = _identify(db, codigo)
    start = datetime(anio, mes, 1, tzinfo=COLOMBIA)
    end = datetime(anio + (mes == 12), 1 if mes == 12 else mes + 1, 1, tzinfo=COLOMBIA)
    start_min = int((start - timedelta(days=1)).timestamp() // 60)
    end_min = int((end + timedelta(days=1)).timestamp() // 60)
    rows = db.execute(text(f"""
        SELECT id_bitacora, ts_in_min, tipo_anotacion, client_uuid
        FROM {settings.BITACORA_DIARIA_TABLE}
        WHERE id_empleado=:participant AND tipo_anotacion IN (4,5)
          AND ts_in_min>=:start_min AND ts_in_min<:end_min
        ORDER BY ts_in_min, id_bitacora
    """), {"participant": worker["id_participante"], "start_min": start_min, "end_min": end_min}).mappings().all()
    calendar_rows = db.execute(text("""
        SELECT fecha_inicio fecha, numero_dia_semana dia_semana,
               (numero_dia_semana=6) sabado, (numero_dia_semana=7) domingo,
               es_festivo festivo, nombre_festivo
        FROM dimension_calendario
        WHERE nivel='DIA' AND activo=TRUE AND fecha_inicio BETWEEN :first AND :last
    """), {"first": start.date(), "last": date(anio, mes, monthrange(anio, mes)[1])}).mappings().all()
    calendar = {row["fecha"]: dict(row) for row in calendar_rows}
    return {"id_participante": worker["id_participante"], "anio": anio, "mes": mes,
            "dias": calculate_days(anio, mes, rows, calendar)}
