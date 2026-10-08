import hashlib
import json
from decimal import Decimal

from sqlalchemy import text
from sqlalchemy.exc import IntegrityError, OperationalError
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.schemas.process_admin import ProcessWrite
from app.services.admin_catalog_service import _audit


FIELDS = (
    "id_proceso", "nombre", "descripcion", "id_proceso_padre", "tiempo_estimado",
    "costo_estimado", "tipo_proceso", "precondicion", "id_actividad", "nombre_corto",
)


class ProcessNotFound(ValueError):
    pass


class ProcessConflict(ValueError):
    pass


def _serializable(row: dict) -> dict:
    result = dict(row)
    if isinstance(result.get("costo_estimado"), Decimal):
        result["costo_estimado"] = format(result["costo_estimado"], ".2f")
    return result


def process_fingerprint(row: dict) -> str:
    payload = {key: _serializable(row).get(key) for key in FIELDS}
    return hashlib.sha256(
        json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
    ).hexdigest()


def _decorate(row) -> dict:
    result = dict(row)
    result["fingerprint"] = process_fingerprint(result)
    return result


def list_processes(db: Session) -> list[dict]:
    rows = db.execute(text("""
        SELECT p.id_proceso,p.nombre,p.descripcion,p.id_proceso_padre,p.tiempo_estimado,
               p.costo_estimado,p.tipo_proceso,p.precondicion,p.id_actividad,p.nombre_corto,
               parent.nombre AS nombre_padre,activity.nombre AS nombre_actividad
        FROM dim_proceso p
        LEFT JOIN dim_proceso parent ON parent.id_proceso=p.id_proceso_padre
        LEFT JOIN cat_actividad activity ON activity.id_actividad=p.id_actividad
        ORDER BY p.nombre,p.id_proceso
    """)).mappings().all()
    return [_decorate(row) for row in rows]


def get_process(db: Session, process_id: int) -> dict | None:
    row = db.execute(text("""
        SELECT p.id_proceso,p.nombre,p.descripcion,p.id_proceso_padre,p.tiempo_estimado,
               p.costo_estimado,p.tipo_proceso,p.precondicion,p.id_actividad,p.nombre_corto,
               parent.nombre AS nombre_padre,activity.nombre AS nombre_actividad
        FROM dim_proceso p
        LEFT JOIN dim_proceso parent ON parent.id_proceso=p.id_proceso_padre
        LEFT JOIN cat_actividad activity ON activity.id_actividad=p.id_actividad
        WHERE p.id_proceso=:id
    """), {"id": process_id}).mappings().first()
    return _decorate(row) if row else None


def _payload_dict(payload: ProcessWrite) -> dict:
    values = payload.model_dump(exclude={"original_fingerprint"})
    if values["costo_estimado"] is not None:
        values["costo_estimado"] = Decimal(values["costo_estimado"])
    return values


def _validate_links(db: Session, values: dict, editing_id: int | None = None) -> None:
    parent = values.get("id_proceso_padre")
    if parent is not None:
        if parent == values["id_proceso"]:
            raise ProcessConflict("El proceso no puede depender de sí mismo ni de sus descendientes")
        exists = db.execute(text("SELECT 1 FROM dim_proceso WHERE id_proceso=:id"), {"id": parent}).first()
        if not exists:
            raise ProcessConflict("El registro seleccionado ya no existe; actualice")
        if editing_id is not None:
            cycle = db.execute(text("""
                WITH RECURSIVE descendants AS (
                    SELECT id_proceso FROM dim_proceso WHERE id_proceso_padre=:id
                    UNION ALL
                    SELECT p.id_proceso FROM dim_proceso p
                    JOIN descendants d ON p.id_proceso_padre=d.id_proceso
                ) SELECT 1 FROM descendants WHERE id_proceso=:parent LIMIT 1
            """), {"id": editing_id, "parent": parent}).first()
            if cycle:
                raise ProcessConflict("El proceso no puede depender de sí mismo ni de sus descendientes")
    activity = values.get("id_actividad")
    if activity is not None and not db.execute(
        text("SELECT 1 FROM cat_actividad WHERE id_actividad=:id"), {"id": activity}
    ).first():
        raise ProcessConflict("El registro seleccionado ya no existe; actualice")


def _translate_integrity(error: IntegrityError) -> ProcessConflict:
    message = str(error.orig).lower()
    if "primary" in message:
        return ProcessConflict("Ya existe un proceso con este código")
    if "uq_dim_proceso_nombre_corto" in message:
        return ProcessConflict("Ya existe un proceso con esta referencia")
    if "uq_dim_proceso_nombre" in message:
        return ProcessConflict("Ya existe un proceso con este nombre")
    return ProcessConflict("No fue posible guardar el proceso por una restricción de integridad")


