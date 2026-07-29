from datetime import date, datetime

from pydantic import BaseModel


class CatalogParticipantOut(BaseModel):
    id_participante: int
    identificacion_participante: str
    nombre: str | None = None
    apellido: str | None = None
    documento: str | None = None
    activo: bool = True
    updated_at: datetime | None = None


class CatalogAreaOut(BaseModel):
    id_area: int
    descripcion: str
    nombre_corto: str | None = None
    nodo_padre: int | None = None
    activo: bool = True
    updated_at: datetime | None = None


class CatalogAssignmentOut(BaseModel):
    id_participante: int
    id_area: int
    cargo: int | None = None
    fecha_inicia: date | None = None
    fecha_final: date | None = None
    activo: bool = True
    updated_at: datetime | None = None


class CatalogParticipantTypeOut(BaseModel):
    codigo: int
    descripcion: str
    activo: bool = True
    capacidades: list[str]


class OfflineCatalogOut(BaseModel):
    generated_at: datetime
    participantes: list[CatalogParticipantOut]
    areas: list[CatalogAreaOut]
    empleado_areas: list[CatalogAssignmentOut]
    tipos_participante: list[CatalogParticipantTypeOut]
