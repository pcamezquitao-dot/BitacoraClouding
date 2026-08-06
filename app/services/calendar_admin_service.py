import json

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity


class CalendarPeriodNotFound(ValueError):
    pass


class CalendarPeriodNotEditable(ValueError):
    pass


_LEVEL_ORDER = {
    "QUINQUENIO": 0,
    "ANIO": 1,
    "MES": 2,
    "DIA": 3,
}


def list_calendar_tree(db: Session) -> list[dict]:
    rows = db.execute(
        text(
            """
            SELECT id_periodo, id_padre, nivel, codigo, nombre,
                   fecha_inicio, fecha_fin, numero_dia_semana,
                   nombre_dia_semana, es_fin_semana, es_festivo,
                   nombre_festivo, orden_periodo, activo
            FROM dimension_calendario
            WHERE activo = TRUE
            ORDER BY orden_periodo, fecha_inicio, id_periodo
            """
        )
    ).mappings().all()

    nodes: dict[int, dict] = {}
    ordered: list[dict] = []
    for row in rows:
        node = {
            "id_periodo": int(row["id_periodo"]),
            "id_padre": int(row["id_padre"]) if row["id_padre"] is not None else None,
            "nivel": row["nivel"],
            "codigo": row["codigo"],
            "nombre": row["nombre"],
            "fecha_inicio": row["fecha_inicio"],
            "fecha_fin": row["fecha_fin"],
            "numero_dia_semana": row["numero_dia_semana"],
            "nombre_dia_semana": row["nombre_dia_semana"],
            "es_fin_semana": (
                bool(row["es_fin_semana"])
                if row["es_fin_semana"] is not None
                else None
            ),
            "es_festivo": bool(row["es_festivo"]),
            "nombre_festivo": row["nombre_festivo"],
            "activo": bool(row["activo"]),
            "hijos": [],
        }
        nodes[node["id_periodo"]] = node
        ordered.append(node)

    roots: list[dict] = []
    for node in ordered:
        parent = nodes.get(node["id_padre"])
        expected_parent_level = _LEVEL_ORDER.get(node["nivel"], -1) - 1
        if (
            parent is not None
            and parent["id_periodo"] != node["id_periodo"]
            and _LEVEL_ORDER.get(parent["nivel"], -1) == expected_parent_level
        ):
            parent["hijos"].append(node)
        else:
            roots.append(node)
    return roots


def update_calendar_holiday(
    db: Session,
    period_id: int,
    is_holiday: bool,
    holiday_name: str | None,
    identity: AdminIdentity,
) -> dict:
    normalized_name = (holiday_name or "").strip()
    if is_holiday and not normalized_name:
        raise ValueError("El nombre del festivo es obligatorio")
    if not is_holiday:
        normalized_name = None

    try:
        row = db.execute(
            text(
                """
                SELECT id_periodo, id_padre, nivel, codigo, nombre,
                       fecha_inicio, fecha_fin, numero_dia_semana,
                       nombre_dia_semana, es_fin_semana, es_festivo,
                       nombre_festivo, activo
                FROM dimension_calendario
                WHERE id_periodo = :id_periodo
                LIMIT 1
                FOR UPDATE
                """
            ),
            {"id_periodo": period_id},
        ).mappings().one_or_none()
        if row is None:
            raise CalendarPeriodNotFound("El período del calendario no existe")
        before = dict(row)
        if not bool(row["activo"]):
            raise CalendarPeriodNotEditable("El período del calendario está inactivo")
        if row["nivel"] != "DIA":
            raise CalendarPeriodNotEditable(
                "Solo los registros de nivel DIA pueden editarse como festivo"
            )

        db.execute(
            text(
                """
                UPDATE dimension_calendario
                SET es_festivo = :es_festivo,
                    nombre_festivo = :nombre_festivo
                WHERE id_periodo = :id_periodo
                """
            ),
            {
                "id_periodo": period_id,
                "es_festivo": is_holiday,
                "nombre_festivo": normalized_name,
            },
        )
        after = {
            **before,
            "es_festivo": is_holiday,
            "nombre_festivo": normalized_name,
        }
        db.execute(
            text(
                """
                INSERT INTO administracion_catalogo_auditoria
                    (catalogo, clave_registro, operacion, valor_anterior,
                     valor_nuevo, actor, dispositivo)
                VALUES
                    ('dimension_calendario', :clave, 'EDITAR',
                     :anterior, :nuevo, :actor, :dispositivo)
                """
            ),
            {
                "clave": str(period_id),
                "anterior": json.dumps(before, default=str),
                "nuevo": json.dumps(after, default=str),
                "actor": identity.actor,
                "dispositivo": identity.device,
            },
        )
        db.commit()
        return _serialize_calendar_day(after)
    except Exception:
        db.rollback()
        raise


def _serialize_calendar_day(row: dict) -> dict:
    return {
        "id_periodo": int(row["id_periodo"]),
        "id_padre": int(row["id_padre"]) if row["id_padre"] is not None else None,
        "nivel": row["nivel"],
        "codigo": row["codigo"],
        "nombre": row["nombre"],
        "fecha_inicio": row["fecha_inicio"],
        "fecha_fin": row["fecha_fin"],
        "numero_dia_semana": row["numero_dia_semana"],
        "nombre_dia_semana": row["nombre_dia_semana"],
        "es_fin_semana": (
            bool(row["es_fin_semana"])
            if row["es_fin_semana"] is not None
            else None
        ),
        "es_festivo": bool(row["es_festivo"]),
        "nombre_festivo": row["nombre_festivo"],
        "activo": bool(row["activo"]),
    }
