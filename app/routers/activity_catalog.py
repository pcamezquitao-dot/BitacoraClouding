from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.activity_catalog import ActivityCatalogIn, ActivityCatalogOut
from app.services.activity_catalog_service import (
    create_activity,
    get_activity,
    list_activities,
    update_activity,
)


router = APIRouter(
    prefix="/admin/actividades",
    tags=["catalogo de actividades"],
    dependencies=[Depends(require_admin_access)],
)


@router.get("", response_model=list[ActivityCatalogOut])
def activities(search: str = Query("", max_length=100), db: Session = Depends(get_db)):
    return list_activities(db, search)


@router.get("/{activity_id}", response_model=ActivityCatalogOut)
def activity_detail(activity_id: int, db: Session = Depends(get_db)):
    result = get_activity(db, activity_id)
    if result is None:
        raise HTTPException(404, "La actividad ya no esta disponible")
    return result


@router.post("", response_model=ActivityCatalogOut, status_code=status.HTTP_201_CREATED)
def add_activity(
    payload: ActivityCatalogIn,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return create_activity(db, payload.nombre, payload.descripcion, identity)
    except ValueError as error:
        raise HTTPException(409, str(error)) from error


@router.put("/{activity_id}", response_model=ActivityCatalogOut)
def edit_activity(
    activity_id: int,
    payload: ActivityCatalogIn,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return update_activity(
            db, activity_id, payload.nombre, payload.descripcion, identity
        )
    except LookupError as error:
        raise HTTPException(404, str(error)) from error
    except ValueError as error:
        raise HTTPException(409, str(error)) from error
