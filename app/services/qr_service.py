from dataclasses import dataclass

@dataclass
class ParsedAreaQr:
    id_area: int
    descripcion: str | None = None

def parse_area_qr(qr: str) -> ParsedAreaQr:
    parts = [p.strip() for p in (qr or "").split("|")]
    if len(parts) < 2:
        raise ValueError("QR de área inválido. Formato esperado: AREA_ADMINISTRATIVA|id|descripcion")
    if parts[0].upper().replace(" ", "_") != "AREA_ADMINISTRATIVA":
        raise ValueError("QR no corresponde a AREA_ADMINISTRATIVA")
    try:
        id_area = int(parts[1])
    except Exception:
        raise ValueError("QR de área inválido: id_Area_Administrativa no es numérico")
    descripcion = parts[2] if len(parts) >= 3 else None
    return ParsedAreaQr(id_area=id_area, descripcion=descripcion)
