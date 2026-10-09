from typing import Any

from pydantic import BaseModel


class Xml2ErCount(BaseModel):
    nuevos: int = 0
    reutilizados: int = 0
    conflictos: int = 0


class Xml2ErPreview(BaseModel):
    estado: str
    archivo: str
    sha256: str
    entorno: str
    base: str
    proceso: str | None = None
    version: int | None = None
    cantidades: dict[str, Xml2ErCount]
    conflictos: list[str] = []
    correspondencias: dict[str, dict[str, int]] = {}
    token_validacion: str | None = None


class Xml2ErImportIn(BaseModel):
    archivo: str
    xml: str
    token_validacion: str


class Xml2ErResult(BaseModel):
    estado: str
    archivo: str
    sha256: str
    entorno: str
    base: str
    proceso: str
    version: int
    confirmado: bool
    cantidades: dict[str, Xml2ErCount]
    correspondencias: dict[str, dict[str, int]]
    usuario: str
    fecha_hora: str
