from pydantic import BaseModel, Field


class SupervisorIdentifyIn(BaseModel):
    codigo: str = Field(min_length=1, max_length=100)


class SupervisorAreaOut(BaseModel):
    id_area: int
    area: str


class SupervisorSessionOut(BaseModel):
    id_supervisor: int
    codigo: str
    nombre_completo: str
    estado: str = "Supervisor identificado"
    areas: list[SupervisorAreaOut]


class SupervisedParticipantOut(BaseModel):
    id_participante: int
    codigo: str
    nombre: str | None = None
    apellido: str | None = None
    id_area: int
    area: str


class SupervisorMovementIn(BaseModel):
    codigo_supervisor: str = Field(min_length=1, max_length=100)
    id_participante: int
    id_area: int
    tipo: str
    timestamp_min: int | None = None
    client_uuid: str = Field(min_length=1, max_length=40)
    dispositivo: str | None = Field(None, max_length=100)


class SupervisorMovementOut(BaseModel):
    id_bitacora: int
    id_participante: int
    id_supervisor: int
    id_area: int
    tipo: str
    timestamp_min: int
    client_uuid: str


class SupervisorTodayMovementOut(SupervisorMovementOut):
    codigo_participante: str
    nombre_completo: str
    area: str
    sync_status: str = "SINCRONIZADO"
