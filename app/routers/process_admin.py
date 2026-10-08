from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.process_admin import ProcessDeleteOut, ProcessOut, ProcessUpdate, ProcessWrite
from app.services.process_admin_service import (
    ProcessConflict, ProcessNotFound, create_process, delete_process, get_process,
    list_processes, update_process,
)

router = APIRouter(
    prefix="/admin/procesos",
    tags=["administración proceso"],
    dependencies=[Depends(require_admin_access)],
)


@router.get("", response_model=list[ProcessOut])
def processes(db: Session = Depends(get_db)):
    return list_processes(db)


@router.get("/{process_id}", response_model=ProcessOut)
def process_detail(process_id: int, db: Session = Depends(get_db)):
    result = get_process(db, process_id)
    if result is None:
        raise HTTPException(404, "El proceso ya no está disponible")
    return result


@router.post("", response_model=ProcessOut, status_code=status.HTTP_201_CREATED)
def add_process(payload: ProcessWrite, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    try:
        return create_process(db, payload, identity)
    except ProcessConflict as error:
        raise HTTPException(409, str(error)) from error


@router.put("/{process_id}", response_model=ProcessOut)
def edit_process(process_id: int, payload: ProcessUpdate, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    try:
        return update_process(db, process_id, payload, identity)
    except ProcessNotFound as error:
        raise HTTPException(404, str(error)) from error
    except ProcessConflict as error:
        raise HTTPException(409, str(error)) from error


@router.delete("/{process_id}", response_model=ProcessDeleteOut)
def remove_process(process_id: int, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    try:
        delete_process(db, process_id, identity)
        return {"id_proceso": process_id, "mensaje": "Proceso eliminado correctamente"}
    except ProcessNotFound as error:
        raise HTTPException(404, str(error)) from error
    except ProcessConflict as error:
        raise HTTPException(409, str(error)) from error
