from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings


def get_asignacion_activa(db: Session, id_participante: int):
    """Devuelve la asignacion vigente aplicando la regla ya usada por el backend."""
    tabla = settings.EMPLEADO_AREA_TABLE
    areas = settings.AREAS_TABLE
    return db.execute(
        text(f"""
            SELECT ea.id_participante, ea.id_area, aa.descripcion AS area_descripcion,
                   ea.cargo, ea.fecha_final
            FROM {tabla} ea
            LEFT JOIN {areas} aa ON aa.id_Area_Administrativa = ea.id_area
            WHERE ea.id_participante = :id_participante
              AND (ea.fecha_final IS NULL OR ea.fecha_final >= CURDATE())
            ORDER BY ea.fecha_final IS NULL DESC, ea.fecha_final DESC
            LIMIT 1
        """),
        {"id_participante": id_participante},
    ).mappings().first()


def require_asignacion_activa(db: Session, id_participante: int, rol: str):
    asignacion = get_asignacion_activa(db, id_participante)
    if not asignacion:
        raise LookupError(f"El {rol} no tiene una asignación de área activa")
    return asignacion
