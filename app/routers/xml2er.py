from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.core.db import get_db
from app.schemas.xml2er import Xml2ErImportIn, Xml2ErPreview, Xml2ErResult
from app.services.xml2er_service import Xml2ErConflict, Xml2ErError, import_definition, preview


router = APIRouter(prefix="/admin/xml2er", tags=["C31 XML2ER"], dependencies=[Depends(require_admin_access)])


def _http(action):
    try:
        return action()
    except Xml2ErConflict as error:
        raise HTTPException(409, str(error)) from error
    except Xml2ErError as error:
        raise HTTPException(422, str(error)) from error


@router.post("/validar", response_model=Xml2ErPreview)
async def validate_xml(file: UploadFile = File(...), db: Session = Depends(get_db)):
    raw = await file.read()
    return _http(lambda: preview(db, raw, file.filename or "proceso.xml")[1])


@router.post("/importar", response_model=Xml2ErResult)
def import_xml(payload: Xml2ErImportIn, db: Session = Depends(get_db), identity: AdminIdentity = Depends(require_admin_access)):
    return _http(lambda: import_definition(db, payload.xml.encode("utf-8"), payload.archivo, payload.token_validacion, identity))
