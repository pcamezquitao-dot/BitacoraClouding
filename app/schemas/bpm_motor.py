from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator


class BpmEvidenceIn(BaseModel):
    client_uuid: str = Field(min_length=1, max_length=40)
    filename: str = Field(min_length=1, max_length=255)
    mime_type: str = Field(min_length=1, max_length=100)
    sha256: str = Field(pattern=r"^[0-9a-fA-F]{64}$")
    content_base64: str = Field(min_length=1)
    captured_at: datetime | None = None


class BpmOperationIn(BaseModel):
    operation_uuid: str = Field(min_length=1, max_length=40)
    dependency_uuid: str | None = Field(None, max_length=40)
    operation_type: Literal[
        "OPEN_CASE", "START_TASK", "COMPLETE_TASK", "SUSPEND_CASE",
        "RESUME_CASE", "REASSIGN_TASK", "CANCEL_CASE"
    ]
    actor_code: str = Field(min_length=1, max_length=50)
    device: str | None = Field(None, max_length=100)
    captured_at: datetime | None = None
    base_revision: int | None = Field(None, ge=1)
    process_id: int | None = Field(None, ge=1)
    definition_sha256: str | None = Field(None, pattern=r"^[0-9a-fA-F]{64}$")
    case_id: int | None = Field(None, ge=1)
    task_id: int | None = Field(None, ge=1)
    case_client_uuid: str | None = Field(None, max_length=40)
    task_client_uuid: str | None = Field(None, max_length=40)
    successor_client_uuid: str | None = Field(None, max_length=40)
    subject: str | None = Field(None, max_length=200)
    affected_code: str | None = Field(None, max_length=50)
    source_type: Literal["MANUAL", "NOVEDAD"] | None = None
    source_id: str | None = Field(None, max_length=100)
    source_table: Literal["novedades", "supervisor_novedad"] | None = None
    responsible_id: int | None = None
    result: str | None = Field(None, max_length=100)
    observations: str | None = None
    reason: str | None = None
    evidences: list[BpmEvidenceIn] = Field(default_factory=list, max_length=20)

    @field_validator("actor_code", "affected_code")
    @classmethod
    def normalize_codes(cls, value):
        return value.strip().upper() if value else value


class BpmOperationOut(BaseModel):
    operation_uuid: str
    status: Literal["CONFIRMED", "CONFLICT", "REJECTED"]
    case_id: int | None = None
    task_id: int | None = None
    next_task_id: int | None = None
    case_client_uuid: str | None = None
    task_client_uuid: str | None = None
    successor_client_uuid: str | None = None
    case_state: str | None = None
    task_state: str | None = None
    case_revision: int | None = None
    task_revision: int | None = None
    message: str | None = None
    server_time: datetime


class BpmSyncIn(BaseModel):
    operations: list[BpmOperationIn] = Field(min_length=1, max_length=100)


class BpmSyncOut(BaseModel):
    results: list[BpmOperationOut]


class BpmDefinitionOut(BaseModel):
    id_bpm_proceso: int
    nombre: str
    version: int
    descripcion: str | None
    activo: bool
    sha256: str
    definition: dict
