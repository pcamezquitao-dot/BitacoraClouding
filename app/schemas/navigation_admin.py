from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class NavigationWrite(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    tipo_accion: Literal[1] = 1
    nombre: str = Field(min_length=1, max_length=150)
    descripcion: str | None = Field(default=None, max_length=200)
    id_accion_padre: int | None = Field(default=None, ge=1)
    id_proceso: int | None = Field(default=None, ge=0)
    request_id: str = Field(min_length=8, max_length=100)

    @field_validator("nombre")
    @classmethod
    def valid_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Ingrese el nombre de la acción")
        return value

    @field_validator("descripcion", mode="before")
    @classmethod
    def optional_description(cls, value):
        if value is None:
            return None
        return str(value).strip() or None


class NavigationUpdate(NavigationWrite):
    original_fingerprint: str = Field(min_length=64, max_length=64)


class NavigationOut(BaseModel):
    id_accion: int
    tipo_accion: int
    nombre: str
    descripcion: str | None = None
    id_accion_padre: int | None = None
    id_proceso: int | None = None
    nombre_padre: str | None = None
    nombre_proceso: str | None = None
    fingerprint: str


class NavigationDeleteOut(BaseModel):
    id_accion: int
    mensaje: str
