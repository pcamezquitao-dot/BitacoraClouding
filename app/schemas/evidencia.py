from pydantic import BaseModel
from typing import Optional

class EvidenciaOut(BaseModel):
    id_evidencia: int
    id_bitacora: Optional[int] = None
    id_empleado: int
    id_supervisor: int
    ts_in_min: int
    id_tipo_evidencia: int
    archivo_url: str
    archivo_nombre: Optional[str] = None
    archivo_hash: Optional[str] = None
    tamanio_bytes: Optional[int] = None
    duracion_seg: Optional[int] = None
    orden: Optional[int] = None

class BitacoraCompletaOut(BaseModel):
    id_bitacora: int
    evidencia: EvidenciaOut
