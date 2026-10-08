from datetime import date, time, datetime
from pydantic import BaseModel, Field


class SupervisorEventIn(BaseModel):
    codigo_supervisor: str = Field(min_length=1, max_length=100)
    tipo_novedad: int
    id_participante: int
    id_area: int
    fecha_inicio: date
    fecha_final: date
    hora_inicio: time | None = None
    hora_final: time | None = None
    observaciones: str | None = Field(None, max_length=400)
    client_uuid: str = Field(min_length=1, max_length=40)


class SupervisorEventOut(SupervisorEventIn):
    id_novedad: int
    id_supervisor: int
    dias_calculados: int | None = None
    minutos_autorizados: int | None = None
    estado: str
    origen: str
    creado_en: datetime
    actualizado_en: datetime
