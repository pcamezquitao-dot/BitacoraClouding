from datetime import date

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings


SUPERVISOR_TYPE_DESCRIPTION = "supervisor"


class SupervisorAuthorizationError(ValueError):
    pass


def resolve_supervisor_type_code(db: Session) -> int:
    """Resuelve una vez el código funcional cuyo nombre es supervisor.

    empleado_area.cargo referencia tipos_participante.codigo. El modo Supervisor
    no usa tipo_participante_capacidad, por decisión funcional del sistema.
    """
    rows = db.execute(
        text(
            """
            SELECT codigo
            FROM tipos_participante
            WHERE activo=TRUE AND LOWER(TRIM(descripcion))=:descripcion
            ORDER BY codigo
            """
        ),
        {"descripcion": SUPERVISOR_TYPE_DESCRIPTION},
    ).scalars().all()
    if len(rows) != 1:
        raise SupervisorAuthorizationError(
            "El catálogo debe contener exactamente un tipo activo llamado supervisor"
        )
    return int(rows[0])


def identify_supervisor(db: Session, code: str) -> dict:
    participant = settings.PARTICIPANTE_TABLE
    employee_area = settings.EMPLEADO_AREA_TABLE
    areas = settings.AREAS_TABLE
    supervisor_type = resolve_supervisor_type_code(db)
    rows = db.execute(
        text(
            f"""
            SELECT p.id_participante, p.identificacion_participante,
                   TRIM(CONCAT_WS(' ', p.nombre, p.apellido)) nombre_completo,
                   ea.id_area, aa.descripcion area
            FROM {participant} p
            JOIN {employee_area} ea ON ea.id_participante=p.id_participante
            JOIN {areas} aa ON aa.id_Area_Administrativa=ea.id_area
            WHERE UPPER(TRIM(p.identificacion_participante))=:codigo
              AND ea.cargo=:supervisor_type
              AND ea.activo=TRUE
              AND ea.fecha_inicia<=CURDATE()
              AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE())
              AND (p.fecha_salida IS NULL OR p.fecha_salida>=CURDATE())
            ORDER BY ea.id_area
            """
        ),
        {"codigo": code.strip().upper(), "supervisor_type": supervisor_type},
    ).mappings().all()
    if not rows:
        raise SupervisorAuthorizationError(
            "El participante no existe, está inactivo o no tiene cargo supervisor vigente"
        )
    first = rows[0]
    return {
        "id_supervisor": int(first["id_participante"]),
        "codigo": first["identificacion_participante"],
        "nombre_completo": first["nombre_completo"],
        "estado": "Supervisor identificado",
        "areas": [{"id_area": int(row["id_area"]), "area": row["area"]} for row in rows],
    }


def supervised_participants(db: Session, code: str, search: str = "") -> list[dict]:
    session = identify_supervisor(db, code)
    participant = settings.PARTICIPANTE_TABLE
    employee_area = settings.EMPLEADO_AREA_TABLE
    areas = settings.AREAS_TABLE
    pattern = f"%{search.strip().upper()}%"
    rows = db.execute(
        text(
            f"""
            WITH RECURSIVE areas_supervisadas AS (
                SELECT ea.id_area
                FROM {employee_area} ea
                WHERE ea.id_participante=:id_supervisor
                  AND ea.cargo=:supervisor_type
                  AND ea.activo=TRUE
                  AND ea.fecha_inicia<=CURDATE()
                  AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE())
                UNION
                SELECT hija.id_Area_Administrativa
                FROM {areas} hija
                JOIN areas_supervisadas padre ON hija.nodo_padre=padre.id_area
            )
            SELECT DISTINCT p.id_participante,
                   p.identificacion_participante codigo,
                   p.nombre, p.apellido, ea.id_area, aa.descripcion area
            FROM areas_supervisadas ars
            JOIN {employee_area} ea ON ea.id_area=ars.id_area
            JOIN {participant} p ON p.id_participante=ea.id_participante
            JOIN {areas} aa ON aa.id_Area_Administrativa=ea.id_area
            WHERE ea.activo=TRUE
              AND ea.fecha_inicia<=CURDATE()
              AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE())
              AND (p.fecha_salida IS NULL OR p.fecha_salida>=CURDATE())
              AND (:pattern='%%' OR UPPER(p.identificacion_participante) LIKE :pattern
                   OR UPPER(COALESCE(p.nombre,'')) LIKE :pattern
                   OR UPPER(COALESCE(p.apellido,'')) LIKE :pattern)
            ORDER BY p.id_participante, ea.id_area
            """
        ),
        {
            "id_supervisor": session["id_supervisor"],
            "supervisor_type": resolve_supervisor_type_code(db),
            "pattern": pattern,
        },
    ).mappings().all()
    return [dict(row) for row in rows]


def require_supervised_participant(db: Session, code: str, participant_id: int, area_id: int) -> dict:
    matches = [
        row for row in supervised_participants(db, code)
        if int(row["id_participante"]) == participant_id and int(row["id_area"]) == area_id
    ]
    if not matches:
        raise SupervisorAuthorizationError(
            "El participante no está activo o no pertenece al área supervisada"
        )
    return matches[0]


def require_supervised_participant_by_id(
    db: Session, supervisor_id: int, participant_id: int, area_id: int
) -> dict:
    code = db.execute(
        text(
            f"SELECT identificacion_participante FROM {settings.PARTICIPANTE_TABLE} "
            "WHERE id_participante=:id LIMIT 1"
        ),
        {"id": supervisor_id},
    ).scalar_one_or_none()
    if not code:
        raise SupervisorAuthorizationError("El supervisor no existe")
    return require_supervised_participant(db, str(code), participant_id, area_id)
