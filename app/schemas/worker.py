from pydantic import BaseModel, Field


class WorkerIdentifyIn(BaseModel):
    codigo: str = Field(min_length=1, max_length=100)


class WorkerSessionOut(BaseModel):
    id_participante: int
    codigo: str
    nombre_completo: str


class WorkerEventOut(BaseModel):
    id_anotacion: str
    timestamp_min: int
    tipo_anotacion: int
    client_uuid: str | None = None
    utilizado: bool = False
    inconsistencia: str | None = None


class WorkerDayOut(BaseModel):
    fecha: str
    dia_semana: int
    laborable: bool
    sabado: bool
    domingo: bool
    festivo: bool
    nombre_festivo: str | None = None
    minutos_trabajados: int
    registro_incompleto: bool
    eventos: list[WorkerEventOut]


class WorkerTimeOut(BaseModel):
    id_participante: int
    anio: int
    mes: int
    zona_horaria: str = "America/Bogota"
    dias: list[WorkerDayOut]
