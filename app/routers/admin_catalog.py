from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.admin_catalog import (
    ParticipantTypeCreate,
    ParticipantTypeOut,
    ParticipantTypeStatusUpdate,
    ParticipantTypeUpdate,
)
from app.services.admin_catalog_service import (
    create_participant_type,
    list_participant_types,
    set_participant_type_status,
    update_participant_type,
)


router = APIRouter(
    prefix="/admin",
    tags=["administración de catálogos"],
    dependencies=[Depends(require_admin_access)],
)


@router.get("/tipos-participante", response_model=list[ParticipantTypeOut])
def obtener_tipos_participante(db: Session = Depends(get_db)):
    return list_participant_types(db)


@router.post(
    "/tipos-participante",
    response_model=ParticipantTypeOut,
    status_code=status.HTTP_201_CREATED,
)
def crear_tipo_participante(
    payload: ParticipantTypeCreate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return create_participant_type(
            db,
            payload.descripcion,
            payload.capacidades,
            identity,
        )
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error

@router.put(
    "/tipos-participante/{codigo}",
    response_model=ParticipantTypeOut,
)
def editar_tipo_participante(
    codigo: int,
    payload: ParticipantTypeUpdate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return update_participant_type(
            db,
            codigo,
            payload.descripcion,
            payload.capacidades,
            identity,
        )
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


@router.put(
    "/tipos-participante/{codigo}/estado",
    response_model=ParticipantTypeOut,
)
def cambiar_estado_tipo_participante(
    codigo: int,
    payload: ParticipantTypeStatusUpdate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return set_participant_type_status(
            db,
            codigo,
            payload.activo,
            identity,
        )
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
