from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class BitacoraAreaEvidenciaCreate(BaseModel):
    id_bitacora: int
    id_area: int
    ts_in_min: int = Field(ge=0)
    id_tipo_evidencia: int = Field(ge=1, le=255)
    archivo_url: str = Field(min_length=1, max_length=500)
    archivo_nombre: str | None = Field(default=None, max_length=255)
    archivo_hash: str | None = Field(default=None, max_length=64)
    mime_type: str | None = Field(default=None, max_length=100)
    duracion_seg: int | None = Field(default=None, ge=0)
    tamanio_bytes: int | None = Field(default=None, ge=0)
    orden: int | None = Field(default=None, ge=0)
    latitud: float | None = Field(default=None, ge=-90, le=90)
    longitud: float | None = Field(default=None, ge=-180, le=180)
    precision_gps: float | None = Field(default=None, ge=0)
    uuid_cliente: UUID


class BitacoraAreaEvidenciaUpdate(BaseModel):
    archivo_nombre: str | None = Field(default=None, max_length=255)
    archivo_hash: str | None = Field(default=None, max_length=64)
    mime_type: str | None = Field(default=None, max_length=100)
    duracion_seg: int | None = Field(default=None, ge=0)
    tamanio_bytes: int | None = Field(default=None, ge=0)
    orden: int | None = Field(default=None, ge=0)
    latitud: float | None = Field(default=None, ge=-90, le=90)
    longitud: float | None = Field(default=None, ge=-180, le=180)
    precision_gps: float | None = Field(default=None, ge=0)


class BitacoraAreaEvidenciaResponse(BitacoraAreaEvidenciaCreate):
    id_evidencia: int
    created_at: datetime
