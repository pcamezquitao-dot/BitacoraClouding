from pydantic import BaseModel, Field


class PortalIdentifyIn(BaseModel):
    codigo: str = Field(min_length=1, max_length=100)


class PortalParticipantOut(BaseModel):
    id_participante: int
    codigo: str
    nombre_completo: str
    tipo_codigo: int
    tipo_descripcion: str


class PortalItemOut(BaseModel):
    id_accion: int
    nombre: str
    descripcion: str | None = None
    id_proceso: int | None = None
    nombre_proceso: str | None = None
    id_bpm_proceso: int | None = None
    nombre_bpm: str | None = None
    bpm_activo: bool = False
    estado_destino: str


class PortalOut(BaseModel):
    participante: PortalParticipantOut
    id_raiz: int | None = None
    estado_menu: str
    mensaje_menu: str | None = None
    opciones: list[PortalItemOut] = Field(default_factory=list)

