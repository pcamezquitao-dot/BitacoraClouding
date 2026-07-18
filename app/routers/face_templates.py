import logging

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.face_auth import require_face_sync_access
from app.schemas.face_template import (
    FaceTemplateAuthorizedOut,
    FaceTemplateDeactivateIn,
    FaceTemplateEnrollIn,
    FaceTemplateMetadataOut,
    FaceTemplateSyncStatusOut,
)
from app.services.face_template_service import (
    deactivate,
    enroll_or_replace,
    get_active_for_participant,
    list_authorized_active,
    sync_status,
)

router = APIRouter(
    prefix="/face-templates",
    tags=["Face templates"],
    dependencies=[Depends(require_face_sync_access)],
)
logger = logging.getLogger("bitacora.sync")


@router.post("/enroll", response_model=FaceTemplateMetadataOut)
def enroll(payload: FaceTemplateEnrollIn, db: Session = Depends(get_db)):
    logger.info(
        "face enroll local_uuid=%s participant_id=%s endpoint=/face-templates/enroll",
        payload.client_uuid,
        payload.id_participante,
    )
    try:
        result = enroll_or_replace(db, payload)
        db.commit()
        logger.info(
            "face enrolled local_uuid=%s participant_id=%s server_id=%s status=committed",
            payload.client_uuid,
            payload.id_participante,
            result["id_face_template"],
        )
        return result
    except LookupError as error:
        db.rollback()
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(error)) from error
    except Exception as error:
        db.rollback()
        logger.exception(
            "face enroll failed local_uuid=%s participant_id=%s error=%s",
            payload.client_uuid,
            payload.id_participante,
            type(error).__name__,
        )
        raise


@router.get(
    "/participant/{id_participante}/active",
    response_model=FaceTemplateMetadataOut,
)
def active_for_participant(id_participante: int, db: Session = Depends(get_db)):
    row = get_active_for_participant(db, id_participante)
    if not row:
        raise HTTPException(status_code=404, detail="El participante no tiene plantilla activa")
    return row


@router.get("/authorized/active", response_model=list[FaceTemplateAuthorizedOut])
def authorized_active(db: Session = Depends(get_db)):
    return list_authorized_active(db)


@router.patch("/{id_face_template}/deactivate", status_code=status.HTTP_204_NO_CONTENT)
def deactivate_template(
    id_face_template: int,
    payload: FaceTemplateDeactivateIn,
    db: Session = Depends(get_db),
):
    if not deactivate(
        db,
        id_face_template,
        payload.revoked_by,
        payload.revocation_reason,
    ):
        db.rollback()
        raise HTTPException(status_code=404, detail="Plantilla activa no encontrada")
    db.commit()


@router.get("/sync/status", response_model=FaceTemplateSyncStatusOut)
def get_sync_status(device_id: str | None = None, db: Session = Depends(get_db)):
    row = sync_status(db, device_id)
    return {
        "active_templates": (row or {}).get("active_templates") or 0,
        "templates_for_device": (row or {}).get("templates_for_device") or 0,
        "latest_update": (row or {}).get("latest_update"),
    }
