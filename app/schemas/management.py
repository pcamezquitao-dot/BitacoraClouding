from pydantic import BaseModel, Field


class ManagementIdentifyIn(BaseModel):
    codigo: str = Field(min_length=1, max_length=100)


class ManagementSessionOut(BaseModel):
    id_directivo: int
    codigo: str
    nombre_completo: str


class ManagementTotalsOut(BaseModel):
    total_minutos: int
    ordinarios_minutos: int
    extras_minutos: int
    dominicales_festivos_minutos: int


class ManagementDayOut(ManagementTotalsOut):
    fecha: str
    dia_semana: int
    sabado: bool
    domingo: bool
    festivo: bool
    nombre_festivo: str | None = None
    registro_incompleto: bool


class ManagementMonthOut(ManagementTotalsOut):
    mes: int
    dias: list[ManagementDayOut]


class ManagementYearOut(ManagementTotalsOut):
    anio: int
    meses: list[ManagementMonthOut]


class ManagementHoursOut(BaseModel):
    desde: str
    hasta: str
    zona_horaria: str = "America/Bogota"
    id_participante: int | None = None
    codigo_participante: str | None = None
    nombre_participante: str | None = None
    id_area: int | None = None
    anios: list[ManagementYearOut]
