from datetime import datetime

from pydantic import BaseModel, Field


class FaceTemplateEnrollIn(BaseModel):
    client_uuid: str | None = Field(default=None, max_length=36)
    id_participante: int
    participant_code: str = Field(min_length=1, max_length=100)
    display_name: str = Field(min_length=1, max_length=255)
    embedding_base64: str = Field(min_length=1)
    embedding_sha256: str | None = Field(default=None, min_length=64, max_length=64)
    model_version: str = Field(min_length=1, max_length=100)
    encryption_version: str = Field(default="server-aesgcm-v1", max_length=50)
    created_by: str | None = Field(default=None, max_length=100)
    device_id: str | None = Field(default=None, max_length=255)


class FaceTemplateMetadataOut(BaseModel):
    id_face_template: int
    id_participante: int
    participant_code: str
    display_name: str
    embedding_sha256: str
    model_version: str
    encryption_version: str
    enrolled_at: datetime
    active: bool
    device_id: str | None = None
    updated_at: datetime | None = None


class FaceTemplateAuthorizedOut(FaceTemplateMetadataOut):
    embedding_base64: str


class FaceTemplateDeactivateIn(BaseModel):
    revoked_by: str | None = Field(default=None, max_length=100)
    revocation_reason: str | None = Field(default=None, max_length=255)


class FaceTemplateSyncStatusOut(BaseModel):
    active_templates: int
    templates_for_device: int
    latest_update: datetime | None = None
