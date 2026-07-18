from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.empleado_area import EmpleadoAreaActivaOut
from app.services.empleado_area_service import get_asignacion_activa, get_asignaciones_activas

router = APIRouter(prefix="/empleado-area", tags=["empleado_area"])


@router.get("/{id_participante}/activas", response_model=list[EmpleadoAreaActivaOut])
def obtener_asignaciones_activas(id_participante: int, db: Session = Depends(get_db)):
    return get_asignaciones_activas(db, id_participante)


@router.get("/{id_participante}/activa", response_model=EmpleadoAreaActivaOut)
def obtener_asignacion_activa(id_participante: int, db: Session = Depends(get_db)):
    asignacion = get_asignacion_activa(db, id_participante)
    if not asignacion:
        raise HTTPException(
            status_code=404,
            detail="El participante no tiene una asignación de área activa",
        )
    return asignacion
