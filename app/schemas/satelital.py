from pydantic import BaseModel
from datetime import date


class ObjetoMonitoreoSatelitalOut(BaseModel):
    id_objeto_monitoreo: int
    nombre: str
    tipo_objeto: str
    pais_codigo: str
    departamento_provincia: str | None = None
    municipio_localidad: str | None = None
    descripcion: str | None = None
    latitud_centro: float | None = None
    longitud_centro: float | None = None
    geometria_geojson: dict | list | None = None


class EmbalseSatelitalOut(BaseModel):
    id_embalse: int
    nombre: str
    pais: str
    departamento: str | None = None
    municipio: str | None = None


class ImagenSatelitalEmbalseOut(BaseModel):
    id_imagen_satelital: int
    id_embalse: int
    fecha_captura: date
    porcentaje_nubes: float | None = None
    fuente: str
    imagen_url: str
    mime_type: str
