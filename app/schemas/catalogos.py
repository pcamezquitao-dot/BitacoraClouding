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
    id_empleado_area: int
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


class CatalogCalendarPeriodOut(BaseModel):
    id_periodo: int
    id_padre: int | None = None
    nivel: str
    codigo: str
    nombre: str
    fecha_inicio: date
    fecha_fin: date
    numero_dia_semana: int | None = None
    nombre_dia_semana: str | None = None
    es_fin_semana: bool | None = None
    es_festivo: bool = False
    nombre_festivo: str | None = None
    activo: bool = True


class OfflineCatalogOut(BaseModel):
    generated_at: datetime
    participantes: list[CatalogParticipantOut]
    areas: list[CatalogAreaOut]
    empleado_areas: list[CatalogAssignmentOut]
    tipos_participante: list[CatalogParticipantTypeOut]
    calendario: list[CatalogCalendarPeriodOut]
