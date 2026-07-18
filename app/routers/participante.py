import logging

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import inspect, text
from sqlalchemy.exc import NoInspectionAvailable
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.config import settings
from app.schemas.participante import ParticipanteOut

router = APIRouter(tags=["Participante"])
logger = logging.getLogger(__name__)


def normalize_participant_code(value: str) -> str:
    return value.strip().upper()


def participant_document_expression(db: Session, table: str) -> str:
    try:
        columns = {column["name"].lower() for column in inspect(db.bind).get_columns(table)}
    except NoInspectionAvailable:
        columns = set()
    return "documento" if "documento" in columns else "NULL"

@router.get("/participante/search", response_model=list[ParticipanteOut])
def search_participantes(q: str, db: Session = Depends(get_db)):
    term = normalize_participant_code(q)
    if not term:
        return []
    table = settings.PARTICIPANTE_TABLE
    document = participant_document_expression(db, table)
    pattern = f"%{term}%"
    query = text(f"""
        SELECT
            id_participante,
            nombre,
            apellido,
            identificacion_participante,
            {document} AS documento
        FROM {table}
        WHERE UPPER(TRIM(COALESCE(identificacion_participante, ''))) = :exact_code
           OR UPPER(COALESCE(nombre, '')) LIKE :pattern
           OR UPPER(COALESCE(apellido, '')) LIKE :pattern
           OR UPPER(CONCAT_WS(' ', nombre, apellido)) LIKE :pattern
        ORDER BY nombre, apellido, id_participante
        LIMIT 25
    """)
    rows = db.execute(
        query,
        {"exact_code": term, "pattern": pattern},
    ).mappings().all()
    logger.info(
        "Búsqueda participante: recibido=%r normalizado=%r fuente=%s resultados=%d",
        q,
        term,
        table,
        len(rows),
    )
    return rows

@router.get("/participante/by_qr/{qr}", response_model=ParticipanteOut)
def get_participante_by_qr(qr: str, db: Session = Depends(get_db)):
    normalized = normalize_participant_code(qr)
    table = settings.PARTICIPANTE_TABLE
    document = participant_document_expression(db, table)
    query = text(f"""
        SELECT
            id_participante,
            nombre,
            apellido,
            identificacion_participante,
            {document} AS documento
        FROM {table}
        WHERE UPPER(TRIM(COALESCE(identificacion_participante, ''))) = :qr
        LIMIT 1
    """)

    row = db.execute(query, {"qr": normalized}).mappings().first()
    logger.info(
        "QR participante: recibido=%r normalizado=%r fuente=%s resultados=%d",
        qr,
        normalized,
        table,
        1 if row else 0,
    )

    if not row:
        raise HTTPException(
            status_code=404,
            detail=f"Participante no encontrado para código={normalized}"
        )

    # row es un dict-like gracias a mappings()
    return row
