import hashlib
import json
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
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


def payload_hash(payload: SupervisorEventIn) -> str:
    canonical = json.dumps(payload.model_dump(mode="json"), sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _find_by_client_uuid(db: Session, client_uuid: str):
    return db.execute(text(f"SELECT * FROM {TABLE} WHERE client_uuid=:uuid"), {"uuid": client_uuid}).mappings().first()


def _return_existing(existing, payload: SupervisorEventIn):
    stored_hash = existing.get("request_sha256")
    if stored_hash and stored_hash != payload_hash(payload):
        raise HTTPException(409, "client_uuid ya fue usado con un contenido diferente")
    return serialize_row(existing, payload.codigo_supervisor)


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
    existing = _find_by_client_uuid(db, payload.client_uuid)
    if existing:
        return _return_existing(existing, payload)
    if payload.tipo_novedad == 7:
        overlap = db.execute(text(f"""
            SELECT 1 FROM {TABLE} WHERE tipo_novedad=7 AND estado='ACTIVO'
              AND id_participante=:participant AND fecha_inicio=:date
              AND hora_inicio<:end AND hora_final>:start LIMIT 1
        """), {"participant": payload.id_participante, "date": payload.fecha_inicio,
                "start": payload.hora_inicio, "end": payload.hora_final}).first()
        if overlap:
            raise HTTPException(409, "Existe una autorizaciÃ³n superpuesta")
    occurrence = datetime.combine(payload.fecha_inicio, payload.hora_inicio or datetime.min.time())
    try:
        log_result = db.execute(text("""
            INSERT INTO bitacora_diaria
              (id_empleado,id_supervisor,ts_in_min,tipo_anotacion,observaciones,
               fecha_in,hora_in,client_uuid,origen_bitacora)
            VALUES (:employee,:supervisor,:ts,NULL,:observations,:date,:time,:uuid,'SUPERVISOR')
        """), {"employee": payload.id_participante, "supervisor": session["id_supervisor"],
                 "ts": int(occurrence.timestamp() // 60), "date": payload.fecha_inicio,
                 "time": payload.hora_inicio or datetime.min.time(), "uuid": payload.client_uuid,
                 "observations": (payload.observaciones or f"Novedad tipo {payload.tipo_novedad}")[:200]})
        values = payload.model_dump(exclude={"codigo_supervisor"}) | {
            "id_supervisor": session["id_supervisor"], "dias_calculados": days,
            "minutos_autorizados": minutes, "estado": "ACTIVO", "origen": "SUPERVISOR",
            "request_sha256": payload_hash(payload), "id_bitacora_registrada": int(log_result.lastrowid),
        }
        db.execute(text(f"""
            INSERT INTO {TABLE} (client_uuid,tipo_novedad,id_participante,id_supervisor,id_area,
              fecha_inicio,fecha_final,hora_inicio,hora_final,dias_calculados,minutos_autorizados,
              observaciones,estado,origen,request_sha256,id_bitacora_registrada)
            VALUES (:client_uuid,:tipo_novedad,:id_participante,:id_supervisor,:id_area,
              :fecha_inicio,:fecha_final,:hora_inicio,:hora_final,:dias_calculados,:minutos_autorizados,
              :observaciones,:estado,:origen,:request_sha256,:id_bitacora_registrada)
        """), values)
        row = _find_by_client_uuid(db, payload.client_uuid)
        db.commit()
    except IntegrityError:
        db.rollback()
        row = _find_by_client_uuid(db, payload.client_uuid)
        if row is None:
            raise
        return _return_existing(row, payload)
    except Exception:
        db.rollback()
        raise
    return serialize_row(row, payload.codigo_supervisor)
