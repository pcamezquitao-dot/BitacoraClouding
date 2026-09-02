from __future__ import annotations

from collections import defaultdict
from datetime import date, datetime, timedelta
from html import escape

from sqlalchemy import bindparam, text

from app.core.config import settings
from app.services.management_service import classify_day
from app.services.worker_time_service import COLOMBIA, calculate_days

TOTAL_KEYS = ("total_minutos", "ordinarios_minutos", "extras_minutos", "dominicales_festivos_minutos")


def _empty_totals():
    return {key: 0 for key in TOTAL_KEYS} | {
        "sabado_minutos": 0, "domingo_minutos": 0, "festivo_minutos": 0,
        "extras_autorizados_minutos": 0, "jornadas_incompletas": 0, "anomalias": 0,
    }


def _add(target, source):
    for key in _empty_totals():
        target[key] += int(source.get(key, 0))


def _active_assignment(assignments, participant_id: int, day: date):
    valid = [row for row in assignments.get(participant_id, [])
             if row["fecha_inicia"] <= day and (row["fecha_final"] is None or row["fecha_final"] >= day)]
    return max(valid, key=lambda row: (row["fecha_inicia"], row["id_empleado_area"]), default=None)


def build_hours_report(db, desde: date, hasta: date, id_area: int | None = None,
                       id_participante: int | None = None) -> dict:
    if desde > hasta:
        raise ValueError("La fecha inicial no puede ser posterior a la final")
    start = int(datetime.combine(desde, datetime.min.time(), COLOMBIA).timestamp() // 60)
    end = int(datetime.combine(hasta + timedelta(days=2), datetime.min.time(), COLOMBIA).timestamp() // 60)
    params = {"start": start, "end": end, "desde": desde, "hasta": hasta}
    employee_filter = ""
    if id_participante:
        employee_filter = " AND bd.id_empleado=:participant"
        params["participant"] = id_participante
    events = db.execute(text(f"""
        SELECT bd.id_bitacora,bd.id_empleado,bd.ts_in_min,bd.tipo_anotacion,bd.client_uuid
        FROM {settings.BITACORA_DIARIA_TABLE} bd
        WHERE bd.tipo_anotacion IN (4,5) AND bd.ts_in_min>=:start AND bd.ts_in_min<:end
        {employee_filter} ORDER BY bd.id_empleado,bd.ts_in_min,bd.id_bitacora
    """), params).mappings().all()
    calendars = db.execute(text("""
        SELECT fecha_inicio fecha,numero_anio,numero_mes,numero_dia,numero_dia_semana dia_semana,
               (numero_dia_semana=6) sabado,(numero_dia_semana=7) domingo,
               es_festivo festivo,nombre_festivo
        FROM dimension_calendario WHERE nivel='DIA' AND activo=TRUE
          AND fecha_inicio BETWEEN :desde AND :hasta ORDER BY fecha_inicio
    """), params).mappings().all()
    calendar = {row["fecha"]: dict(row) for row in calendars}
    areas = [dict(row) for row in db.execute(text(f"""
        SELECT id_Area_Administrativa id_area,descripcion,nodo_padre FROM {settings.AREAS_TABLE}
    """)).mappings().all()]
    area_map = {row["id_area"]: row for row in areas}
    allowed_area_ids = None
    if id_area:
        allowed_area_ids = {id_area}
        changed = True
        while changed:
            before = len(allowed_area_ids)
            allowed_area_ids.update(row["id_area"] for row in areas if row["nodo_padre"] in allowed_area_ids)
            changed = len(allowed_area_ids) != before
    assignments_by_person = defaultdict(list)
    for row in db.execute(text(f"""
        SELECT id_empleado_area,id_participante,id_area,fecha_inicia,fecha_final
        FROM {settings.EMPLEADO_AREA_TABLE}
        WHERE activo=TRUE AND fecha_inicia<=:hasta AND (fecha_final IS NULL OR fecha_final>=:desde)
    """), params).mappings().all():
        assignments_by_person[row["id_participante"]].append(dict(row))
    authorized = defaultdict(int)
    for row in db.execute(text("""
        SELECT id_participante,fecha_inicio,SUM(minutos_autorizados) minutos
        FROM supervisor_novedad WHERE tipo_novedad=7 AND estado='ACTIVO'
          AND fecha_inicio BETWEEN :desde AND :hasta GROUP BY id_participante,fecha_inicio
    """), params).mappings().all():
        authorized[(row["id_participante"], row["fecha_inicio"])] = int(row["minutos"] or 0)
    by_employee = defaultdict(list)
    for row in events:
        by_employee[row["id_empleado"]].append(row)
    participant_ids = set(by_employee) | ({id_participante} if id_participante else set())
    names = {}
    if participant_ids:
        names_query = text(f"""
            SELECT id_participante,identificacion_participante codigo,
                   TRIM(CONCAT_WS(' ',nombre,apellido)) nombre
            FROM {settings.PARTICIPANTE_TABLE} WHERE id_participante IN :ids
        """).bindparams(bindparam("ids", expanding=True))
        names = {row["id_participante"]: dict(row) for row in
                 db.execute(names_query, {"ids": sorted(participant_ids)}).mappings().all()}
    records = []
    for participant_id, employee_events in by_employee.items():
        cursor = date(desde.year, desde.month, 1)
        while cursor <= hasta:
            for raw_day in calculate_days(cursor.year, cursor.month, employee_events, calendar):
                day_date = date.fromisoformat(raw_day["fecha"])
                if not desde <= day_date <= hasta:
                    continue
                assignment = _active_assignment(assignments_by_person, participant_id, day_date)
                if assignment is None or (allowed_area_ids is not None and assignment["id_area"] not in allowed_area_ids):
                    continue
                classified = classify_day(raw_day)
                anomalies = sum(1 for event in raw_day["eventos"] if event.get("inconsistencia"))
                authorized_minutes = authorized[(participant_id, day_date)]
                if (classified["total_minutos"] == 0 and not classified["registro_incompleto"]
                        and anomalies == 0 and authorized_minutes == 0):
                    continue
                record = classified | {
                    "id_participante": participant_id,
                    "codigo": names.get(participant_id, {}).get("codigo", ""),
                    "nombre": names.get(participant_id, {}).get("nombre", ""),
                    "id_area": assignment["id_area"],
                    "extras_autorizados_minutos": authorized_minutes,
                    "sabado_minutos": classified["total_minutos"] if raw_day["sabado"] else 0,
                    "domingo_minutos": classified["total_minutos"] if raw_day["domingo"] else 0,
                    "festivo_minutos": classified["total_minutos"] if raw_day["festivo"] else 0,
                    "jornadas_incompletas": int(raw_day["registro_incompleto"]), "anomalias": anomalies,
                }
                record["diferencia_revision_minutos"] = max(
                    record["extras_minutos"] - record["extras_autorizados_minutos"], 0
                )
                records.append(record)
            cursor = (cursor.replace(day=28) + timedelta(days=4)).replace(day=1)
    summary = _empty_totals()
    for record in records:
        _add(summary, record)
    active_workers = {row["id_participante"] for row in records if row["total_minutos"] > 0}
    summary["trabajadores_con_actividad"] = len(active_workers)
    summary["promedio_minutos_por_trabajador"] = (
        summary["total_minutos"] // len(active_workers) if active_workers else 0
    )
    summary["diferencia_revision_minutos"] = max(
        summary["extras_minutos"] - summary["extras_autorizados_minutos"], 0
    )
    node_totals = {area_id: _empty_totals() | {"workers": set()} for area_id in area_map}
    for record in records:
        current = record["id_area"]
        visited = set()
        while current in area_map and current not in visited:
            visited.add(current); _add(node_totals[current], record)
            if record["total_minutos"] > 0:
                node_totals[current]["workers"].add(record["id_participante"])
            current = area_map[current]["nodo_padre"]
    def node(area):
        totals = node_totals[area["id_area"]]
        children = [node(child) for child in areas if child["nodo_padre"] == area["id_area"]]
        return {"id_area": area["id_area"], "nombre": area["descripcion"],
                "trabajadores": len(totals.pop("workers")), **totals, "hijos": children}
    roots = [node(area) for area in areas if area["nodo_padre"] not in area_map]
    return {"generado_en": datetime.now(COLOMBIA).isoformat(), "zona_horaria": "America/Bogota",
            "desde": desde.isoformat(), "hasta": hasta.isoformat(), "fecha_corte": hasta.isoformat(),
            "pendientes_sincronizacion": False, "resumen": summary,
            "calendario": [dict(row) for row in calendars], "organizacion": roots,
            "detalle": sorted(records, key=lambda item: (item["fecha"], item["id_area"], item["id_participante"]))}


def build_management_aggregate(db, desde: date, hasta: date, id_area: int | None = None) -> dict:
    """Contrato gerencial agregado; nunca expone participantes ni marcaciones individuales."""
    complete_report = build_hours_report(db, desde, hasta)
    report = complete_report
    organization_records = complete_report["detalle"]

    def collect_area_ids(source):
        yield source["id_area"]
        for child in source.get("hijos", []):
            yield from collect_area_ids(child)

    def walk_areas(source):
        yield source
        for child in source.get("hijos", []):
            yield from walk_areas(child)

    selected_area_ids = None
    if id_area is not None:
        for root in complete_report["organizacion"]:
            for candidate in walk_areas(root):
                if candidate["id_area"] == id_area:
                    selected_area_ids = set(collect_area_ids(candidate))
                    break
        if selected_area_ids is None:
            selected_area_ids = set()
    records = [row for row in organization_records
               if selected_area_ids is None or row["id_area"] in selected_area_ids]

    def aggregate(items):
        result = _empty_totals() | {"azul_claro_minutos": 0, "azul_oscuro_minutos": 0}
        for item in items:
            _add(result, item)
            ordinary_limit = 240 if item["sabado"] else 480
            if item["ordinarios_minutos"] < ordinary_limit:
                result["azul_claro_minutos"] += item["ordinarios_minutos"]
            else:
                result["azul_oscuro_minutos"] += item["ordinarios_minutos"]
        return result

    years = []
    for year in sorted({row["fecha"][:4] for row in records}):
        year_rows = [row for row in records if row["fecha"].startswith(year)]
        months = []
        for month in sorted({row["fecha"][:7] for row in year_rows}):
            month_rows = [row for row in year_rows if row["fecha"].startswith(month)]
            days = [{"tipo": "DIA", "codigo": day, "nombre": day, **aggregate(
                [row for row in month_rows if row["fecha"] == day])}
                    for day in sorted({row["fecha"] for row in month_rows})]
            months.append({"tipo": "MES", "codigo": month, "nombre": month,
                           "hijos": days, **aggregate(month_rows)})
        years.append({"tipo": "ANIO", "codigo": year, "nombre": year,
                      "hijos": months, **aggregate(year_rows)})

    def area_node(source, path=()):
        child_ids = {child_id for child in source.get("hijos", [])
                     for child_id in collect_area_ids(child)}
        ids = child_ids | {source["id_area"]}
        node_rows = [row for row in organization_records if row["id_area"] in ids]
        children = [area_node(child, path + (source["nombre"],)) for child in source.get("hijos", [])]
        return {"id_area": source["id_area"], "nombre": source["nombre"],
                "ruta": " > ".join(path + (source["nombre"],)), "hijos": children,
                **aggregate(node_rows)}

    organization = [area_node(root) for root in complete_report["organizacion"]]
    return {"generado_en": report["generado_en"], "zona_horaria": report["zona_horaria"],
            "desde": report["desde"], "hasta": report["hasta"],
            "fecha_corte": report["fecha_corte"], "resumen": aggregate(records),
            "tiempo": years, "organizacion": organization}


def render_hours_html(report: dict, previous_report: dict | None = None) -> tuple[str, str]:
    s = report["resumen"]
    previous_total = previous_report["resumen"]["total_minutos"] if previous_report else None
    comparison = s["total_minutos"] - previous_total if previous_total is not None else None
    def hours(value):
        sign = "-" if value < 0 else ""
        absolute = abs(value)
        return f"{sign}{absolute // 60} h {absolute % 60} min"
    comparison_html = (
        f"<tr><td>Variación frente al día anterior</td><td>{'+' if comparison > 0 else ''}{hours(comparison)}</td></tr>"
        if comparison is not None else ""
    )
    rows = "".join(
        f"<tr><td>{escape(node['nombre'])}</td><td>{node['trabajadores']}</td>"
        f"<td>{hours(node['total_minutos'])}</td><td>{node['anomalias']}</td></tr>"
        for node in report["organizacion"]
    )
    html = f"""<!doctype html><html><body style='font-family:Arial;color:#222'>
    <h1>Informe gerencial de horas laboradas</h1>
    <p>Periodo: {escape(report['desde'])} a {escape(report['hasta'])}<br>
    Generado: {escape(report['generado_en'])}<br>Zona horaria: America/Bogota</p>
    <table cellpadding='8' cellspacing='0' border='1'><tr><th>Indicador</th><th>Valor</th></tr>
    <tr><td>Trabajadores con actividad</td><td>{s['trabajadores_con_actividad']}</td></tr>
    <tr><td>Total laborado</td><td>{hours(s['total_minutos'])}</td></tr>
    <tr><td>Ordinarias</td><td>{hours(s['ordinarios_minutos'])}</td></tr>
    <tr><td>Adicionales calculadas</td><td>{hours(s['extras_minutos'])}</td></tr>
    <tr><td>Extras autorizadas</td><td>{hours(s['extras_autorizados_minutos'])}</td></tr>
    <tr><td>Jornadas incompletas</td><td>{s['jornadas_incompletas']}</td></tr>
    <tr><td>Registros con anomalías</td><td>{s['anomalias']}</td></tr>{comparison_html}</table>
    <h2>Consolidado organizacional</h2><table cellpadding='8' cellspacing='0' border='1'>
    <tr><th>Nivel</th><th>Trabajadores</th><th>Horas</th><th>Anomalías</th></tr>{rows}</table>
    <p>Resumen estático. El detalle requiere acceso autorizado en Bitácora.</p></body></html>"""
    plain = (f"Informe gerencial de horas laboradas\nPeriodo: {report['desde']} a {report['hasta']}\n"
             f"Trabajadores: {s['trabajadores_con_actividad']}\nTotal: {hours(s['total_minutos'])}\n"
             f"Ordinarias: {hours(s['ordinarios_minutos'])}\nAdicionales: {hours(s['extras_minutos'])}\n"
             f"Autorizadas: {hours(s['extras_autorizados_minutos'])}\nAnomalías: {s['anomalias']}")
    if comparison is not None:
        sign = "+" if comparison > 0 else ""
        plain += f"\nVariación frente al día anterior: {sign}{hours(comparison)}"
    return html, plain
