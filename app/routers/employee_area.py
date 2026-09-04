"""Empleado–Área con control temporal en la aplicación Android.

Este router no constituye autenticación segura de servidor. Android solamente
expone el maestro cuando el ambiente activo es Administrador.
"""

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.db import get_db
from app.schemas.admin_catalog import (
    EmployeeAreaAssignmentOut,
    EmployeeAreaCreate,
    EmployeeAreaOut,
    EmployeeAreaTreeNodeOut,
    EmployeeAreaUpdate,
    ParticipantOptionOut,
    ParticipantTypeOut,
)
from app.services.admin_catalog_service import (
    AdministrativeAreaNotFound,
    create_employee_area,
    list_employee_area_tree,
    list_participant_options,
    list_participant_types,
    retire_employee_area,
    update_employee_area,
)


router = APIRouter(prefix="/admin", tags=["empleado–área"])


def employee_admin_identity(
    actor: str | None = Header(None, alias="X-Admin-Actor"),
    device: str | None = Header(None, alias="X-Admin-Device"),
) -> AdminIdentity:
    normalized_actor = (actor or "").strip()
    return AdminIdentity(normalized_actor, (device or "").strip() or None)


@router.get(
    "/empleado-area/tree",
    response_model=list[EmployeeAreaTreeNodeOut],
)
def obtener_arbol_empleado_area(db: Session = Depends(get_db)):
    return list_employee_area_tree(db)


@router.get(
    "/empleado-area/tipos-participante",
    response_model=list[ParticipantTypeOut],
)
def obtener_tipos_empleado_area(db: Session = Depends(get_db)):
    return list_participant_types(db)


@router.get(
    "/participantes/options",
    response_model=list[ParticipantOptionOut],
)
def obtener_opciones_participantes(
    search: str = "",
    db: Session = Depends(get_db),
):
    return list_participant_options(db, search)


@router.post(
    "/empleado-area",
    response_model=EmployeeAreaOut,
    status_code=status.HTTP_201_CREATED,
)
def crear_asignacion_empleado_area(
    payload: EmployeeAreaCreate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(employee_admin_identity),
):
    try:
        return create_employee_area(db, payload, identity)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


@router.put(
    "/empleado-area/{id_asignacion}",
    response_model=EmployeeAreaAssignmentOut,
)
def editar_asignacion_empleado_area(
    id_asignacion: int,
    payload: EmployeeAreaUpdate,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(employee_admin_identity),
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
    identity: AdminIdentity = Depends(employee_admin_identity),
):
    try:
        retire_employee_area(db, id_asignacion, identity)
    except AdministrativeAreaNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
