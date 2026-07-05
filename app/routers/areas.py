from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session
from app.core.db import get_db
from app.core.config import settings
from app.schemas.areas import AreaByQrIn, AreaOut
from app.services.qr_service import parse_area_qr

router = APIRouter(prefix="/areas", tags=["areas"])

@router.post("/by_qr", response_model=AreaOut)
def area_by_qr(payload: AreaByQrIn, db: Session = Depends(get_db)):
    try:
        parsed = parse_area_qr(payload.qr)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    t = settings.AREAS_TABLE
    sql = text(f"""
        SELECT id_Area_Administrativa AS id_area, descripcion
        FROM {t}
        WHERE id_Area_Administrativa = :id_area
        LIMIT 1
    """)
    row = db.execute(sql, {"id_area": parsed.id_area}).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Área administrativa no encontrada.")
    return row
