from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.db import get_db
from app.routers.participante import participant_document_expression
from app.schemas.catalogos import OfflineCatalogOut

router = APIRouter(prefix="/catalogos", tags=["catálogos offline"])


@router.get("/offline", response_model=OfflineCatalogOut)
def catalogos_offline(db: Session = Depends(get_db)):
    participante = settings.PARTICIPANTE_TABLE
    areas = settings.AREAS_TABLE
    empleado_area = settings.EMPLEADO_AREA_TABLE
    documento = participant_document_expression(db, participante)

    participantes = db.execute(
        text(
            f"""
            SELECT id_participante, identificacion_participante, nombre, apellido,
                   {documento} AS documento, TRUE AS activo, NULL AS updated_at
            FROM {participante}
            WHERE TRIM(COALESCE(identificacion_participante, '')) <> ''
            ORDER BY id_participante
            """
        )
    ).mappings().all()
    areas_rows = db.execute(
        text(
            f"""
            SELECT id_Area_Administrativa AS id_area, descripcion,
                   nombre_corto, nodo_padre,
                   TRUE AS activo, NULL AS updated_at
            FROM {areas}
            ORDER BY id_Area_Administrativa
            """
        )
    ).mappings().all()
    asignaciones = db.execute(
        text(
            f"""
            SELECT id_empleado_area, id_participante, id_area, cargo,
                   fecha_inicia, fecha_final,
                   TRUE AS activo, NULL AS updated_at
            FROM {empleado_area}
            WHERE activo = TRUE
              AND fecha_inicia <= CURDATE()
              AND (fecha_final IS NULL OR fecha_final >= CURDATE())
            ORDER BY id_participante, id_area, cargo
            """
        )
    ).mappings().all()
    tipos_rows = db.execute(
        text(
            """
            SELECT tp.codigo, tp.descripcion, tp.activo,
                   GROUP_CONCAT(tpc.codigo_capacidad
                       ORDER BY tpc.codigo_capacidad) AS capacidades
            FROM tipos_participante AS tp
            LEFT JOIN tipo_participante_capacidad AS tpc
              ON tpc.codigo_tipo = tp.codigo
            GROUP BY tp.codigo, tp.descripcion, tp.activo
            ORDER BY tp.codigo
            """
        )
    ).mappings().all()
    calendario = db.execute(
        text(
            """
            SELECT id_periodo, id_padre, nivel, codigo, nombre,
                   fecha_inicio, fecha_fin, numero_dia_semana,
                   nombre_dia_semana, es_fin_semana, es_festivo,
                   nombre_festivo, activo
            FROM dimension_calendario
            WHERE activo = TRUE
            ORDER BY orden_periodo, fecha_inicio, id_periodo
            """
        )
    ).mappings().all()
    return {
        "generated_at": datetime.now(timezone.utc),
        "participantes": participantes,
        "areas": areas_rows,
        "empleado_areas": asignaciones,
        "tipos_participante": [
            {
                **dict(row),
                "activo": bool(row["activo"]),
                "capacidades": (
                    row["capacidades"].split(",")
                    if row["capacidades"]
                    else []
                ),
            }
            for row in tipos_rows
        ],
        "calendario": calendario,
    }
