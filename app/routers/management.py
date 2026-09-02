from datetime import date, datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.db import get_db
from app.schemas.management import ManagementHoursOut, ManagementIdentifyIn, ManagementSessionOut
from app.services.management_service import classify_day, identify_management, sum_totals
from app.services.worker_time_service import COLOMBIA, calculate_days


router = APIRouter(prefix="/gerencia", tags=["gerencia"])


@router.post("/identificar", response_model=ManagementSessionOut)
def identify(payload: ManagementIdentifyIn, db: Session = Depends(get_db)):
    return identify_management(db, payload.codigo)


@router.get("/{codigo}/horas-laboradas", response_model=ManagementHoursOut)
def hours(codigo: str, desde: date, hasta: date,
          participante: str | None = Query(None, max_length=100),
          id_area: int | None = Query(None, ge=1), db: Session = Depends(get_db)):
    identify_management(db, codigo)
    if desde > hasta:
        raise HTTPException(422, "La fecha inicial no puede ser posterior a la final")
    if (hasta - desde).days > 366 * 5:
        raise HTTPException(422, "El rango máximo permitido es de cinco años")
    participant = None
    if participante:
        participant = db.execute(text(f"""
            SELECT id_participante, identificacion_participante codigo,
                   TRIM(CONCAT_WS(' ',nombre,apellido)) nombre
            FROM {settings.PARTICIPANTE_TABLE}
            WHERE UPPER(identificacion_participante)=:code LIMIT 1
        """), {"code": participante.strip().upper()}).mappings().first()
        if not participant:
            raise HTTPException(404, "El trabajador solicitado no existe")
    start_min = int(datetime.combine(desde, datetime.min.time(), COLOMBIA).timestamp() // 60)
    end_min = int(datetime.combine(hasta + timedelta(days=2), datetime.min.time(), COLOMBIA).timestamp() // 60)
    filters = ["bd.tipo_anotacion IN (4,5)", "bd.ts_in_min>=:start", "bd.ts_in_min<:end"]
    params = {"start": start_min, "end": end_min}
    if participant:
        filters.append("bd.id_empleado=:participant")
        params["participant"] = participant["id_participante"]
    if id_area:
        filters.append(f"""EXISTS (SELECT 1 FROM {settings.EMPLEADO_AREA_TABLE} ea
            WHERE ea.id_participante=bd.id_empleado AND ea.id_area=:area AND ea.activo=TRUE
              AND ea.fecha_inicia<=:hasta AND (ea.fecha_final IS NULL OR ea.fecha_final>=:desde))""")
        params.update(area=id_area, desde=desde, hasta=hasta)
    rows = db.execute(text(f"""
        SELECT bd.id_bitacora,bd.id_empleado,bd.ts_in_min,bd.tipo_anotacion,bd.client_uuid
        FROM {settings.BITACORA_DIARIA_TABLE} bd WHERE {' AND '.join(filters)}
        ORDER BY bd.id_empleado,bd.ts_in_min,bd.id_bitacora
    """), params).mappings().all()
    calendars = db.execute(text("""
        SELECT fecha_inicio fecha,numero_dia_semana dia_semana,
               (numero_dia_semana=6) sabado,(numero_dia_semana=7) domingo,
               es_festivo festivo,nombre_festivo
        FROM dimension_calendario WHERE nivel='DIA' AND activo=TRUE
          AND fecha_inicio BETWEEN :desde AND :hasta
    """), {"desde": desde, "hasta": hasta}).mappings().all()
    calendar = {row["fecha"]: dict(row) for row in calendars}
    by_employee = {}
    for row in rows: by_employee.setdefault(row["id_empleado"], []).append(row)
    aggregate = {}
    cursor = date(desde.year, desde.month, 1)
    while cursor <= hasta:
        employee_sets = list(by_employee.values()) or [[]]
        for employee_rows in employee_sets:
            for day in calculate_days(cursor.year, cursor.month, employee_rows, calendar):
                if not (desde.isoformat() <= day["fecha"] <= hasta.isoformat()): continue
                classified = classify_day(day)
                target = aggregate.setdefault(day["fecha"], {
                    **classified,
                    **{key: 0 for key in ("total_minutos", "ordinarios_minutos", "extras_minutos", "dominicales_festivos_minutos")},
                })
                for key in ("total_minutos", "ordinarios_minutos", "extras_minutos", "dominicales_festivos_minutos"):
                    target[key] += classified[key]
                target["registro_incompleto"] |= classified["registro_incompleto"]
        cursor = (cursor.replace(day=28) + timedelta(days=4)).replace(day=1)
    years = []
    for year in sorted({int(value["fecha"][:4]) for value in aggregate.values()}):
        months = []
        for month in sorted({int(v["fecha"][5:7]) for v in aggregate.values() if v["fecha"].startswith(str(year))}):
            days = [v for v in aggregate.values() if v["fecha"].startswith(f"{year}-{month:02d}")]
            months.append({"mes": month, "dias": sorted(days,key=lambda d:d["fecha"]), **sum_totals(days)})
        years.append({"anio": year, "meses": months, **sum_totals(months)})
    return {"desde": desde.isoformat(),"hasta": hasta.isoformat(),"id_participante": participant and participant["id_participante"],
            "codigo_participante": participant and participant["codigo"],"nombre_participante": participant and participant["nombre"],
            "id_area": id_area,"anios": years}
