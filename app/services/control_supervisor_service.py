"""Consulta CONTROL: agrega, sin alterar, los cálculos individuales existentes."""

from calendar import monthrange
from datetime import date, datetime, timedelta
from uuid import uuid4

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.routers.bitacora_uc03 import _resolve_bao_table
from app.services.supervisor_service import identify_supervisor, supervised_participants
from app.services.worker_time_service import COLOMBIA, calculate_days


def validated_supervisor_identity(db: Session, identity) -> dict:
    """Revalida en MariaDB la identidad firmada y su cargo vigente."""
    supervisor = identify_supervisor(db, identity.code)
    if int(supervisor["id_supervisor"]) != int(identity.participant_id):
        from app.services.supervisor_service import SupervisorAuthorizationError
        raise SupervisorAuthorizationError("La identidad del token no coincide con el supervisor vigente")
    current_areas = {int(area["id_area"]) for area in supervisor.get("areas", [])}
    if current_areas != set(identity.area_ids):
        from app.services.supervisor_service import SupervisorAuthorizationError
        raise SupervisorAuthorizationError("El alcance del supervisor cambio; debe iniciar una nueva sesion")
    return supervisor


def _worker_days(db: Session, participant_id: int, year: int, month: int) -> list[dict]:
    """Obtiene las mismas entradas y calendario que el informe individual."""
    start = datetime(year, month, 1, tzinfo=COLOMBIA)
    end = datetime(year + (month == 12), 1 if month == 12 else month + 1, 1, tzinfo=COLOMBIA)
    start_min = int((start - timedelta(days=1)).timestamp() // 60)
    end_min = int((end + timedelta(days=1)).timestamp() // 60)
    rows = db.execute(text(f"""
        SELECT id_bitacora, ts_in_min, tipo_anotacion, client_uuid
        FROM {settings.BITACORA_DIARIA_TABLE}
        WHERE id_empleado=:participant AND tipo_anotacion IN (4,5)
          AND ts_in_min>=:start_min AND ts_in_min<:end_min
        ORDER BY ts_in_min, id_bitacora
    """), {"participant": participant_id, "start_min": start_min, "end_min": end_min}).mappings().all()
    calendar_rows = db.execute(text("""
        SELECT fecha_inicio fecha, numero_dia_semana dia_semana,
               (numero_dia_semana=6) sabado, (numero_dia_semana=7) domingo,
               es_festivo festivo, nombre_festivo
        FROM dimension_calendario
        WHERE nivel='DIA' AND activo=TRUE AND fecha_inicio BETWEEN :first AND :last
    """), {"first": start.date(), "last": date(year, month, monthrange(year, month)[1])}).mappings().all()
    calendar = {row["fecha"]: dict(row) for row in calendar_rows}
    return calculate_days(year, month, rows, calendar)


def control_supervisor_report(db: Session, code: str, year: int, month: int) -> dict:
    """Devuelve solo los participantes autorizados para el supervisor."""
    supervisor = identify_supervisor(db, code)
    assignments = supervised_participants(db, supervisor["codigo"])
    workers: dict[int, dict] = {}
    for assignment in assignments:
        participant_id = int(assignment["id_participante"])
        worker = workers.setdefault(participant_id, {
            "id_participante": participant_id,
            "codigo": assignment["codigo"],
            "nombre_completo": " ".join(value for value in (
                assignment.get("nombre"), assignment.get("apellido")
            ) if value).strip(),
            "areas": [],
        })
        if assignment["area"] not in worker["areas"]:
            worker["areas"].append(assignment["area"])

    detail = []
    for worker in sorted(workers.values(), key=lambda item: item["id_participante"]):
        days = _worker_days(db, worker["id_participante"], year, month)
        detail.append({
            **worker,
            "total_minutos": sum(day["minutos_trabajados"] for day in days),
            "jornadas_incompletas": sum(1 for day in days if day["registro_incompleto"]),
            "dias": days,
        })
    return {
        "id_supervisor": supervisor["id_supervisor"],
        "codigo_supervisor": supervisor["codigo"],
        "nombre_supervisor": supervisor["nombre_completo"],
        "anio": year, "mes": month, "zona_horaria": "America/Bogota",
        "acumulado": {
            "supervisados": len(detail),
            "total_minutos": sum(worker["total_minutos"] for worker in detail),
            "jornadas_incompletas": sum(worker["jornadas_incompletas"] for worker in detail),
        },
        "supervisados": detail,
    }


def control_supervisor_report_for_identity(db: Session, identity, year: int, month: int) -> dict:
    supervisor = validated_supervisor_identity(db, identity)
    return control_supervisor_report(db, supervisor["codigo"], year, month)


def _authorized_worker(db: Session, identity, participant_id: int) -> dict:
    supervisor = validated_supervisor_identity(db, identity)
    matches = [
        row for row in supervised_participants(db, supervisor["codigo"])
        if int(row["id_participante"]) == int(participant_id)
    ]
    if not matches:
        from app.services.supervisor_service import SupervisorAuthorizationError
        raise SupervisorAuthorizationError(
            "El participante no esta activo o no pertenece al alcance del supervisor"
        )
    return matches[0]


def control_day_bitacoras(db: Session, identity, participant_id: int, day: date) -> list[dict]:
    participant = _authorized_worker(db, identity, participant_id)
    start = int(datetime.combine(day, datetime.min.time(), COLOMBIA).timestamp() // 60)
    end = int(datetime.combine(day + timedelta(days=1), datetime.min.time(), COLOMBIA).timestamp() // 60)
    rows = db.execute(text(f"""
        SELECT id_bitacora, id_empleado id_participante, ts_in_min timestamp_min,
               tipo_anotacion, observaciones
        FROM {settings.BITACORA_DIARIA_TABLE}
        WHERE id_empleado=:participant AND tipo_anotacion IN (4,5)
          AND ts_in_min>=:start AND ts_in_min<:end
        ORDER BY ts_in_min, id_bitacora
    """), {"participant": participant_id, "start": start, "end": end}).mappings().all()
    return [{
        **dict(row), "codigo_participante": participant["codigo"],
        "fecha": day.isoformat(),
    } for row in rows]


def update_control_observation(
    db: Session, identity, bitacora_id: int, expected: str | None, new_value: str | None
) -> dict:
    """Actualiza una sola observacion y crea su evidencia dentro de la transaccion activa."""
    supervisor = validated_supervisor_identity(db, identity)
    row = db.execute(text(f"""
        SELECT id_bitacora, id_empleado, ts_in_min, tipo_anotacion, observaciones
        FROM {settings.BITACORA_DIARIA_TABLE}
        WHERE id_bitacora=:id
        FOR UPDATE
    """), {"id": bitacora_id}).mappings().first()
    if not row:
        raise LookupError("Bitacora no encontrada")
    if int(row["tipo_anotacion"] or 0) not in (4, 5):
        raise ValueError("Solo pueden modificarse bitacoras tipo 4 o 5")
    participant = _authorized_worker(db, identity, int(row["id_empleado"]))
    current = row["observaciones"]
    if current != expected:
        raise RuntimeError("CONFLICTO_OBSERVACION")
    if current == new_value:
        return {
            "bitacora": _control_row(row, participant["codigo"]),
            "modificada": False, "id_evidencia": None,
        }
    bao_table = _resolve_bao_table(db)
    area_id = db.execute(text(f"""
        SELECT id_area FROM {bao_table}
        WHERE id_bitacora=:id LIMIT 1
    """), {"id": bitacora_id}).scalar_one_or_none()
    if area_id is None:
        area_id = int(participant["id_area"])
    changed_at = datetime.now(COLOMBIA)
    before_text = "" if current is None else str(current)
    after_text = "" if new_value is None else str(new_value)
    content = (
        "Accion: MODIFICACION_OBSERVACION_SUPERVISOR\n"
        f"El supervisor {supervisor['codigo']} modifico la observacion de la bitacora "
        f"{bitacora_id} del participante {participant['codigo']}. "
        f"Tipo de bitacora: {row['tipo_anotacion']}. "
        f"Observacion anterior: [{before_text}]. Observacion nueva: [{after_text}]. "
        f"Fecha del servidor: [{changed_at.isoformat()}]."
    )
    db.execute(text(f"""
        UPDATE {settings.BITACORA_DIARIA_TABLE}
        SET observaciones=:new_value
        WHERE id_bitacora=:id
    """), {"new_value": new_value, "id": bitacora_id})
    evidence = db.execute(text(f"""
        INSERT INTO {settings.BAE_TABLE}
            (id_bitacora, id_area, ts_in_min, id_tipo_evidencia, contenido_texto,
             uuid_cliente, id_supervisor_actor)
        VALUES (:bitacora, :area, :timestamp, 4, :content, :uuid, :supervisor)
    """), {
        "bitacora": bitacora_id, "area": area_id,
        "timestamp": int(changed_at.timestamp() // 60), "content": content,
        "uuid": str(uuid4()), "supervisor": supervisor["id_supervisor"],
    })
    updated = dict(row)
    updated["observaciones"] = new_value
    return {
        "bitacora": _control_row(updated, participant["codigo"]),
        "modificada": True, "id_evidencia": int(evidence.lastrowid),
    }


def _control_row(row, participant_code: str) -> dict:
    timestamp = int(row["timestamp_min"] if "timestamp_min" in row else row["ts_in_min"])
    participant_id = int(
        row["id_participante"] if "id_participante" in row else row["id_empleado"]
    )
    return {
        "id_bitacora": int(row["id_bitacora"]),
        "id_participante": participant_id,
        "codigo_participante": participant_code,
        "fecha": datetime.fromtimestamp(timestamp * 60, COLOMBIA).date().isoformat(),
        "timestamp_min": timestamp,
        "tipo_anotacion": int(row["tipo_anotacion"]),
        "observaciones": row["observaciones"],
    }