def create_process(db: Session, payload: ProcessWrite, identity: AdminIdentity) -> dict:
    values = _payload_dict(payload)
    try:
        existing = get_process(db, payload.id_proceso)
        if existing:
            same = all(_serializable(existing).get(key) == _serializable(values).get(key) for key in FIELDS)
            if same:
                return existing
            raise ProcessConflict("Ya existe un proceso con este código")
        _validate_links(db, values)
        db.execute(text("""
            INSERT INTO dim_proceso
            (id_proceso,nombre,descripcion,id_proceso_padre,tiempo_estimado,costo_estimado,
             tipo_proceso,precondicion,id_actividad,nombre_corto)
            VALUES (:id_proceso,:nombre,:descripcion,:id_proceso_padre,:tiempo_estimado,
                    :costo_estimado,:tipo_proceso,:precondicion,:id_actividad,:nombre_corto)
        """), values)
        _audit(db, "dim_proceso", str(payload.id_proceso), "CREAR", None, _serializable(values), identity)
        db.commit()
        return get_process(db, payload.id_proceso)
    except (IntegrityError, OperationalError) as error:
        db.rollback()
        existing = get_process(db, payload.id_proceso)
        if existing and all(_serializable(existing).get(k) == _serializable(values).get(k) for k in FIELDS):
            return existing
        if isinstance(error, IntegrityError):
            raise _translate_integrity(error) from error
        raise ProcessConflict("El proceso cambió durante el guardado; actualice") from error
    except Exception:
        db.rollback()
        raise


def update_process(db: Session, process_id: int, payload, identity: AdminIdentity) -> dict:
    if process_id != payload.id_proceso:
        raise ProcessConflict("El código del proceso es inmutable")
    try:
        before = get_process(db, process_id)
        if before is None:
            raise ProcessNotFound("El proceso ya no está disponible")
        if before["fingerprint"] != payload.original_fingerprint:
            raise ProcessConflict("El proceso cambió mientras se editaba; actualice")
        values = _payload_dict(payload)
        _validate_links(db, values, process_id)
        updated = db.execute(text("""
            UPDATE dim_proceso SET nombre=:nombre,descripcion=:descripcion,
              id_proceso_padre=:id_proceso_padre,tiempo_estimado=:tiempo_estimado,
              costo_estimado=:costo_estimado,tipo_proceso=:tipo_proceso,
              precondicion=:precondicion,id_actividad=:id_actividad,nombre_corto=:nombre_corto
            WHERE id_proceso=:id_proceso
        """), values)
        if updated.rowcount != 1:
            raise ProcessNotFound("El proceso ya no está disponible")
        after = get_process(db, process_id)
        _audit(db, "dim_proceso", str(process_id), "EDITAR", _serializable(before), _serializable(after), identity)
        db.commit()
        return get_process(db, process_id)
    except IntegrityError as error:
        db.rollback()
        raise _translate_integrity(error) from error
    except Exception:
        db.rollback()
        raise


def process_dependencies(db: Session, process_id: int) -> list[str]:
    dependencies = []
    children = db.execute(text("SELECT COUNT(*) FROM dim_proceso WHERE id_proceso_padre=:id"), {"id": process_id}).scalar_one()
    if children:
        dependencies.append("subprocesos")
    incoming = db.execute(text("""
        SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
        WHERE REFERENCED_TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='dim_proceso'
          AND NOT (TABLE_NAME='dim_proceso' AND COLUMN_NAME='id_proceso_padre')
    """)).all()
    for table_name, column_name in incoming:
        count = db.execute(text(f"SELECT COUNT(*) FROM `{table_name}` WHERE `{column_name}`=:id"), {"id": process_id}).scalar_one()
        if count:
            dependencies.append(f"{table_name}.{column_name} ({count})")
    return dependencies


def delete_process(db: Session, process_id: int, identity: AdminIdentity) -> None:
    try:
        before = get_process(db, process_id)
        if before is None:
            raise ProcessNotFound("El proceso ya no está disponible")
        dependencies = process_dependencies(db, process_id)
        if "subprocesos" in dependencies:
            raise ProcessConflict("No se puede eliminar: el proceso tiene subprocesos")
        if dependencies:
            raise ProcessConflict("No se puede eliminar: el proceso está referenciado: " + ", ".join(dependencies))
        db.execute(text("DELETE FROM dim_proceso WHERE id_proceso=:id"), {"id": process_id})
        _audit(db, "dim_proceso", str(process_id), "ELIMINAR", _serializable(before), None, identity)
        db.commit()
    except IntegrityError as error:
        db.rollback()
        raise ProcessConflict("No se puede eliminar: el proceso está referenciado") from error
    except Exception:
        db.rollback()
        raise
