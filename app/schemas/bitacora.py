from pydantic import BaseModel, Field
from typing import Optional

class BitacoraAreaObsCreate(BaseModel):
    id_empleado: int = Field(..., description="id_participante del empleado (desde QR)")
    qr_area: str = Field(..., description="QR de área: AREA_ADMINISTRATIVA|id|descripcion")
    observaciones: Optional[str] = None
    tipo_anotacion: Optional[int] = None

class BitacoraAreaObsOut(BaseModel):
    id_bitacora: Optional[int] = None
    id_empleado: int
    id_supervisor: int
    ts_in_min: int
    id_area: int
    area_descripcion: str

class BitacoraDiariaCreate(BaseModel):
    id_empleado: int = Field(..., description="id_participante del empleado")
    id_supervisor: Optional[int] = Field(None, description="Si no se envía, el backend intenta calcularlo")
    ts_in_min: Optional[int] = Field(None, description="Minutos Unix. Si no se envía, usa la hora actual")
    ts_out_min: Optional[int] = None
    tipo_anotacion: Optional[int] = None
    observaciones: Optional[str] = Field(None, max_length=200)
    client_uuid: Optional[str] = Field(None, max_length=40)
    qr_area: Optional[str] = Field(None, description="QR de área validado para el empleado")

class BitacoraDiariaOut(BaseModel):
    id_bitacora: int
    id_empleado: int
    id_supervisor: Optional[int] = None
    ts_in_min: int
    ts_out_min: Optional[int] = None
    tipo_anotacion: Optional[int] = None
    observaciones: Optional[str] = None
