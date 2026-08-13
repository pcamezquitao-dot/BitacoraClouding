import json
from datetime import date

from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.schemas.participante import ParticipantAdminIn


class ParticipantNotFound(ValueError):
    pass


class ParticipantConflict(ValueError):
    pass


def _clean(payload: ParticipantAdminIn) -> dict:
    values = payload.model_dump() if hasattr(payload, "model_dump") else payload.dict()
    for key in ("documento", "identificacion_participante", "nombre", "apellido"):
        values[key] = values[key].strip()
        if not values[key]:
            raise ValueError(f"{key.replace('_', ' ').capitalize()} es obligatorio")
    values["identificacion_participante"] = values["identificacion_participante"].upper()
    values["sexo"] = values["sexo"].strip().upper()
    if values["sexo"] not in {"M", "F"}:
        raise ValueError("Sexo debe ser M o F")
    for key in ("observaciones", "email"):
        values[key] = (values[key] or "").strip() or None
    if values["fecha_entrada"] and values["fecha_salida"] and values["fecha_salida"] < values["fecha_entrada"]:
        raise ValueError("La fecha de salida no puede ser anterior a la fecha de entrada")
    return values


def _active(row: dict) -> bool:
    return row.get("fecha_salida") is None or row["fecha_salida"] >= date.today()


def _out(row) -> dict:
    result = dict(row)
    result["activo"] = _active(result)
    return result


def list_participants(db: Session, search: str, offset: int, limit: int) -> dict:
    normalized = search.strip()
    params = {"pattern": f"%{normalized}%", "offset": offset, "limit": limit}
    where = """WHERE (:pattern='%%' OR identificacion_participante LIKE :pattern
        OR documento LIKE :pattern OR nombre LIKE :pattern OR apellido LIKE :pattern
        OR CONCAT_WS(' ', nombre, apellido) LIKE :pattern)"""
    total = db.execute(text(f"SELECT COUNT(*) FROM participante {where}"), params).scalar_one()
    rows = db.execute(text(f"""SELECT id_participante,tipo_documento,documento,
        identificacion_participante,nombre,apellido,fecha_nacimiento,sexo,
        fecha_entrada,fecha_salida,observaciones,email FROM participante {where}
        ORDER BY id_participante LIMIT :limit OFFSET :offset"""), params).mappings().all()
    return {"items": [_out(row) for row in rows], "total": total, "offset": offset, "limit": limit}


def list_document_types(db: Session) -> list[dict]:
    return [dict(r) for r in db.execute(text("SELECT tipo_documento codigo, descripcion FROM tipos_documentos ORDER BY descripcion")).mappings()]


def get_participant(db: Session, participant_id: int) -> dict:
    row = db.execute(text("""SELECT id_participante,tipo_documento,documento,
        identificacion_participante,nombre,apellido,fecha_nacimiento,sexo,
        fecha_entrada,fecha_salida,observaciones,email FROM participante
        WHERE id_participante=:id"""), {"id": participant_id}).mappings().first()
    if row is None:
        raise ParticipantNotFound("El participante no existe")
    return _out(row)


def _get_locked(db: Session, participant_id: int):
    return db.execute(text("SELECT * FROM participante WHERE id_participante=:id FOR UPDATE"), {"id": participant_id}).mappings().first()


def _audit(db: Session, key: str, operation: str, before, after, identity: AdminIdentity):
    db.execute(text("""INSERT INTO administracion_catalogo_auditoria
        (catalogo,clave_registro,operacion,valor_anterior,valor_nuevo,actor,dispositivo)
        VALUES ('participante',:key,:operation,:before,:after,:actor,:device)"""), {
        "key": key, "operation": operation,
        "before": json.dumps(dict(before), default=str) if before else None,
        "after": json.dumps(dict(after), default=str) if after else None,
        "actor": identity.actor, "device": identity.device,
    })


def create_participant(db: Session, payload: ParticipantAdminIn, identity: AdminIdentity) -> dict:
    values = _clean(payload)
    try:
        last_id = db.execute(text("SELECT id_participante FROM participante ORDER BY id_participante DESC LIMIT 1 FOR UPDATE")).scalar()
        values["id_participante"] = int(last_id or 0) + 1
        db.execute(text("""INSERT INTO participante
            (id_participante,tipo_documento,documento,identificacion_participante,nombre,apellido,
             fecha_nacimiento,sexo,fecha_entrada,fecha_salida,observaciones,email)
            VALUES (:id_participante,:tipo_documento,:documento,:identificacion_participante,:nombre,:apellido,
             :fecha_nacimiento,:sexo,:fecha_entrada,:fecha_salida,:observaciones,:email)"""), values)
        result = {**values, "activo": _active(values)}
        _audit(db, str(values["id_participante"]), "CREAR", None, result, identity)
        db.commit()
        return result
    except IntegrityError as error:
        db.rollback()
        raise ParticipantConflict("El código o documento del participante ya existe") from error
    except Exception:
        db.rollback(); raise


def update_participant(db: Session, participant_id: int, payload: ParticipantAdminIn, identity: AdminIdentity) -> dict:
    values = _clean(payload)
    try:
        before = _get_locked(db, participant_id)
        if before is None: raise ParticipantNotFound("El participante no existe")
        values["id_participante"] = participant_id
        db.execute(text("""UPDATE participante SET tipo_documento=:tipo_documento,documento=:documento,
            identificacion_participante=:identificacion_participante,nombre=:nombre,apellido=:apellido,
            fecha_nacimiento=:fecha_nacimiento,sexo=:sexo,fecha_entrada=:fecha_entrada,
            fecha_salida=:fecha_salida,observaciones=:observaciones,email=:email
            WHERE id_participante=:id_participante"""), values)
        result = {**values, "activo": _active(values)}
        _audit(db, str(participant_id), "EDITAR", before, result, identity)
        db.commit(); return result
    except IntegrityError as error:
        db.rollback(); raise ParticipantConflict("El código o documento del participante ya existe") from error
    except Exception:
        db.rollback(); raise


def retire_participant(db: Session, participant_id: int, identity: AdminIdentity) -> dict:
    try:
        before = _get_locked(db, participant_id)
        if before is None: raise ParticipantNotFound("El participante no existe")
        if before["fecha_salida"] is not None and before["fecha_salida"] <= date.today():
            raise ValueError("El participante ya está retirado")
        db.execute(text("UPDATE participante SET fecha_salida=CURRENT_DATE WHERE id_participante=:id"), {"id": participant_id})
        after = {**dict(before), "fecha_salida": date.today(), "activo": False}
        _audit(db, str(participant_id), "DESACTIVAR", before, after, identity)
        db.commit(); return after
    except Exception:
        db.rollback(); raise
