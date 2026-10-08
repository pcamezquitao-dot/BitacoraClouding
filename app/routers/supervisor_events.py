from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.supervisor_event import SupervisorEventIn, SupervisorEventOut
from app.services.supervisor_service import SupervisorAuthorizationError, identify_supervisor, require_supervised_participant

router = APIRouter(prefix="/supervisor/novedades", tags=["supervisor novedades"])
TABLE = "supervisor_novedad"


def serialize_row(row, supervisor_code: str) -> dict:
    result = dict(row) | {"codigo_supervisor": supervisor_code}
    for field in ("hora_inicio", "hora_final"):
        value = result.get(field)
        if isinstance(value, timedelta):
            seconds = int(value.total_seconds()) % 86400
            result[field] = (datetime.min + timedelta(seconds=seconds)).time()
    return result


def calculate_event(payload: SupervisorEventIn) -> tuple[int | None, int | None]:
    if payload.tipo_novedad not in (1, 2, 7):
        raise ValueError("tipo_novedad debe ser 1, 2 o 7")
    if payload.fecha_final < payload.fecha_inicio:
        raise ValueError("La fecha final no puede ser anterior a la inicial")
    if (payload.hora_inicio is None) != (payload.hora_final is None):
        raise ValueError("Debe registrar ambas horas o dejarlas vacÃ­as")
    days = (payload.fecha_final - payload.fecha_inicio).days + 1 if payload.tipo_novedad == 2 else None
    minutes = None
    if payload.tipo_novedad == 7:
        if payload.hora_inicio is None or payload.hora_final is None:
            raise ValueError("Las horas inicial y final son obligatorias")
        start = datetime.combine(payload.fecha_inicio, payload.hora_inicio)
        end = datetime.combine(payload.fecha_final, payload.hora_final)
        if end <= start and payload.fecha_inicio == payload.fecha_final:
            end += timedelta(days=1)
        minutes = int((end - start).total_seconds() // 60)
        if minutes <= 0:
            raise ValueError("La autorizaciÃ³n debe ser mayor que cero")
    if payload.tipo_novedad == 1 and payload.hora_inicio and payload.hora_final \
            and payload.fecha_inicio == payload.fecha_final and payload.hora_final < payload.hora_inicio:
        raise ValueError("La hora final no puede ser anterior a la inicial")
    return days, minutes


@router.post("", response_model=SupervisorEventOut)
def create_event(payload: SupervisorEventIn, db: Session = Depends(get_db)):
    try:
        days, minutes = calculate_event(payload)
        session = identify_supervisor(db, payload.codigo_supervisor)
        require_supervised_participant(db, payload.codigo_supervisor, payload.id_participante, payload.id_area)
    except ValueError as error:
        raise HTTPException(422, str(error)) from error
    except SupervisorAuthorizationError as error:
        raise HTTPException(403, str(error)) from error
    existing = db.execute(text(f"SELECT * FROM {TABLE} WHERE client_uuid=:uuid"), {"uuid": payload.client_uuid}).mappings().first()
    if existing:
        return serialize_row(existing, payload.codigo_supervisor)
    if payload.tipo_novedad == 7:
        overlap = db.execute(text(f"""
            SELECT 1 FROM {TABLE} WHERE tipo_novedad=7 AND estado='ACTIVO'
              AND id_participante=:participant AND fecha_inicio=:date
              AND hora_inicio<:end AND hora_final>:start LIMIT 1
        """), {"participant": payload.id_participante, "date": payload.fecha_inicio,
                "start": payload.hora_inicio, "end": payload.hora_final}).first()
        if overlap:
            raise HTTPException(409, "Existe una autorizaciÃ³n superpuesta")
    values = payload.model_dump(exclude={"codigo_supervisor"}) | {
        "id_supervisor": session["id_supervisor"], "dias_calculados": days,
        "minutos_autorizados": minutes, "estado": "ACTIVO", "origen": "SUPERVISOR",
    }
    db.execute(text(f"""
        INSERT INTO {TABLE} (client_uuid,tipo_novedad,id_participante,id_supervisor,id_area,
          fecha_inicio,fecha_final,hora_inicio,hora_final,dias_calculados,minutos_autorizados,
          observaciones,estado,origen)
        VALUES (:client_uuid,:tipo_novedad,:id_participante,:id_supervisor,:id_area,
          :fecha_inicio,:fecha_final,:hora_inicio,:hora_final,:dias_calculados,:minutos_autorizados,
          :observaciones,:estado,:origen)
    """), values)
    row = db.execute(text(f"SELECT * FROM {TABLE} WHERE client_uuid=:client_uuid"), values).mappings().one()
    db.commit()
    return serialize_row(row, payload.codigo_supervisor)
