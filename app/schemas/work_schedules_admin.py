from datetime import date, datetime
import re

from pydantic import BaseModel, Field, model_validator


class WorkScheduleDetailWrite(BaseModel):
    id_detalle: int | None = None
    dia_semana_num: int = Field(ge=1, le=7)
    numero_tramo: int = Field(ge=1, le=10)
    es_laborable: bool = True
    hora_entrada_min: int | None = Field(default=None, ge=0, le=1439)
    hora_salida_min: int | None = Field(default=None, ge=0, le=1439)
    salida_dia_siguiente: bool = False
    descanso_min: int = Field(default=0, ge=0, le=480)
    descanso_remunerado: bool = False
    observaciones: str | None = Field(default=None, max_length=300)

    @model_validator(mode="after")
    def validate_detail(self):
        if any(value is not None and value % 15 for value in (
            self.hora_entrada_min, self.hora_salida_min, self.descanso_min
        )):
            raise ValueError("Horas y descansos deben usar intervalos de 15 minutos")
        if not self.es_laborable:
            if any((self.numero_tramo != 1, self.hora_entrada_min is not None,
                    self.hora_salida_min is not None, self.salida_dia_siguiente,
                    self.descanso_min != 0, self.descanso_remunerado)):
                raise ValueError("Un día no laborable no puede contener horario ni descanso")
            return self
        if self.hora_entrada_min is None or self.hora_salida_min is None:
            raise ValueError("Los tramos laborables requieren entrada y salida")
        duration = self.hora_salida_min + (1440 if self.salida_dia_siguiente else 0) - self.hora_entrada_min
        if not 15 <= duration <= 1440 or self.descanso_min >= duration:
            raise ValueError("La duración y el descanso del tramo no son válidos")
        self.observaciones = (self.observaciones or "").strip() or None
        return self


def programmed_minutes(detail: WorkScheduleDetailWrite) -> int:
    if not detail.es_laborable:
        return 0
    duration = detail.hora_salida_min + (1440 if detail.salida_dia_siguiente else 0) - detail.hora_entrada_min
    return duration if detail.descanso_remunerado else duration - detail.descanso_min


def programmed_week_minutes(details: list[WorkScheduleDetailWrite]) -> int:
    return sum(programmed_minutes(item) for item in details)


class WorkScheduleWrite(BaseModel):
    codigo_jornada: str = Field(min_length=1, max_length=30)
    nombre_jornada: str = Field(min_length=1, max_length=120)
    minutos_objetivo_semana: int = Field(ge=0, le=10080)
    tolerancia_entrada_min: int = Field(default=0, ge=0, le=240)
    tolerancia_salida_min: int = Field(default=0, ge=0, le=240)
    vigencia_desde: date
    vigencia_hasta: date | None = None
    activo: bool = False
    aplica_control_horario: bool = True
    observaciones: str | None = Field(default=None, max_length=500)
    detalles: list[WorkScheduleDetailWrite] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_schedule(self):
        self.codigo_jornada = self.codigo_jornada.strip().upper()
        self.nombre_jornada = re.sub(r"\s+", " ", self.nombre_jornada).strip()
        self.observaciones = (self.observaciones or "").strip() or None
        if not re.fullmatch(r"[A-Z0-9][A-Z0-9_-]*", self.codigo_jornada):
            raise ValueError("El código solo admite letras, números, guion y guion bajo")
        if not self.nombre_jornada:
            raise ValueError("El nombre de la jornada es obligatorio")
        if self.vigencia_hasta and self.vigencia_hasta < self.vigencia_desde:
            raise ValueError("La vigencia final no puede ser anterior a la inicial")
        if self.minutos_objetivo_semana % 15:
            raise ValueError("El objetivo semanal debe ser múltiplo de 15 minutos")
        if not self.aplica_control_horario:
            if self.minutos_objetivo_semana != 0 or self.detalles:
                raise ValueError("Sin control horario requiere objetivo cero y sin tramos")
            return self
        if self.minutos_objetivo_semana <= 0:
            raise ValueError("El objetivo semanal debe ser positivo")
        keys = [(item.dia_semana_num, item.numero_tramo) for item in self.detalles]
        if len(keys) != len(set(keys)):
            raise ValueError("No se puede repetir el número de tramo en un mismo día")
        for day in range(1, 8):
            values = [item for item in self.detalles if item.dia_semana_num == day]
            labor = sorted((item for item in values if item.es_laborable), key=lambda item: item.hora_entrada_min or 0)
            if labor and any(not item.es_laborable for item in values):
                raise ValueError("Un día no puede ser laborable y no laborable simultáneamente")
            for previous, current in zip(labor, labor[1:]):
                end = previous.hora_salida_min + (1440 if previous.salida_dia_siguiente else 0)
                if current.hora_entrada_min < end:
                    raise ValueError(f"Existen tramos superpuestos en el día {day}")
        if self.activo:
            if {item.dia_semana_num for item in self.detalles} != set(range(1, 8)):
                raise ValueError("Una jornada activa debe definir los siete días")
            if programmed_week_minutes(self.detalles) != self.minutos_objetivo_semana:
                raise ValueError("El total semanal debe coincidir con el objetivo para activar la jornada")
        return self


class WorkScheduleDetailOut(WorkScheduleDetailWrite):
    id_detalle: int
    minutos_programados: int


class WorkScheduleOut(BaseModel):
    id_jornada: int
    codigo_jornada: str
    nombre_jornada: str
    minutos_objetivo_semana: int
    tolerancia_entrada_min: int
    tolerancia_salida_min: int
    vigencia_desde: date
    vigencia_hasta: date | None = None
    activo: bool
    aplica_control_horario: bool
    observaciones: str | None = None
    fecha_creacion: datetime | None = None
    fecha_actualizacion: datetime | None = None
    total_programado_semana: int
    referenciada_activa: bool
    detalles: list[WorkScheduleDetailOut] = Field(default_factory=list)


class WorkScheduleStatusWrite(BaseModel):
    activo: bool
