from sqlalchemy import bindparam, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.schemas.work_schedules_admin import (
    WorkScheduleDetailWrite,
    WorkScheduleWrite,
    programmed_minutes,
)

HEADER_TABLE = "jornadas_de_trabajo"
DETAIL_TABLE = "jornadas_de_trabajo_detalle"


class WorkScheduleNotFound(ValueError):
    pass


class WorkScheduleConflict(ValueError):
    pass


def _referenced_active(db: Session, schedule_id: int) -> bool:
    return bool(db.execute(text("""
        SELECT EXISTS(
            SELECT 1 FROM empleado_area
            WHERE id_jornada=:id AND activo=TRUE
        )
    """), {"id": schedule_id}).scalar_one())


def _details(db: Session, schedule_id: int) -> list[dict]:
    rows = db.execute(text(f"""
        SELECT id_detalle,dia_semana_num,numero_tramo,es_laborable,
               hora_entrada_min,hora_salida_min,salida_dia_siguiente,
               descanso_min,descanso_remunerado,observaciones
        FROM {DETAIL_TABLE} WHERE id_jornada=:id
        ORDER BY dia_semana_num,numero_tramo,id_detalle
    """), {"id": schedule_id}).mappings().all()
    values = []
    for row in rows:
        item = dict(row)
        for key in ("es_laborable", "salida_dia_siguiente", "descanso_remunerado"):
            item[key] = bool(item[key])
        item["minutos_programados"] = programmed_minutes(WorkScheduleDetailWrite(**item))
        values.append(item)
    return values


def _serialize(db: Session, header: dict) -> dict:
    result = dict(header)
    result["activo"] = bool(result["activo"])
    result["aplica_control_horario"] = bool(result["aplica_control_horario"])
    result["detalles"] = _details(db, int(result["id_jornada"]))
    result["total_programado_semana"] = sum(item["minutos_programados"] for item in result["detalles"])
    result["referenciada_activa"] = _referenced_active(db, int(result["id_jornada"]))
    return result


def list_schedules(db: Session, search: str = "", active: bool | None = None) -> list[dict]:
    filters, params = [], {}
    if search.strip():
        filters.append("(codigo_jornada LIKE :search OR nombre_jornada LIKE :search)")
        params["search"] = f"%{search.strip()}%"
    if active is not None:
        filters.append("activo=:active")
        params["active"] = active
    where = " WHERE " + " AND ".join(filters) if filters else ""
    rows = db.execute(text(f"SELECT * FROM {HEADER_TABLE}{where} ORDER BY codigo_jornada"), params).mappings().all()
    return [_serialize(db, row) for row in rows]


def get_schedule(db: Session, schedule_id: int) -> dict:
    row = db.execute(text(f"SELECT * FROM {HEADER_TABLE} WHERE id_jornada=:id"), {"id": schedule_id}).mappings().first()
    if not row:
        raise WorkScheduleNotFound("La jornada no existe")
    return _serialize(db, row)


def _header_values(payload: WorkScheduleWrite) -> dict:
    return payload.model_dump(exclude={"detalles"})


def _detail_values(schedule_id: int, detail) -> dict:
    return {"id_jornada": schedule_id, **detail.model_dump(exclude={"id_detalle"})}


def _assert_edit_allowed(db: Session, schedule_id: int, payload: WorkScheduleWrite) -> None:
    if not _referenced_active(db, schedule_id):
        return
    current = get_schedule(db, schedule_id)
    locked = (
        "codigo_jornada", "minutos_objetivo_semana", "tolerancia_entrada_min",
        "tolerancia_salida_min", "vigencia_desde", "vigencia_hasta",
        "aplica_control_horario", "activo",
    )
    if any(getattr(payload, key) != current[key] for key in locked):
        raise WorkScheduleConflict("La jornada está asignada: solo puede editar nombre y observaciones")
    incoming = [item.model_dump(exclude={"id_detalle"}) for item in payload.detalles]
    stored = [{key: value for key, value in item.items() if key not in {"id_detalle", "minutos_programados"}}
              for item in current["detalles"]]
    if incoming != stored:
        raise WorkScheduleConflict("La jornada está asignada: no se pueden modificar sus tramos")


def _insert_details(db: Session, schedule_id: int, details) -> None:
    for detail in details:
        db.execute(text(f"""INSERT INTO {DETAIL_TABLE}
          (id_jornada,dia_semana_num,numero_tramo,es_laborable,hora_entrada_min,hora_salida_min,
           salida_dia_siguiente,descanso_min,descanso_remunerado,observaciones)
          VALUES (:id_jornada,:dia_semana_num,:numero_tramo,:es_laborable,:hora_entrada_min,:hora_salida_min,
                  :salida_dia_siguiente,:descanso_min,:descanso_remunerado,:observaciones)"""),
          _detail_values(schedule_id, detail))


