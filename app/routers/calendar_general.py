"""Calendario general con control temporal de acceso en la aplicación Android.

Estos endpoints no constituyen autenticación administrativa de servidor. La
aplicación solo expone el módulo cuando su ambiente activo es Administrador.
"""

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.db import get_db
from app.schemas.admin_catalog import (
    CalendarDayOut,
    CalendarHolidayUpdate,
    CalendarTreeNodeOut,
)
from app.services.calendar_admin_service import (
    CalendarPeriodNotEditable,
    CalendarPeriodNotFound,
    list_calendar_tree,
    update_calendar_holiday,
)


router = APIRouter(prefix="/admin/calendario", tags=["calendario general"])


@router.get("/arbol", response_model=list[CalendarTreeNodeOut])
def obtener_arbol_calendario(db: Session = Depends(get_db)):
    return list_calendar_tree(db)


@router.patch("/dias/{id_periodo}/festivo", response_model=CalendarDayOut)
def editar_festivo_calendario(
    id_periodo: int,
    payload: CalendarHolidayUpdate,
    db: Session = Depends(get_db),
    actor: str | None = Header(None, alias="X-Admin-Actor"),
    device: str | None = Header(None, alias="X-Admin-Device"),
):
    normalized_actor = (actor or "").strip()
    if not normalized_actor:
        raise HTTPException(status_code=422, detail="Identifique al administrador")
    identity = AdminIdentity(normalized_actor, (device or "").strip() or None)
    try:
        return update_calendar_holiday(
            db,
            id_periodo,
            payload.es_festivo,
            payload.nombre_festivo,
            identity,
        )
    except CalendarPeriodNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except CalendarPeriodNotEditable as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
