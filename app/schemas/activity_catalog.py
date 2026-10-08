from pydantic import BaseModel, Field, field_validator


class ActivityCatalogIn(BaseModel):
    nombre: str = Field(max_length=100)
    descripcion: str | None = None

    @field_validator("nombre")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("El nombre es obligatorio")
        return normalized

    @field_validator("descripcion")
    @classmethod
    def normalize_description(cls, value: str | None) -> str | None:
        if value is None:
            return None
        if len(value.encode("utf-8")) > 65535:
            raise ValueError("La descripcion excede la capacidad permitida")
        return value if value.strip() else None


class ActivityCatalogOut(ActivityCatalogIn):
    id_actividad: int
