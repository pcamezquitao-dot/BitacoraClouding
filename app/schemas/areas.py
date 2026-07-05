from pydantic import BaseModel

class AreaByQrIn(BaseModel):
    qr: str

class AreaOut(BaseModel):
    id_area: int
    descripcion: str
