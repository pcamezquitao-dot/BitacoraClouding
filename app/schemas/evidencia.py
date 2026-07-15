from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class EvidenciaOut(BaseModel):
    id_evidencia: int
    id_bitacora: int
    id_area: int
    ts_in_min: int
    id_tipo_evidencia: int
    archivo_url: str
    archivo_nombre: Optional[str] = None
    archivo_hash: Optional[str] = None
    mime_type: Optional[str] = None
    tamanio_bytes: Optional[int] = None
    duracion_seg: Optional[int] = None
    orden: Optional[int] = None
    latitud: Optional[float] = None
    longitud: Optional[float] = None
    precision_gps: Optional[float] = None
    uuid_cliente: str
    created_at: Optional[datetime] = None


class BitacoraCompletaOut(BaseModel):
    id_bitacora: int
    evidencia: EvidenciaOut
