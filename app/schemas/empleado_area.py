from datetime import date

from pydantic import BaseModel


class EmpleadoAreaActivaOut(BaseModel):
    id_participante: int
    id_area: int
    area_descripcion: str | None = None
    cargo: int | None = None
    fecha_final: date | None = None
