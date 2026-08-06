"""Áreas administrativas con control temporal en la aplicación Android.

Este router no representa autenticación segura de servidor. Android solamente
expone el maestro cuando el ambiente activo es Administrador.
"""

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.db import get_db
from app.schemas.admin_catalog import (
    AdministrativeAreaOut,
    AdministrativeAreaWrite,
    AreaTreeNodeOut,
)
from app.services.admin_catalog_service import (
    AdministrativeAreaNotFound,
    create_administrative_area,
    delete_administrative_area,
    list_area_tree,
    update_administrative_area,
)


router = APIRouter(prefix="/admin/areas", tags=["áreas administrativas"])


def area_admin_identity(
    actor: str | None = Header(None, alias="X-Admin-Actor"),
    device: str | None = Header(None, alias="X-Admin-Device"),
) -> AdminIdentity:
    normalized_actor = (actor or "").strip()
    if not normalized_actor:
        raise HTTPException(status_code=422, detail="Identifique al administrador")
    return AdminIdentity(normalized_actor, (device or "").strip() or None)


@router.get("/arbol", response_model=list[AreaTreeNodeOut])
def obtener_arbol_areas(db: Session = Depends(get_db)):
    return list_area_tree(db)


@router.post("", response_model=AdministrativeAreaOut, status_code=status.HTTP_201_CREATED)
def crear_area_administrativa(
    payload: AdministrativeAreaWrite,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(area_admin_identity),
):
    try:
        return create_administrative_area(db, payload, identity)
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


@router.put("/{id_area}", response_model=AdministrativeAreaOut)
def editar_area_administrativa(
    id_area: int,
    payload: AdministrativeAreaWrite,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(area_admin_identity),
):
    try:
        return update_administrative_area(db, id_area, payload, identity)
    except AdministrativeAreaNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


@router.delete("/{id_area}", status_code=status.HTTP_204_NO_CONTENT)
def eliminar_area_administrativa(
    id_area: int,
    db: Session = Depends(get_db),
    identity: AdminIdentity = Depends(area_admin_identity),
):
    try:
        delete_administrative_area(db, id_area, identity)
    except AdministrativeAreaNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