def create_schedule(db: Session, payload: WorkScheduleWrite) -> dict:
    try:
        result = db.execute(text(f"""INSERT INTO {HEADER_TABLE}
          (codigo_jornada,nombre_jornada,minutos_objetivo_semana,tolerancia_entrada_min,
           tolerancia_salida_min,vigencia_desde,vigencia_hasta,activo,aplica_control_horario,observaciones)
          VALUES (:codigo_jornada,:nombre_jornada,:minutos_objetivo_semana,:tolerancia_entrada_min,
                  :tolerancia_salida_min,:vigencia_desde,:vigencia_hasta,:activo,:aplica_control_horario,:observaciones)"""),
          _header_values(payload))
        schedule_id = int(result.lastrowid)
        _insert_details(db, schedule_id, payload.detalles)
        result = get_schedule(db, schedule_id)
        db.commit()
    except IntegrityError as error:
        db.rollback()
        raise WorkScheduleConflict("El código o un tramo de la jornada ya existe") from error
    except Exception:
        db.rollback()
        raise
    return result


def update_schedule(db: Session, schedule_id: int, payload: WorkScheduleWrite) -> dict:
    get_schedule(db, schedule_id)
    _assert_edit_allowed(db, schedule_id, payload)
    try:
        values = _header_values(payload) | {"id": schedule_id}
        db.execute(text(f"""UPDATE {HEADER_TABLE} SET
          codigo_jornada=:codigo_jornada,nombre_jornada=:nombre_jornada,
          minutos_objetivo_semana=:minutos_objetivo_semana,tolerancia_entrada_min=:tolerancia_entrada_min,
          tolerancia_salida_min=:tolerancia_salida_min,vigencia_desde=:vigencia_desde,
          vigencia_hasta=:vigencia_hasta,activo=:activo,aplica_control_horario=:aplica_control_horario,
          observaciones=:observaciones WHERE id_jornada=:id"""), values)
        existing = {row[0] for row in db.execute(text(
            f"SELECT id_detalle FROM {DETAIL_TABLE} WHERE id_jornada=:id"), {"id": schedule_id})}
        retained = set()
        for detail in payload.detalles:
            values = _detail_values(schedule_id, detail)
            if detail.id_detalle is not None:
                if detail.id_detalle not in existing:
                    raise WorkScheduleConflict("Un detalle no pertenece a la jornada")
                retained.add(detail.id_detalle)
                db.execute(text(f"""UPDATE {DETAIL_TABLE} SET dia_semana_num=:dia_semana_num,
                  numero_tramo=:numero_tramo,es_laborable=:es_laborable,hora_entrada_min=:hora_entrada_min,
                  hora_salida_min=:hora_salida_min,salida_dia_siguiente=:salida_dia_siguiente,
                  descanso_min=:descanso_min,descanso_remunerado=:descanso_remunerado,observaciones=:observaciones
                  WHERE id_detalle=:id_detalle AND id_jornada=:id_jornada"""), values | {"id_detalle": detail.id_detalle})
            else:
                created = db.execute(text(f"""INSERT INTO {DETAIL_TABLE}
                  (id_jornada,dia_semana_num,numero_tramo,es_laborable,hora_entrada_min,hora_salida_min,
                   salida_dia_siguiente,descanso_min,descanso_remunerado,observaciones)
                  VALUES (:id_jornada,:dia_semana_num,:numero_tramo,:es_laborable,:hora_entrada_min,:hora_salida_min,
                          :salida_dia_siguiente,:descanso_min,:descanso_remunerado,:observaciones)"""), values)
                retained.add(int(created.lastrowid))
        removed = existing - retained
        if removed:
            statement = text(f"DELETE FROM {DETAIL_TABLE} WHERE id_jornada=:id AND id_detalle IN :ids").bindparams(bindparam("ids", expanding=True))
            db.execute(statement, {"id": schedule_id, "ids": sorted(removed)})
        result = get_schedule(db, schedule_id)
        db.commit()
    except IntegrityError as error:
        db.rollback()
        raise WorkScheduleConflict("El código o un tramo de la jornada ya existe") from error
    except Exception:
        db.rollback()
        raise
    return result


def change_status(db: Session, schedule_id: int, active: bool) -> dict:
    current = get_schedule(db, schedule_id)
    if not active and current["referenciada_activa"]:
        raise WorkScheduleConflict("No se puede inactivar una jornada referenciada por empleado-área activo")
    if active == current["activo"]:
        return current
    payload = WorkScheduleWrite(**{
        key: value for key, value in current.items()
        if key not in {"id_jornada", "fecha_creacion", "fecha_actualizacion", "total_programado_semana", "referenciada_activa", "activo"}
    }, activo=active)
    return update_schedule(db, schedule_id, payload)
