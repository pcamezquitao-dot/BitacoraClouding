from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.admin_catalog import (
    EmployeeAreaCreate,
    EmployeeAreaAssignmentOut,
    EmployeeAreaOut,
    EmployeeAreaTreeNodeOut,
    EmployeeAreaUpdate,
    ParticipantOptionOut,
    ParticipantTypeCreate,
    ParticipantTypeOut,
    ParticipantTypeStatusUpdate,
    ParticipantTypeUpdate,
)
from app.services.admin_catalog_service import (
    create_employee_area,
    create_participant_type,
    list_participant_types,
    list_employee_area_tree,
    list_participant_options,
    retire_employee_area,
    set_participant_type_status,
    update_participant_type,
    update_employee_area,
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


@router.get(
    "/empleado-area/tree",
    response_model=list[EmployeeAreaTreeNodeOut],
)
def obtener_arbol_empleado_area(db: Session = Depends(get_db)):
    return list_employee_area_tree(db)


@router.get(
    "/participantes/options",
    response_model=list[ParticipantOptionOut],
)
def obtener_opciones_participantes(
    search: str = "",
    db: Session = Depends(get_db),
):
    return list_participant_options(db, search)


@router.put(
    "/empleado-area/{id_asignacion}",
    response_model=EmployeeAreaAssignmentOut,
)
def editar_asignacion_empleado_area(
    id_asignacion: int,
    payload: EmployeeAreaUpdate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return update_employee_area(db, id_asignacion, payload, identity)
    except AdministrativeAreaNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


@router.delete(
    "/empleado-area/{id_asignacion}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def retirar_asignacion_empleado_area(
    id_asignacion: int,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        retire_employee_area(db, id_asignacion, identity)
    except AdministrativeAreaNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
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


@router.post(
    "/empleado-area",
    response_model=EmployeeAreaOut,
    status_code=status.HTTP_201_CREATED,
)
def crear_asignacion_empleado_area(
    payload: EmployeeAreaCreate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(require_admin_access),
):
    try:
        return create_employee_area(db, payload, identity)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
