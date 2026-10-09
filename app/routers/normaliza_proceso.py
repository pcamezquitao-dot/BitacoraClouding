from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.normaliza_proceso import ApprovalOut, VersionUpdate, WorkCreate, WorkOut, WorkSummary
from app.services.normaliza_proceso_service import Cu30Conflict, Cu30NotFound, approve, create_work, get_work, list_works, regenerate, render_plantuml_svg


router = APIRouter(prefix="/admin/normaliza-proceso", tags=["CU30 Normaliza Proceso"], dependencies=[Depends(require_admin_access)])


def _call(action):
    try: return action()
    except Cu30NotFound as error: raise HTTPException(404, str(error)) from error
    except Cu30Conflict as error: raise HTTPException(409, str(error)) from error


@router.get("", response_model=list[WorkSummary])
def works(db: Session = Depends(get_db)): return list_works(db)


@router.post("", response_model=WorkOut, status_code=status.HTTP_201_CREATED)
def add(payload: WorkCreate, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    return _call(lambda: create_work(db, payload.titulo, payload.requerimiento_original, identity))


@router.get("/{work_id}", response_model=WorkOut)
def detail(work_id: str, db: Session = Depends(get_db)): return _call(lambda: get_work(db, work_id))


@router.put("/{work_id}/regenerar", response_model=WorkOut)
def update(work_id: str, payload: VersionUpdate, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    return _call(lambda: regenerate(db, work_id, payload.definicion, payload.version_esperada, identity))


@router.post("/{work_id}/aprobar", response_model=ApprovalOut)
def accept(work_id: str, version: int, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    return _call(lambda: approve(db, work_id, version, identity))


@router.get("/{work_id}/exportar/{kind}")
def export(work_id: str, kind: str, db: Session = Depends(get_db)):
    work = _call(lambda: get_work(db, work_id)); version = work["version"]
    values = {"texto": (version["texto_normalizado"], "text/plain", "txt"), "plantuml": (version["plantuml"], "text/plain", "puml"), "xml": (version["xml_definicion"], "application/xml", "xml")}
    if kind not in values: raise HTTPException(404, "Producto no disponible")
    content, media, extension = values[kind]
    return Response(content, media_type=media, headers={"Content-Disposition": f'attachment; filename="cu30-v{version["numero"]}.{extension}"'})


@router.get("/{work_id}/diagrama.svg")
def diagram(work_id: str, db: Session = Depends(get_db)):
    work = _call(lambda: get_work(db, work_id))
    svg = _call(lambda: render_plantuml_svg(work["version"]["plantuml"]))
    return Response(svg, media_type="image/svg+xml")
