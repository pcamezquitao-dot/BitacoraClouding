from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.navigation_admin import NavigationDeleteOut, NavigationOut, NavigationUpdate, NavigationWrite
from app.services.navigation_admin_service import NavigationConflict, NavigationNotFound, create_action, delete_action, get_action, list_actions, update_action

router = APIRouter(prefix="/admin/navegacion", tags=["C35 navegación"], dependencies=[Depends(require_admin_access)])

@router.get("", response_model=list[NavigationOut])
def actions(db: Session = Depends(get_db)):
    return list_actions(db)

@router.get("/{action_id}", response_model=NavigationOut)
def action_detail(action_id: int, db: Session = Depends(get_db)):
    result = get_action(db, action_id)
    if result is None:
        raise HTTPException(404, "La acción ya no está disponible")
    return result

@router.post("", response_model=NavigationOut, status_code=status.HTTP_201_CREATED)
def add_action(payload: NavigationWrite, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    try:
        return create_action(db, payload, identity)
    except NavigationConflict as error:
        raise HTTPException(409, str(error)) from error

@router.put("/{action_id}", response_model=NavigationOut)
def edit_action(action_id: int, payload: NavigationUpdate, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    try:
        return update_action(db, action_id, payload, identity)
    except NavigationNotFound as error:
        raise HTTPException(404, str(error)) from error
    except NavigationConflict as error:
        raise HTTPException(409, str(error)) from error

@router.delete("/{action_id}", response_model=NavigationDeleteOut)
def remove_action(action_id: int, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    try:
        delete_action(db, action_id, identity)
        return {"id_accion": action_id, "mensaje": "Acción eliminada correctamente"}
    except NavigationNotFound as error:
        raise HTTPException(404, str(error)) from error
    except NavigationConflict as error:
        raise HTTPException(409, str(error)) from error
