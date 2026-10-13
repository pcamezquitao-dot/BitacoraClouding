import hashlib
import json

from sqlalchemy import text
from sqlalchemy.exc import IntegrityError, OperationalError
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.schemas.navigation_admin import NavigationWrite
from app.services.admin_catalog_service import _audit

FIELDS = ("id_accion", "tipo_accion", "nombre", "descripcion", "id_accion_padre", "id_proceso")


class NavigationNotFound(ValueError):
    pass


class NavigationConflict(ValueError):
    pass


def action_fingerprint(row: dict) -> str:
    payload = {key: dict(row).get(key) for key in FIELDS}
    return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()


def _decorate(row) -> dict:
    result = dict(row)
    result["fingerprint"] = action_fingerprint(result)
    return result


def list_actions(db: Session) -> list[dict]:
    rows = db.execute(text("""
        SELECT a.id_accion,a.tipo_accion,a.nombre,a.descripcion,a.id_accion_padre,a.id_proceso,
               parent.nombre AS nombre_padre,p.nombre AS nombre_proceso
        FROM dim_accion a
        LEFT JOIN dim_accion parent ON parent.id_accion=a.id_accion_padre
        LEFT JOIN dim_proceso p ON p.id_proceso=a.id_proceso
        ORDER BY a.nombre,a.id_accion
    """)).mappings().all()
    return [_decorate(row) for row in rows]


def get_action(db: Session, action_id: int) -> dict | None:
    row = db.execute(text("""
        SELECT a.id_accion,a.tipo_accion,a.nombre,a.descripcion,a.id_accion_padre,a.id_proceso,
               parent.nombre AS nombre_padre,p.nombre AS nombre_proceso
        FROM dim_accion a
        LEFT JOIN dim_accion parent ON parent.id_accion=a.id_accion_padre
        LEFT JOIN dim_proceso p ON p.id_proceso=a.id_proceso
        WHERE a.id_accion=:id
    """), {"id": action_id}).mappings().first()
    return _decorate(row) if row else None


def _values(payload: NavigationWrite) -> dict:
    return payload.model_dump(exclude={"request_id", "original_fingerprint"})


def _validate_links(db: Session, values: dict, editing_id: int | None = None) -> None:
    parent = values.get("id_accion_padre")
    if parent is not None:
        if parent == editing_id:
            raise NavigationConflict("La acción no puede depender de sí misma ni de sus descendientes")
        if not db.execute(text("SELECT 1 FROM dim_accion WHERE id_accion=:id"), {"id": parent}).first():
            raise NavigationConflict("La acción padre ya no existe; actualice")
        if editing_id is not None:
            cycle = db.execute(text("""
                WITH RECURSIVE descendants AS (
                    SELECT id_accion FROM dim_accion WHERE id_accion_padre=:id
                    UNION ALL
                    SELECT a.id_accion FROM dim_accion a JOIN descendants d ON a.id_accion_padre=d.id_accion
                ) SELECT 1 FROM descendants WHERE id_accion=:parent LIMIT 1
            """), {"id": editing_id, "parent": parent}).first()
            if cycle:
                raise NavigationConflict("La acción no puede depender de sí misma ni de sus descendientes")
    process = values.get("id_proceso")
    if process is not None and not db.execute(text("SELECT 1 FROM dim_proceso WHERE id_proceso=:id"), {"id": process}).first():
        raise NavigationConflict("El proceso seleccionado ya no existe; actualice")


def _request_result(db: Session, request_id: str) -> dict | None:
    row = db.execute(text("""
        SELECT clave_registro FROM administracion_catalogo_auditoria
        WHERE catalogo='dim_accion' AND operacion='CREAR' AND actor LIKE :marker
        ORDER BY id_auditoria DESC LIMIT 1
    """), {"marker": f"%[{request_id}]%"}).first()
    return get_action(db, int(row[0])) if row else None


def create_action(db: Session, payload: NavigationWrite, identity: AdminIdentity) -> dict:
    existing = _request_result(db, payload.request_id)
    if existing:
        return existing
    values = _values(payload)
    _validate_links(db, values)
    try:
        result = db.execute(text("""
            INSERT INTO dim_accion(tipo_accion,nombre,descripcion,id_accion_padre,id_proceso)
            VALUES(:tipo_accion,:nombre,:descripcion,:id_accion_padre,:id_proceso)
        """), values)
        action_id = int(result.lastrowid)
        marked_identity = AdminIdentity(f"{identity.actor} [{payload.request_id}]", identity.device)
        _audit(db, "dim_accion", str(action_id), "CREAR", None, values, marked_identity)
        db.commit()
        return get_action(db, action_id)
    except (IntegrityError, OperationalError) as error:
        db.rollback()
        recovered = _request_result(db, payload.request_id)
        if recovered:
            return recovered
        raise NavigationConflict("No fue posible guardar la acción por una restricción de integridad") from error
    except Exception:
        db.rollback()
        raise


def update_action(db: Session, action_id: int, payload, identity: AdminIdentity) -> dict:
    locked = db.execute(
        text("SELECT id_accion FROM dim_accion WHERE id_accion=:id FOR UPDATE"),
        {"id": action_id},
    ).first()
    if locked is None:
        raise NavigationNotFound("La acción ya no está disponible")
    before = get_action(db, action_id)
    if before["fingerprint"] != payload.original_fingerprint:
        db.rollback()
        raise NavigationConflict("La acción cambió mientras se editaba; actualice")
    values = _values(payload)
    _validate_links(db, values, action_id)
    try:
        updated = db.execute(text("""
            UPDATE dim_accion SET tipo_accion=:tipo_accion,nombre=:nombre,descripcion=:descripcion,
              id_accion_padre=:id_accion_padre,id_proceso=:id_proceso WHERE id_accion=:id
        """), {**values, "id": action_id})
        if updated.rowcount != 1:
            raise NavigationNotFound("La acción ya no está disponible")
        after = get_action(db, action_id)
        _audit(db, "dim_accion", str(action_id), "EDITAR", before, after, identity)
        db.commit()
        return get_action(db, action_id)
    except IntegrityError as error:
        db.rollback()
        raise NavigationConflict("No fue posible guardar la acción por una restricción de integridad") from error
    except Exception:
        db.rollback()
        raise


def action_dependencies(db: Session, action_id: int) -> list[str]:
    children = db.execute(text("SELECT COUNT(*) FROM dim_accion WHERE id_accion_padre=:id"), {"id": action_id}).scalar_one()
    return ["acciones hijas"] if children else []


def delete_action(db: Session, action_id: int, identity: AdminIdentity) -> None:
    before = get_action(db, action_id)
    if before is None:
        raise NavigationNotFound("La acción ya no está disponible")
    dependencies = action_dependencies(db, action_id)
    if dependencies:
        raise NavigationConflict("No se puede eliminar una acción que tiene acciones hijas")
    try:
        db.execute(text("DELETE FROM dim_accion WHERE id_accion=:id"), {"id": action_id})
        _audit(db, "dim_accion", str(action_id), "ELIMINAR", before, None, identity)
        db.commit()
    except IntegrityError as error:
        db.rollback()
        raise NavigationConflict("No se puede eliminar: la acción está referenciada") from error
    except Exception:
        db.rollback()
        raise
