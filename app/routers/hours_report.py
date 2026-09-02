from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.services.hours_report_service import build_management_aggregate
from app.services.management_service import identify_management

router = APIRouter(prefix="/gerencia", tags=["informes gerenciales"])


@router.get("/{codigo}/informes/horas-laboradas/agregado")
def aggregate_hours_report(codigo: str, desde: date, hasta: date,
                           id_area: int | None = Query(None, ge=1),
                           db: Session = Depends(get_db)):
    identify_management(db, codigo)
    try:
        return build_management_aggregate(db, desde, hasta, id_area)
    except ValueError as error:
        raise HTTPException(422, str(error)) from error
