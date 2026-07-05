from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.core.db import get_db
from app.services.jerarquia_service import get_supervisor_for_empleado

router = APIRouter(prefix="/empleados", tags=["empleados"])

@router.get("/{id_empleado}/supervisor")
def supervisor_de_empleado(id_empleado: int, db: Session = Depends(get_db)):
    try:
        id_sup = get_supervisor_for_empleado(db, id_empleado)
    except LookupError as e:
        raise HTTPException(status_code=404, detail=str(e))
    return {"id_empleado": id_empleado, "id_supervisor": id_sup}
