from typing import Literal

from pydantic import BaseModel, Field, model_validator


class CandidateOut(BaseModel):
    id_actividad: int
    nombre: str
    descripcion: str | None = None
    coincidencias: list[str] = []
    diferencias: list[str] = []
    puntuacion: float


class StepDefinition(BaseModel):
    paso_id: str = Field(min_length=1, max_length=50)
    requerimiento: str = Field(min_length=1)
    responsable: str | None = None
    documentos: list[str] = []
    reglas: list[str] = []
    datos: list[str] = []
    integraciones: list[str] = []
    tipo: Literal["actividad", "decision", "espera", "final"] = "actividad"
    siguiente: list[str] = []
    id_actividad: int | None = None
    nombre_actividad: str | None = None
    propuesta_codigo: str | None = None
    propuesta_nombre: str | None = None
    propuesta_descripcion: str | None = None
    candidates: list[CandidateOut] = []

    @model_validator(mode="after")
    def exclusive_choice(self):
        existing = self.id_actividad is not None or self.nombre_actividad is not None
        proposed = any((self.propuesta_codigo, self.propuesta_nombre, self.propuesta_descripcion))
        if existing and proposed:
            raise ValueError("Un paso no puede seleccionar actividad existente y propuesta a la vez")
        return self


class StructuredDefinition(BaseModel):
    nombre: str = Field(min_length=1, max_length=150)
    pasos: list[StepDefinition] = Field(min_length=1)
    observaciones: list[str] = []


class WorkCreate(BaseModel):
    titulo: str = Field(min_length=1, max_length=150)
    requerimiento_original: str = Field(min_length=1, max_length=100000)


class VersionUpdate(BaseModel):
    definicion: StructuredDefinition
    version_esperada: int = Field(ge=1)


class WorkSummary(BaseModel):
    id_trabajo: str
    titulo: str
    estado: str
    version_actual: int
    creado_por: str
    creado_en: str
    actualizado_en: str


class VersionOut(BaseModel):
    id_version: str
    numero: int
    estado: str
    definicion: StructuredDefinition
    texto_normalizado: str
    plantuml: str
    xml_definicion: str
    pendientes: list[str]
    catalogo_sha256: str
    contenido_sha256: str
    aprobado_por: str | None = None
    aprobado_en: str | None = None


class WorkOut(WorkSummary):
    requerimiento_original: str
    requerimiento_sha256: str
    version: VersionOut


class ApprovalOut(BaseModel):
    id_trabajo: str
    numero_version: int
    estado: str
    aprobado_por: str
    aprobado_en: str
    contenido_sha256: str
    disponible_para_cu31: bool
