from datetime import date

from fastapi import HTTPException
from sqlalchemy import text

from app.core.config import settings


DIRECTIVO_TYPE_NAME = "directivo"


def resolve_management_type_code(db) -> int:
    rows = db.execute(text("""
        SELECT codigo FROM tipos_participante
        WHERE activo=TRUE AND LOWER(TRIM(descripcion))=:name
    """), {"name": DIRECTIVO_TYPE_NAME}).all()
    if len(rows) != 1:
        raise HTTPException(503, "El catálogo debe contener exactamente un tipo directivo activo")
    return int(rows[0][0])


def identify_management(db, raw_code: str) -> dict:
    code = raw_code.strip().upper()
    type_code = resolve_management_type_code(db)
    row = db.execute(text(f"""
        SELECT DISTINCT p.id_participante id_directivo,
               p.identificacion_participante codigo,
               TRIM(CONCAT_WS(' ',p.nombre,p.apellido)) nombre_completo
        FROM {settings.PARTICIPANTE_TABLE} p
        JOIN {settings.EMPLEADO_AREA_TABLE} ea ON ea.id_participante=p.id_participante
        WHERE UPPER(p.identificacion_participante)=:code
          AND (p.fecha_salida IS NULL OR p.fecha_salida>=CURRENT_DATE)
          AND ea.cargo=:type_code AND ea.activo=TRUE
          AND ea.fecha_inicia<=CURRENT_DATE
          AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURRENT_DATE)
        LIMIT 1
    """), {"code": code, "type_code": type_code}).mappings().first()
    if not row:
        raise HTTPException(403, "El participante no tiene una asignación activa como directivo")
    return dict(row)


def classify_day(day: dict) -> dict:
    total = int(day["minutos_trabajados"])
    if day["domingo"] or day["festivo"]:
        ordinary, extra, special = 0, 0, total
    else:
        limit = 240 if day["sabado"] else 480
        ordinary, extra, special = min(total, limit), max(total - limit, 0), 0
    return {
        "fecha": day["fecha"], "dia_semana": day["dia_semana"],
        "sabado": day["sabado"], "domingo": day["domingo"],
        "festivo": day["festivo"], "nombre_festivo": day["nombre_festivo"],
        "registro_incompleto": day["registro_incompleto"],
        "total_minutos": total, "ordinarios_minutos": ordinary,
        "extras_minutos": extra, "dominicales_festivos_minutos": special,
    }


def sum_totals(items: list[dict]) -> dict:
    keys = ("total_minutos", "ordinarios_minutos", "extras_minutos", "dominicales_festivos_minutos")
    return {key: sum(int(item[key]) for item in items) for key in keys}
