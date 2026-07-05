from sqlalchemy import text
from sqlalchemy.orm import Session
from app.core.config import settings

def get_supervisor_for_empleado(db: Session, id_empleado: int) -> int:
    ea = settings.EMPLEADO_AREA_TABLE
    aa = settings.AREAS_TABLE

    sql = text(f"""
    WITH RECURSIVE ascenso AS (
        SELECT
            ea.id_participante AS id_empleado,
            ea.id_area         AS id_area_actual,
            0                  AS nivel
        FROM {ea} ea
        WHERE ea.id_participante = :id_empleado
          AND (ea.fecha_final IS NULL OR ea.fecha_final >= CURDATE())

        UNION ALL

        SELECT
            a.id_empleado,
            aa.nodo_padre      AS id_area_actual,
            a.nivel + 1        AS nivel
        FROM ascenso a
        JOIN {aa} aa
          ON aa.id_Area_Administrativa = a.id_area_actual
        WHERE aa.nodo_padre IS NOT NULL
    ),
    candidatos AS (
        SELECT
            a.id_empleado,
            ea_sup.id_participante AS id_supervisor,
            a.nivel
        FROM ascenso a
        JOIN {ea} ea_sup
          ON ea_sup.id_area = a.id_area_actual
         AND ea_sup.cargo = 3
         AND (ea_sup.fecha_final IS NULL OR ea_sup.fecha_final >= CURDATE())
    )
    SELECT id_supervisor
    FROM candidatos
    ORDER BY nivel ASC
    LIMIT 1
    """)
    row = db.execute(sql, {"id_empleado": id_empleado}).first()
    if not row or row[0] is None:
        raise LookupError("Empleado sin supervisor asignado (no se encontró supervisor vigente).")
    return int(row[0])
