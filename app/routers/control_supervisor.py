"""Endpoint aislado para la vista CONTROL del supervisor."""

from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.supervisor_auth import (
    SupervisorIdentity, issue_supervisor_token, require_supervisor_identity,
)
from app.schemas.control_supervisor import (
    ControlBitacoraOut, ControlObservationUpdateIn, ControlObservationUpdateOut,
    ControlSupervisorReportOut, ControlSupervisorSessionIn, ControlSupervisorSessionOut,
)
from app.services.control_supervisor_service import (
    control_day_bitacoras, control_supervisor_report_for_identity,
    update_control_observation,
)
from app.services.supervisor_service import identify_supervisor
from app.services.supervisor_service import SupervisorAuthorizationError


router = APIRouter(prefix="/control/supervisor", tags=["control-supervisor"])


@router.post("/session", response_model=ControlSupervisorSessionOut)
def crear_sesion(payload: ControlSupervisorSessionIn, db: Session = Depends(get_db)):
    try:
        supervisor = identify_supervisor(db, payload.codigo)
        token, expires = issue_supervisor_token(
            supervisor["id_supervisor"], supervisor["codigo"],
            [area["id_area"] for area in supervisor["areas"]],
        )
        return {"access_token": token, "expires_at": expires, "supervisor": supervisor}
    except SupervisorAuthorizationError as error:
        raise HTTPException(status_code=403, detail=str(error)) from error


@router.get("/me", response_model=ControlSupervisorReportOut)
def consultar_control(anio: int = Query(ge=2020, le=2100), mes: int = Query(ge=1, le=12),
                      identity: SupervisorIdentity = Depends(require_supervisor_identity),
                      db: Session = Depends(get_db)):
    try:
        return control_supervisor_report_for_identity(db, identity, anio, mes)
    except SupervisorAuthorizationError as error:
        raise HTTPException(status_code=403, detail=str(error)) from error


@router.get("/me/participantes/{participant_id}/bitacoras", response_model=list[ControlBitacoraOut])
def listar_bitacoras_dia(participant_id: int, fecha: date,
                         identity: SupervisorIdentity = Depends(require_supervisor_identity),
                         db: Session = Depends(get_db)):
    try:
        return control_day_bitacoras(db, identity, participant_id, fecha)
    except SupervisorAuthorizationError as error:
        raise HTTPException(status_code=403, detail=str(error)) from error


@router.patch("/me/bitacoras/{bitacora_id}/observaciones", response_model=ControlObservationUpdateOut)
def actualizar_observacion(bitacora_id: int, payload: ControlObservationUpdateIn,
                           identity: SupervisorIdentity = Depends(require_supervisor_identity),
                           db: Session = Depends(get_db)):
    try:
        result = update_control_observation(
            db, identity, bitacora_id, payload.observacion_anterior, payload.observacion_nueva
        )
        db.commit()
        return result
    except SupervisorAuthorizationError as error:
        db.rollback()
        raise HTTPException(status_code=403, detail=str(error)) from error
    except LookupError as error:
        db.rollback()
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(error)) from error
    except RuntimeError as error:
        db.rollback()
        if str(error) == "CONFLICTO_OBSERVACION":
            raise HTTPException(status_code=409, detail="La observacion cambio en el servidor") from error
        raise
    except Exception:
        db.rollback()
        raise
