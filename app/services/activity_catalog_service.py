from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.services.admin_catalog_service import _audit


DUPLICATE_MESSAGE = "Ya existe una actividad con este nombre"


def list_activities(db: Session, search: str = "") -> list[dict]:
    normalized = search.strip()
    rows = db.execute(
        text(
            """
            SELECT id_actividad, nombre, descripcion
            FROM cat_actividad
            WHERE :search = '' OR nombre LIKE CONCAT('%', :search, '%')
            ORDER BY nombre, id_actividad
            """
        ),
        {"search": normalized},
    ).mappings().all()
    return [dict(row) for row in rows]


def get_activity(db: Session, activity_id: int) -> dict | None:
    row = db.execute(
        text(
            "SELECT id_actividad, nombre, descripcion "
            "FROM cat_actividad WHERE id_actividad=:id"
        ),
        {"id": activity_id},
    ).mappings().first()
    return dict(row) if row else None


def create_activity(
    db: Session, name: str, description: str | None, identity: AdminIdentity
) -> dict:
    try:
        result = db.execute(
            text(
                "INSERT INTO cat_actividad (nombre, descripcion) "
                "VALUES (:nombre, :descripcion)"
            ),
            {"nombre": name, "descripcion": description},
        )
        created = {
            "id_actividad": int(result.lastrowid),
            "nombre": name,
            "descripcion": description,
        }
        _audit(db, "cat_actividad", str(created["id_actividad"]), "CREAR", None, created, identity)
        db.commit()
        return created
    except IntegrityError as error:
        db.rollback()
        raise ValueError(DUPLICATE_MESSAGE) from error
    except Exception:
        db.rollback()
        raise


def update_activity(
    db: Session,
    activity_id: int,
    name: str,
    description: str | None,
    identity: AdminIdentity,
) -> dict:
    try:
        before = get_activity(db, activity_id)
        if before is None:
            raise LookupError("La actividad ya no esta disponible")
        updated = db.execute(
            text(
                "UPDATE cat_actividad SET nombre=:nombre, descripcion=:descripcion "
                "WHERE id_actividad=:id"
            ),
            {"id": activity_id, "nombre": name, "descripcion": description},
        )
        if updated.rowcount != 1:
            raise LookupError("La actividad ya no esta disponible")
        after = {"id_actividad": activity_id, "nombre": name, "descripcion": description}
        _audit(db, "cat_actividad", str(activity_id), "EDITAR", before, after, identity)
        db.commit()
        return after
    except IntegrityError as error:
        db.rollback()
        raise ValueError(DUPLICATE_MESSAGE) from error
    except Exception:
        db.rollback()
        raise
