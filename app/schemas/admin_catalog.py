from datetime import date, datetime

from pydantic import BaseModel, Field


class ParticipantTypeCreate(BaseModel):
    descripcion: str = Field(min_length=1, max_length=100)
    capacidades: list[str] = Field(min_length=1)


class ParticipantTypeUpdate(ParticipantTypeCreate):
    pass


class ParticipantTypeStatusUpdate(BaseModel):
    activo: bool


class ParticipantTypeOut(BaseModel):
    codigo: int
    descripcion: str
    activo: bool
    capacidades: list[str]


class AreaTreeNodeOut(BaseModel):
    id_area: int
    descripcion: str
    nombre_corto: str | None = None
    id_padre: int | None = None
    nivel: int
    ruta: str


class AdministrativeAreaWrite(BaseModel):
    descripcion: str = Field(min_length=1, max_length=100)
    nombre_corto: str | None = Field(default=None, max_length=25)
    nodo_padre: int | None = None


class AdministrativeAreaOut(AdministrativeAreaWrite):
    id_area_administrativa: int


class EmployeeAreaCreate(BaseModel):
    id_participante: int
    id_area: int
    codigo_tipo: int
    descripcion: str | None = Field(default=None, max_length=100)
    fecha_inicia: date
    fecha_final: date | None = None


class EmployeeAreaOut(EmployeeAreaCreate):
    id_empleado_area: int
    creado_en: datetime | None = None


class EmployeeAreaAssignmentOut(BaseModel):
    id_empleado_area: int
    id_participante: int
    codigo_participante: str
    nombre_completo: str
    codigo_tipo: int | None = None
    cargo: str | None = None
    descripcion: str | None = None
    fecha_inicia: date
    fecha_final: date | None = None


class EmployeeAreaTreeNodeOut(BaseModel):
    id_area: int
    descripcion: str
    nombre_corto: str | None = None
    nodo_padre: int | None = None
    nivel: int
    ruta: str
    cantidad_participantes: int
    participantes: list[EmployeeAreaAssignmentOut] = Field(default_factory=list)
    hijos: list["EmployeeAreaTreeNodeOut"] = Field(default_factory=list)


class EmployeeAreaUpdate(BaseModel):
    id_area: int
    codigo_tipo: int
    descripcion: str | None = Field(default=None, max_length=100)
    fecha_inicia: date
    fecha_final: date | None = None


class ParticipantOptionOut(BaseModel):
    id_participante: int
    codigo: str
    nombres: str
    apellidos: str
    nombre_completo: str
    documento: str | None = None
