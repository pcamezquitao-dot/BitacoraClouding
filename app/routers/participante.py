from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.db import get_db

router = APIRouter(tags=["Participante"])

@router.get("/participante/by_qr/{qr}")
def get_participante_by_qr(qr: str, db: Session = Depends(get_db)):
    query = text("""
        SELECT
            id_participante,
            identificacion_participante
        FROM participante
        WHERE identificacion_participante = :qr
        LIMIT 1
    """)

    row = db.execute(query, {"qr": qr}).mappings().first()

    if not row:
        raise HTTPException(
            status_code=404,
            detail=f"Participante no encontrado para qr={qr}"
        )

    # row es un dict-like gracias a mappings()
    return row
