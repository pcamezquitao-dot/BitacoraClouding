from datetime import date

from pydantic import BaseModel, Field

class ParticipanteOut(BaseModel):
    id_participante: int
    nombre: str | None = None
    apellido: str | None = None
    identificacion_participante: str | None = None
    documento: str | None = None


class ParticipantAdminIn(BaseModel):
    tipo_documento: int = Field(gt=0)
    documento: str = Field(min_length=1, max_length=20)
    identificacion_participante: str = Field(min_length=1, max_length=100)
    nombre: str = Field(min_length=1, max_length=100)
    apellido: str = Field(min_length=1, max_length=100)
    fecha_nacimiento: date
    sexo: str = Field(min_length=1, max_length=1)
    fecha_entrada: date | None = None
    fecha_salida: date | None = None
    observaciones: str | None = Field(default=None, max_length=200)
    email: str | None = Field(default=None, max_length=80)


class ParticipantAdminOut(ParticipantAdminIn):
    id_participante: int
    activo: bool


class ParticipantAdminPage(BaseModel):
    items: list[ParticipantAdminOut]
    total: int
    offset: int
    limit: int


class DocumentTypeOut(BaseModel):
    codigo: int
    descripcion: str
