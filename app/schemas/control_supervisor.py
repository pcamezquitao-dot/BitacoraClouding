"""Contrato exclusivo de la consulta CONTROL para supervisores."""

from pydantic import BaseModel, Field

from app.schemas.worker import WorkerDayOut


class ControlSupervisorTotalsOut(BaseModel):
    supervisados: int
    total_minutos: int
    jornadas_incompletas: int


class ControlSupervisedWorkerOut(BaseModel):
    id_participante: int
    codigo: str
    nombre_completo: str
    areas: list[str]
    total_minutos: int
    jornadas_incompletas: int
    dias: list[WorkerDayOut]


class ControlSupervisorReportOut(BaseModel):
    id_supervisor: int
    codigo_supervisor: str
    nombre_supervisor: str
    anio: int
    mes: int
    zona_horaria: str = "America/Bogota"
    acumulado: ControlSupervisorTotalsOut
    supervisados: list[ControlSupervisedWorkerOut]


class ControlSupervisorSessionIn(BaseModel):
    codigo: str = Field(min_length=1, max_length=100)


class ControlSupervisorSessionOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_at: int
    supervisor: dict
    advertencia: str = "PROTOTIPO DE DESARROLLO - IDENTIDAD NO AUTENTICADA CON CREDENCIAL PERSONAL"


class ControlBitacoraOut(BaseModel):
    id_bitacora: int
    id_participante: int
    codigo_participante: str
    fecha: str
    timestamp_min: int
    tipo_anotacion: int
    observaciones: str | None = None


class ControlObservationUpdateIn(BaseModel):
    observacion_anterior: str | None = None
    observacion_nueva: str | None = Field(default=None, max_length=65535)


class ControlObservationUpdateOut(BaseModel):
    bitacora: ControlBitacoraOut
    modificada: bool
    id_evidencia: int | None = None
