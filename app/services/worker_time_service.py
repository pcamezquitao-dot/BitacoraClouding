from collections import defaultdict
from datetime import date, datetime, timedelta, timezone


COLOMBIA = timezone(timedelta(hours=-5), name="America/Bogota")


def minute_to_local(minute: int) -> datetime:
    return datetime.fromtimestamp(minute * 60, tz=timezone.utc).astimezone(COLOMBIA)


def calculate_days(year: int, month: int, rows, calendar: dict[date, tuple[bool, bool]]):
    """Pair 4->5 chronologically; a cross-midnight interval belongs to its entry day."""
    unique = {}
    for raw in rows:
        item = dict(raw)
        key = ("id", item.get("id_bitacora")) if item.get("id_bitacora") is not None else (
            "uuid", item.get("client_uuid")
        )
        if key[1] is None:
            key = ("event", len(unique))
        unique.setdefault(key, item)
    events = sorted(unique.values(), key=lambda item: (item["ts_in_min"], item.get("id_bitacora", 0)))
    minutes = defaultdict(int)
    detailed = defaultdict(list)
    pending = None
    for event in events:
        kind = event["tipo_anotacion"]
        if kind == 4:
            if pending is None:
                pending = event
            else:
                event["inconsistencia"] = "Entrada consecutiva sin salida intermedia"
        elif kind == 5 and pending is not None:
            start = minute_to_local(pending["ts_in_min"])
            end = minute_to_local(event["ts_in_min"])
            if end > start:
                minutes[start.date()] += int((end - start).total_seconds() // 60)
                pending["utilizado"] = event["utilizado"] = True
                detailed[start.date()].extend((pending, event))
            else:
                event["inconsistencia"] = "Duración no positiva"
            pending = None
        elif kind == 5:
            event["inconsistencia"] = "Salida sin entrada"

    if pending is not None:
        pending["inconsistencia"] = "Entrada sin salida"

    by_day = defaultdict(list)
    for event in events:
        day = minute_to_local(event["ts_in_min"]).date()
        if not event.get("utilizado"):
            detailed[day].append(event)
    for day, day_events in detailed.items():
        seen = set()
        for event in day_events:
            identity = event.get("id_bitacora") or event.get("client_uuid") or id(event)
            if identity in seen:
                continue
            seen.add(identity)
            by_day[day].append({
                "id_anotacion": str(event.get("id_bitacora") or event.get("client_uuid") or identity),
                "timestamp_min": event["ts_in_min"],
                "tipo_anotacion": event["tipo_anotacion"],
                "client_uuid": event.get("client_uuid"),
                "utilizado": bool(event.get("utilizado")),
                "inconsistencia": event.get("inconsistencia"),
            })
    first = date(year, month, 1)
    next_month = date(year + (month == 12), 1 if month == 12 else month + 1, 1)
    result = []
    day = first
    while day < next_month:
        cal = calendar.get(day, {})
        weekday = int(cal.get("dia_semana", day.isoweekday()))
        saturday = bool(cal.get("sabado", weekday == 6))
        sunday = bool(cal.get("domingo", weekday == 7))
        festivo = bool(cal.get("festivo", False))
        laborable = not (sunday or festivo)
        day_events = by_day[day]
        result.append({"fecha": day.isoformat(), "dia_semana": weekday,
                       "laborable": laborable, "sabado": saturday, "domingo": sunday,
                       "festivo": festivo, "nombre_festivo": cal.get("nombre_festivo"),
                       "minutos_trabajados": minutes[day],
                       "registro_incompleto": any(e.get("inconsistencia") for e in day_events),
                       "eventos": day_events})
        day += timedelta(days=1)
    return result
