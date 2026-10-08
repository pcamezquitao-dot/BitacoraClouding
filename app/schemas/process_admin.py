from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _optional_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


class ProcessWrite(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    id_proceso: int = Field(ge=0, le=2147483647)
    nombre: str = Field(min_length=1, max_length=50)
    descripcion: str | None = Field(default=None, max_length=250)
    id_proceso_padre: int | None = Field(default=None, ge=0, le=2147483647)
    tiempo_estimado: int | None = Field(default=0, ge=0, le=2147483647)
    costo_estimado: Decimal | None = Field(default=Decimal("0.00"), ge=0, max_digits=10, decimal_places=2)
    tipo_proceso: Literal[1] = 1
    precondicion: str | None = Field(default=None, max_length=250)
    id_actividad: int | None = Field(default=None, ge=1)
    nombre_corto: str | None = Field(default=None, max_length=100)

    @field_validator("nombre")
    @classmethod
    def valid_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Ingrese el nombre del proceso")
        return value

    @field_validator("descripcion", "precondicion", "nombre_corto", mode="before")
    @classmethod
    def normalize_optional(cls, value):
        return _optional_text(value)


class ProcessUpdate(ProcessWrite):
    original_fingerprint: str = Field(min_length=64, max_length=64)


class ProcessOut(BaseModel):
    id_proceso: int
    nombre: str
    descripcion: str | None = None
    id_proceso_padre: int | None = None
    tiempo_estimado: int | None = None
    costo_estimado: Decimal | None = None
    tipo_proceso: int
    precondicion: str | None = None
    id_actividad: int | None = None
    nombre_corto: str | None = None
    nombre_padre: str | None = None
    nombre_actividad: str | None = None
    fingerprint: str


class ProcessDeleteOut(BaseModel):
    id_proceso: int
    mensaje: str
