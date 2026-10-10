from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.bpm_motor import BpmDefinitionOut, BpmOperationIn, BpmOperationOut, BpmSyncIn, BpmSyncOut
from app.services.bpm_motor_service import BpmConflict, BpmError, BpmForbidden, case_detail, list_definitions, list_tasks, metrics, process_operation


router = APIRouter(prefix="/bpm", tags=["C32 Motor BPM"])


def _error(error):
    if isinstance(error, BpmForbidden): return HTTPException(403, str(error))
    if isinstance(error, BpmConflict): return HTTPException(409, str(error))
    return HTTPException(422, str(error))


@router.get("/definitions", response_model=list[BpmDefinitionOut])
def definitions(db: Session = Depends(get_db)):
    try: return list_definitions(db)
    except BpmError as error: raise _error(error) from error


@router.get("/tasks")
def tasks(actor: str = Query(min_length=1, max_length=50), status: str | None = None, db: Session = Depends(get_db)):
    try: return list_tasks(db, actor, status)
    except BpmError as error: raise _error(error) from error


@router.get("/cases/{case_id}")
def get_case(case_id: int, actor: str = Query(min_length=1, max_length=50), db: Session = Depends(get_db)):
    try: return case_detail(db, case_id, actor)
    except BpmError as error: raise _error(error) from error


@router.get("/metrics")
def get_metrics(actor: str = Query(min_length=1, max_length=50), process_id: int | None = None,
                db: Session = Depends(get_db)):
    try: return metrics(db, actor, process_id)
    except BpmError as error: raise _error(error) from error


@router.post("/operations", response_model=BpmOperationOut)
def operation(payload: BpmOperationIn, db: Session = Depends(get_db)):
    try: return process_operation(db, payload)
    except BpmError as error: raise _error(error) from error
    except IntegrityError as error:
        db.rollback(); raise HTTPException(409, "Conflicto de integridad al procesar la operación") from error


@router.post("/sync", response_model=BpmSyncOut)
def sync(payload: BpmSyncIn, db: Session = Depends(get_db)):
    results = []
    blocked = False
    for item in payload.operations:
        if blocked:
            results.append({"operation_uuid": item.operation_uuid, "status": "CONFLICT",
                            "message": "Dependencia anterior no confirmada", "server_time": __import__('datetime').datetime.utcnow()})
            continue
        try:
            results.append(process_operation(db, item))
        except (BpmError, IntegrityError) as error:
            db.rollback(); blocked = True
            results.append({"operation_uuid": item.operation_uuid,
                            "status": "CONFLICT" if isinstance(error, (BpmConflict, IntegrityError)) else "REJECTED",
                            "message": str(error), "server_time": __import__('datetime').datetime.utcnow()})
    return {"results": results}
