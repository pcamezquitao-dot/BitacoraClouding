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
                   TRUE AS activo, NULL AS updated_at
            FROM {areas}
            ORDER BY id_Area_Administrativa
            """
        )
    ).mappings().all()
    asignaciones = db.execute(
        text(
            f"""
            SELECT id_participante, id_area, cargo,
                   fecha_final, TRUE AS activo, NULL AS updated_at
            FROM {empleado_area}
            WHERE fecha_final IS NULL OR fecha_final >= CURDATE()
            ORDER BY id_participante, id_area, cargo
            """
        )
    ).mappings().all()
    return {
        "generated_at": datetime.now(timezone.utc),
        "participantes": participantes,
        "areas": areas_rows,
        "empleado_areas": asignaciones,
    }
