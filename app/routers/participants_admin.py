"""Maestro temporalmente restringido por el ambiente Administrador de Android.

X-Admin-Actor identifica al responsable, pero no sustituye autenticación de servidor.
"""
from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.db import get_db
from app.schemas.participante import DocumentTypeOut, ParticipantAdminIn, ParticipantAdminOut, ParticipantAdminPage
from app.services.participant_admin_service import ParticipantConflict, ParticipantNotFound, create_participant, get_participant, list_document_types, list_participants, retire_participant, update_participant

router = APIRouter(prefix="/admin/participantes", tags=["participantes-admin"])

def participant_admin_identity(actor: str | None = Header(None, alias="X-Admin-Actor"), device: str | None = Header(None, alias="X-Admin-Device")) -> AdminIdentity:
    if not (actor or "").strip(): raise HTTPException(status_code=422, detail="Identifique al administrador")
    return AdminIdentity(actor.strip(), (device or "").strip() or None)

@router.get("", response_model=ParticipantAdminPage)
def get_participants(search: str = "", offset: int = Query(0, ge=0), limit: int = Query(50, ge=1, le=100), db: Session = Depends(get_db)):
    return list_participants(db, search, offset, limit)

@router.get("/tipos-documento", response_model=list[DocumentTypeOut])
def get_document_types(db: Session = Depends(get_db)):
    return list_document_types(db)

@router.get("/{participant_id}", response_model=ParticipantAdminOut)
def get_participant_detail(participant_id: int, db: Session = Depends(get_db)):
    try: return get_participant(db, participant_id)
    except ParticipantNotFound as error: raise HTTPException(status_code=404, detail=str(error)) from error

@router.post("", response_model=ParticipantAdminOut, status_code=status.HTTP_201_CREATED)
def post_participant(payload: ParticipantAdminIn, db: Session = Depends(get_db), identity: AdminIdentity = Depends(participant_admin_identity)):
    try: return create_participant(db, payload, identity)
    except ParticipantConflict as error: raise HTTPException(status_code=409, detail=str(error)) from error
    except ValueError as error: raise HTTPException(status_code=400, detail=str(error)) from error

@router.put("/{participant_id}", response_model=ParticipantAdminOut)
def put_participant(participant_id: int, payload: ParticipantAdminIn, db: Session = Depends(get_db), identity: AdminIdentity = Depends(participant_admin_identity)):
    try: return update_participant(db, participant_id, payload, identity)
    except ParticipantNotFound as error: raise HTTPException(status_code=404, detail=str(error)) from error
    except ParticipantConflict as error: raise HTTPException(status_code=409, detail=str(error)) from error
    except ValueError as error: raise HTTPException(status_code=400, detail=str(error)) from error

@router.delete("/{participant_id}", response_model=ParticipantAdminOut)
def delete_participant(participant_id: int, db: Session = Depends(get_db), identity: AdminIdentity = Depends(participant_admin_identity)):
    try: return retire_participant(db, participant_id, identity)
    except ParticipantNotFound as error: raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error: raise HTTPException(status_code=409, detail=str(error)) from error
