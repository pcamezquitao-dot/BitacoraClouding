from pydantic import BaseModel


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
