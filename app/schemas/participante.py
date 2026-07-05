from pydantic import BaseModel

class ParticipanteOut(BaseModel):
    id_participante: int
    nombre: str | None = None
    apellido: str | None = None
    identificacion_participante: str | None = None
