from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.work_schedules_admin import (
    WorkScheduleOut,
    WorkScheduleStatusWrite,
    WorkScheduleWrite,
)
from app.services.work_schedules_admin_service import (
    WorkScheduleConflict,
    WorkScheduleNotFound,
    change_status,
    create_schedule,
    get_schedule,
    list_schedules,
    update_schedule,
)

router = APIRouter(
    prefix="/admin/jornadas",
    tags=["jornadas de trabajo"],
)


def _translate(action):
    try:
        return action()
    except WorkScheduleNotFound as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except WorkScheduleConflict as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


@router.get("", response_model=list[WorkScheduleOut])
def list_work_schedules(
    search: str = "",
    active: bool | None = Query(None),
    db: Session = Depends(get_db),
):
    return list_schedules(db, search, active)


@router.get("/{schedule_id}", response_model=WorkScheduleOut)
def get_work_schedule(schedule_id: int, db: Session = Depends(get_db)):
    return _translate(lambda: get_schedule(db, schedule_id))


@router.post("", response_model=WorkScheduleOut, status_code=status.HTTP_201_CREATED)
def create_work_schedule(payload: WorkScheduleWrite, db: Session = Depends(get_db)):
    return _translate(lambda: create_schedule(db, payload))


@router.put("/{schedule_id}", response_model=WorkScheduleOut)
def update_work_schedule(schedule_id: int, payload: WorkScheduleWrite, db: Session = Depends(get_db)):
    return _translate(lambda: update_schedule(db, schedule_id, payload))


@router.patch("/{schedule_id}/estado", response_model=WorkScheduleOut)
def update_work_schedule_status(
    schedule_id: int,
    payload: WorkScheduleStatusWrite,
    db: Session = Depends(get_db),
):
    return _translate(lambda: change_status(db, schedule_id, payload.activo))
