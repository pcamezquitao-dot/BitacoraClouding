from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field, model_validator


class EstadoTranscripcion(str, Enum):
    PENDIENTE = "PENDIENTE"
    PROCESANDO = "PROCESANDO"
    COMPLETADA = "COMPLETADA"
    ERROR = "ERROR"


class TranscripcionCreate(BaseModel):
    proveedor: str | None = Field(default=None, max_length=100)
    modelo: str | None = Field(default=None, max_length=150)
    idioma: str | None = Field(default=None, max_length=20)


class TranscripcionUpdate(BaseModel):
    estado: EstadoTranscripcion
    proveedor: str | None = Field(default=None, max_length=100)
    modelo: str | None = Field(default=None, max_length=150)
    idioma: str | None = Field(default=None, max_length=20)
    confianza: float | None = Field(default=None, ge=0, le=1)
    texto_transcrito: str | None = None
    error: str | None = None

    @model_validator(mode="after")
    def validar_estado(self):
        if self.estado == EstadoTranscripcion.COMPLETADA and not (
            self.texto_transcrito and self.texto_transcrito.strip()
        ):
            raise ValueError("texto_transcrito es obligatorio para COMPLETADA")
        if self.estado == EstadoTranscripcion.ERROR and not (
            self.error and self.error.strip()
        ):
            raise ValueError("error es obligatorio para ERROR")
        return self


class TranscripcionResponse(BaseModel):
    id_transcripcion: int
    id_evidencia: int
    id_bitacora: int
    estado: EstadoTranscripcion
    proveedor: str | None = None
    modelo: str | None = None
    idioma: str | None = None
    confianza: float | None = None
    texto_transcrito: str | None = None
    numero_reintentos: int
    ultimo_error: str | None = None
    creado_en: datetime
    actualizado_en: datetime
    completado_en: datetime | None = None
