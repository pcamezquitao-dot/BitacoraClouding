from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.portal_general import PortalIdentifyIn, PortalItemOut, PortalOut
from app.services.portal_general_service import (
    PortalConfigurationError, PortalForbidden, authorize_item, load_portal,
)


router = APIRouter(prefix="/portal-general", tags=["C34 Portal General"])


def _http(error: Exception) -> HTTPException:
    return HTTPException(403 if isinstance(error, PortalForbidden) else 409, str(error))


@router.post("/identificar", response_model=PortalOut)
def identify(payload: PortalIdentifyIn, db: Session = Depends(get_db)):
    try:
        return load_portal(db, payload.codigo)
    except (PortalForbidden, PortalConfigurationError) as error:
        raise _http(error) from error


@router.get("/{codigo}/opciones/{id_accion}", response_model=PortalItemOut)
def open_option(codigo: str, id_accion: int, db: Session = Depends(get_db)):
    try:
        return authorize_item(db, codigo, id_accion)
    except (PortalForbidden, PortalConfigurationError) as error:
        raise _http(error) from error

