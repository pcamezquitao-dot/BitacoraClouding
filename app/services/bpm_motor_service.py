from __future__ import annotations

import base64
import hashlib
import json
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.schemas.bpm_motor import BpmEvidenceIn, BpmOperationIn


class BpmError(ValueError):
    pass


class BpmConflict(BpmError):
    pass


class BpmForbidden(BpmError):
    pass


UNSUPPORTED_RULE_MARKERS = ("SELECT ", "INSERT ", "UPDATE ", "DELETE ", "DROP ", "PYTHON", "HTTP", "API:")


def _canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str)


def _hash(value: Any) -> str:
    return hashlib.sha256(_canonical(value).encode("utf-8")).hexdigest()


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _actor(db: Session, code: str) -> dict:
    row = db.execute(text("""
        SELECT id_participante,identificacion_participante,nombre,apellido
        FROM participante
        WHERE UPPER(TRIM(identificacion_participante))=:code
          AND (fecha_salida IS NULL OR fecha_salida>=CURDATE())
        LIMIT 1
    """), {"code": code.strip().upper()}).mappings().first()
    if not row:
        raise BpmForbidden("El participante no existe o no está activo")
    return dict(row)


def _roles(db: Session, participant_id: int) -> set[str]:
    return {str(value).strip().upper() for value in db.execute(text("""
        SELECT tp.descripcion
        FROM empleado_area ea
        JOIN tipos_participante tp ON tp.codigo=ea.cargo AND tp.activo=1
        WHERE ea.id_participante=:id AND ea.activo=1
          AND ea.fecha_inicia<=CURDATE()
          AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE())
    """), {"id": participant_id}).scalars()}


def _is_admin(db: Session, participant_id: int) -> bool:
    return bool(db.execute(text("""
        SELECT 1 FROM administrador_autorizacion
        WHERE id_participante=:id AND activo=1
          AND (vigencia_desde IS NULL OR vigencia_desde<=CURDATE())
          AND (vigencia_hasta IS NULL OR vigencia_hasta>=CURDATE()) LIMIT 1
    """), {"id": participant_id}).first())


def _can_open_for(db: Session, actor_id: int, affected_id: int) -> bool:
    if _is_admin(db, actor_id):
        return True
    actor_roles = _roles(db, actor_id)
    if not actor_roles.intersection({"SUPERVISOR", "GERENTE", "DIRECTIVO"}):
        return False
    return bool(db.execute(text("""
        WITH RECURSIVE alcance AS (
          SELECT id_area FROM empleado_area
          WHERE id_participante=:actor AND activo=1
            AND fecha_inicia<=CURDATE() AND (fecha_final IS NULL OR fecha_final>=CURDATE())
          UNION DISTINCT
          SELECT a.id_Area_Administrativa FROM areas_administrativas a
          JOIN alcance p ON a.nodo_padre=p.id_area
        )
        SELECT 1 FROM empleado_area ea JOIN alcance a ON a.id_area=ea.id_area
        WHERE ea.id_participante=:affected AND ea.activo=1
          AND ea.fecha_inicia<=CURDATE() AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE())
        LIMIT 1
    """), {"actor": actor_id, "affected": affected_id}).first())


def _definition_dict(db: Session, process_id: int) -> dict:
    process = db.execute(text("""
        SELECT id_bpm_proceso,nombre,version,descripcion,activo,fecha_creacion
        FROM bpm_proceso WHERE id_bpm_proceso=:id
    """), {"id": process_id}).mappings().first()
    if not process:
        raise BpmError("La definición BPM no existe")
    stages = [dict(row) for row in db.execute(text("""
        SELECT e.id_etapa,e.rol_responsable,e.plazo_horas,e.es_inicial,e.es_final,
               d.nombre,d.id_actividad,c.nombre AS actividad,
               cfg.instrucciones,cfg.resultados_json,cfg.documentos_requeridos,
               cfg.regla_asignacion
        FROM bpm_etapa e
        JOIN dim_proceso d ON d.id_proceso=e.id_etapa
        LEFT JOIN cat_actividad c ON c.id_actividad=d.id_actividad
        LEFT JOIN bpm_etapa_config cfg ON cfg.id_bpm_proceso=e.id_bpm_proceso
                                      AND cfg.id_etapa=e.id_etapa
        WHERE e.id_bpm_proceso=:id ORDER BY e.id_etapa
    """), {"id": process_id}).mappings()]
    transitions = [dict(row) for row in db.execute(text("""
        SELECT id_transicion,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo
        FROM bpm_transicion WHERE id_bpm_proceso=:id ORDER BY id_transicion
    """), {"id": process_id}).mappings()]
    return {"process": dict(process), "stages": stages, "transitions": transitions}


def validate_definition(db: Session, process_id: int, persist_snapshot: bool = True) -> tuple[dict, str]:
    definition = _definition_dict(db, process_id)
    process, stages, transitions = definition["process"], definition["stages"], definition["transitions"]
    if not process["activo"]:
        raise BpmError("La definición no está habilitada")
    initials = [stage for stage in stages if stage["es_inicial"]]
    finals = [stage for stage in stages if stage["es_final"]]
    if len(initials) != 1:
        raise BpmError("La definición requiere exactamente una etapa inicial")
    if not finals:
        raise BpmError("La definición requiere al menos una etapa final")
    stage_ids = {int(stage["id_etapa"]) for stage in stages}
    graph = {stage_id: [] for stage_id in stage_ids}
    for transition in transitions:
        origin, destination = int(transition["id_etapa_origen"]), int(transition["id_etapa_destino"])
        if origin not in stage_ids or destination not in stage_ids:
            raise BpmError("Una transición referencia una etapa inexistente")
        graph[origin].append(destination)
        rule = str(transition["codigo_regla"] or "SIEMPRE").upper()
        if any(marker in rule for marker in UNSUPPORTED_RULE_MARKERS):
            raise BpmError("La definición requiere una regla o conector no soportado")
    visited, pending = set(), [int(initials[0]["id_etapa"])]
    while pending:
        current = pending.pop()
        if current in visited:
            continue
        visited.add(current); pending.extend(graph[current])
    if visited != stage_ids:
        raise BpmError("La definición contiene etapas inaccesibles")
    if not any(int(stage["id_etapa"]) in visited for stage in finals):
        raise BpmError("No existe una etapa final alcanzable")
    for stage in stages:
        if not stage["es_final"] and (not stage["rol_responsable"] or not stage["id_actividad"]):
            raise BpmError(f"La etapa {stage['id_etapa']} requiere rol y actividad")
        if stage["rol_responsable"]:
            exists = db.execute(text("""
                SELECT 1 FROM tipos_participante
                WHERE activo=1 AND UPPER(TRIM(descripcion))=UPPER(TRIM(:role)) LIMIT 1
            """), {"role": stage["rol_responsable"]}).first()
            if not exists:
                raise BpmError(f"Rol no disponible: {stage['rol_responsable']}")
    sha = _hash(definition)
    if persist_snapshot:
        existing = db.execute(text("""
            SELECT definicion_sha256 FROM bpm_definicion_snapshot WHERE id_bpm_proceso=:id
        """), {"id": process_id}).scalar_one_or_none()
        if existing and existing != sha:
            raise BpmConflict("La versión publicada de la definición fue modificada")
        if not existing:
            db.execute(text("""
                INSERT INTO bpm_definicion_snapshot
                  (id_bpm_proceso,definicion_sha256,definicion_json)
                VALUES (:id,:sha,:json)
            """), {"id": process_id, "sha": sha, "json": _canonical(definition)})
            db.commit()
    return definition, sha


def list_definitions(db: Session) -> list[dict]:
    rows = db.execute(text("SELECT id_bpm_proceso FROM bpm_proceso WHERE activo=1 ORDER BY nombre,version")).scalars()
    result = []
    for process_id in rows:
        definition, sha = validate_definition(db, int(process_id))
        process = definition["process"]
        result.append({**process, "activo": bool(process["activo"]), "sha256": sha, "definition": definition})
    return result


def _snapshot_hash(db: Session, process_id: int) -> str:
    value = db.execute(text("SELECT definicion_sha256 FROM bpm_definicion_snapshot WHERE id_bpm_proceso=:id"), {"id": process_id}).scalar_one_or_none()
    if not value:
        _, value = validate_definition(db, process_id)
    return str(value)


def _operation_payload(operation: BpmOperationIn) -> dict:
    return operation.model_dump(mode="json", exclude_none=True)


def _existing_operation(db: Session, operation: BpmOperationIn) -> dict | None:
    row = db.execute(text("SELECT solicitud_sha256,resultado_json,estado,error FROM bpm_operacion WHERE operacion_uuid=:uuid"), {"uuid": operation.operation_uuid}).mappings().first()
    if not row:
        return None
    if row["solicitud_sha256"] != _hash(_operation_payload(operation)):
        raise BpmConflict("La clave de operación ya fue usada con contenido diferente")
    if row["resultado_json"]:
        return json.loads(row["resultado_json"])
    raise BpmConflict(row["error"] or "La operación previa no fue confirmada")


def _record_operation(db: Session, operation: BpmOperationIn, result: dict, case_id=None, task_id=None):
    payload = _operation_payload(operation)
    db.execute(text("""
        INSERT INTO bpm_operacion
          (operacion_uuid,dependencia_uuid,id_caso,id_actividad,tipo,actor_codigo,dispositivo,
           revision_base,id_bpm_proceso,definicion_sha256,fecha_captura,
           fecha_confirmacion,solicitud_sha256,solicitud_json,estado,resultado_json)
        VALUES (:uuid,:dependency,:case_id,:task_id,:type,:actor,:device,:revision,:process_id,
                :definition,:captured,NOW(),:sha,:payload,'CONFIRMADA',:result)
    """), {"uuid": operation.operation_uuid, "case_id": case_id, "task_id": task_id,
             "dependency": operation.dependency_uuid,
             "type": operation.operation_type, "actor": operation.actor_code,
             "device": operation.device, "revision": operation.base_revision,
             "process_id": operation.process_id, "definition": operation.definition_sha256,
             "captured": operation.captured_at, "sha": _hash(payload),
             "payload": _canonical(payload), "result": _canonical(result)})


def _history(db: Session, operation: BpmOperationIn, actor: dict, event: str,
             case_id: int, task_id: int | None, before: str | None,
             after: str | None, reason: str | None = None, detail: dict | None = None):
    db.execute(text("""
        INSERT INTO bpm_historial
          (id_caso,id_actividad,operacion_uuid,evento,actor_codigo,actor_id,
           estado_anterior,estado_nuevo,motivo,detalle_json,fecha_captura)
        VALUES (:case_id,:task_id,:uuid,:event,:actor,:actor_id,:before,:after,
                :reason,:detail,:captured)
    """), {"case_id": case_id, "task_id": task_id, "uuid": operation.operation_uuid,
             "event": event, "actor": operation.actor_code, "actor_id": actor["id_participante"],
             "before": before, "after": after, "reason": reason,
             "detail": _canonical(detail) if detail else None, "captured": operation.captured_at})


def _participant_by_code(db: Session, code: str) -> dict:
    return _actor(db, code)


def _resolve_responsible(db: Session, process_id: int, stage_id: int,
                         affected_code: str, explicit_id: int | None) -> int:
    stage = db.execute(text("""
        SELECT e.rol_responsable,COALESCE(cfg.regla_asignacion,'UNICO_O_EXPLICITO') regla
        FROM bpm_etapa e LEFT JOIN bpm_etapa_config cfg
          ON cfg.id_bpm_proceso=e.id_bpm_proceso AND cfg.id_etapa=e.id_etapa
        WHERE e.id_bpm_proceso=:p AND e.id_etapa=:e
    """), {"p": process_id, "e": stage_id}).mappings().one()
    affected = _participant_by_code(db, affected_code)
    role = str(stage["rol_responsable"] or "").strip().upper()
    if stage["regla"] == "AFECTADO":
        if role and role not in _roles(db, int(affected["id_participante"])):
            raise BpmError("El participante afectado no cumple el rol de la etapa")
        return int(affected["id_participante"])
    candidates = [int(value) for value in db.execute(text("""
        SELECT DISTINCT ea.id_participante
        FROM empleado_area ea JOIN tipos_participante tp ON tp.codigo=ea.cargo
        JOIN participante p ON p.id_participante=ea.id_participante
        WHERE ea.activo=1 AND tp.activo=1
          AND UPPER(TRIM(tp.descripcion))=:role
          AND ea.fecha_inicia<=CURDATE()
          AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE())
          AND (p.fecha_salida IS NULL OR p.fecha_salida>=CURDATE())
        ORDER BY ea.id_participante
    """), {"role": role}).scalars()]
    if explicit_id is not None:
        if explicit_id not in candidates:
            raise BpmError("El responsable seleccionado no es elegible")
        return explicit_id
    if len(candidates) != 1:
        raise BpmError("La etapa requiere seleccionar un responsable elegible")
    return candidates[0]


def _base_result(operation: BpmOperationIn, **values) -> dict:
    return {"operation_uuid": operation.operation_uuid, "status": "CONFIRMED",
            "server_time": _now().isoformat(), **values}


def _resolve_local_id(db: Session, entity_type: str, client_uuid: str | None) -> int | None:
    if not client_uuid:
        return None
    value = db.execute(text("""
        SELECT id_servidor FROM bpm_identidad_local
        WHERE tipo_entidad=:type AND client_uuid=:uuid
    """), {"type": entity_type, "uuid": client_uuid}).scalar_one_or_none()
    return int(value) if value is not None else None


def _map_local_id(db: Session, entity_type: str, client_uuid: str, server_id: int, device: str | None):
    db.execute(text("""
        INSERT INTO bpm_identidad_local(tipo_entidad,client_uuid,id_servidor,dispositivo)
        VALUES(:type,:uuid,:server_id,:device)
    """), {"type": entity_type, "uuid": client_uuid, "server_id": server_id, "device": device})


def open_case(db: Session, operation: BpmOperationIn) -> dict:
    existing = _existing_operation(db, operation)
    if existing:
        return existing
    if not operation.process_id or not operation.subject or not operation.affected_code or not operation.source_type:
        raise BpmError("Proceso, asunto, afectado y origen son obligatorios")
    if not operation.case_client_uuid or not operation.task_client_uuid:
        raise BpmError("La apertura requiere UUID local de caso y tarea inicial")
    actor = _actor(db, operation.actor_code)
    definition, sha = validate_definition(db, operation.process_id)
    if operation.definition_sha256 and operation.definition_sha256.lower() != sha:
        raise BpmConflict("La definición local está desactualizada")
    affected = _participant_by_code(db, operation.affected_code)
    if not _can_open_for(db, int(actor["id_participante"]), int(affected["id_participante"])):
        raise BpmForbidden("El actor no puede abrir casos para ese participante")
    if operation.source_type == "NOVEDAD":
        if not operation.source_id or not operation.source_table:
            raise BpmError("La apertura desde Novedades requiere tabla e identificador de origen")
        source = db.execute(text(f"SELECT id_participante,id_supervisor FROM {operation.source_table} WHERE id_novedad=:id"), {"id": operation.source_id}).mappings().first()
        if not source or int(source["id_participante"]) != int(affected["id_participante"]):
            raise BpmError("La novedad confirmada no existe o no corresponde al afectado")
        if int(source["id_supervisor"]) != int(actor["id_participante"]) and not _is_admin(db, int(actor["id_participante"])):
            raise BpmForbidden("El actor no puede abrir el caso desde esa novedad")
    initial = next(stage for stage in definition["stages"] if stage["es_inicial"])
    responsible = _resolve_responsible(db, operation.process_id, int(initial["id_etapa"]), operation.affected_code, operation.responsible_id)
    try:
        db.execute(text("""
            INSERT INTO bpm_caso(id_bpm_proceso,asunto,id_participante_afectado,
              id_participante_creador,estado,revision)
            VALUES(:process,:subject,:affected,:creator,'ABIERTO',1)
        """), {"process": operation.process_id, "subject": operation.subject.strip(),
                 "affected": operation.affected_code, "creator": operation.actor_code})
        case_id = int(db.execute(text("SELECT LAST_INSERT_ID()")).scalar_one())
        db.execute(text("""
            INSERT INTO bpm_caso_control(id_caso,client_uuid,solicitud_sha256,
              origen_tipo,origen_id,definicion_sha256,dispositivo,fecha_captura)
            VALUES(:case_id,:uuid,:hash,:origin,:origin_id,:definition,:device,:captured)
        """), {"case_id": case_id, "uuid": operation.case_client_uuid,
                 "hash": _hash(_operation_payload(operation)), "origin": operation.source_type,
                 "origin_id": operation.source_id, "definition": sha,
                 "device": operation.device, "captured": operation.captured_at})
        db.execute(text("""
            INSERT INTO bpm_actividad(id_caso,id_bpm_proceso,id_etapa,id_responsable,
              estado,fecha_programada,fecha_vencimiento,clave_idempotencia,revision)
            VALUES(:case_id,:process,:stage,:responsible,'PENDIENTE',NOW(),
              CASE WHEN :hours IS NULL THEN NULL ELSE DATE_ADD(NOW(),INTERVAL :minutes MINUTE) END,
              :key,1)
        """), {"case_id": case_id, "process": operation.process_id,
                 "stage": initial["id_etapa"], "responsible": responsible,
                 "hours": initial["plazo_horas"],
                 "minutes": int(float(initial["plazo_horas"] or 0) * 60),
                 "key": f"{operation.operation_uuid}:initial"[:100]})
        task_id = int(db.execute(text("SELECT LAST_INSERT_ID()")).scalar_one())
        _map_local_id(db, "CASO", operation.case_client_uuid, case_id, operation.device)
        _map_local_id(db, "TAREA", operation.task_client_uuid, task_id, operation.device)
        result = _base_result(operation, case_id=case_id, task_id=task_id,
                              case_client_uuid=operation.case_client_uuid,
                              task_client_uuid=operation.task_client_uuid,
                              case_state="ABIERTO", task_state="PENDIENTE",
                              case_revision=1, task_revision=1)
        _record_operation(db, operation, result, case_id, task_id)
        _history(db, operation, actor, "APERTURA", case_id, task_id, None, "ABIERTO",
                 detail={"responsible_id": responsible, "source_type": operation.source_type,
                         "source_id": operation.source_id})
        db.commit()
        return result
    except Exception:
        db.rollback(); raise


def _locked_task(db: Session, operation: BpmOperationIn) -> tuple[dict, dict, dict]:
    case_id = operation.case_id or _resolve_local_id(db, "CASO", operation.case_client_uuid)
    task_id = operation.task_id or _resolve_local_id(db, "TAREA", operation.task_client_uuid)
    if not task_id or not case_id:
        raise BpmError("Caso y tarea son obligatorios")
    task = db.execute(text("SELECT * FROM bpm_actividad WHERE id_actividad=:id AND id_caso=:case_id FOR UPDATE"), {"id": task_id, "case_id": case_id}).mappings().first()
    case = db.execute(text("SELECT * FROM bpm_caso WHERE id_caso=:id FOR UPDATE"), {"id": case_id}).mappings().first()
    if not task or not case:
        raise BpmError("El caso o la tarea no existe")
    actor = _actor(db, operation.actor_code)
    if operation.base_revision is not None and int(task["revision"]) != operation.base_revision:
        raise BpmConflict("La tarea cambió desde la última sincronización")
    if int(task["id_responsable"] or -1) != int(actor["id_participante"]):
        raise BpmForbidden("La tarea está asignada a otro participante")
    return dict(task), dict(case), actor


def start_task(db: Session, operation: BpmOperationIn) -> dict:
    existing = _existing_operation(db, operation)
    if existing: return existing
    try:
        task, case, actor = _locked_task(db, operation)
        case_id, task_id = int(case["id_caso"]), int(task["id_actividad"])
        if case["estado"] != "ABIERTO" or task["estado"] not in ("PENDIENTE", "EN_EJECUCION"):
            raise BpmConflict("El caso o la tarea ya no permite iniciar")
        if task["estado"] == "PENDIENTE":
            db.execute(text("""
                UPDATE bpm_actividad
                SET estado='EN_EJECUCION',fecha_inicio=NOW(),id_ejecutor=:actor,
                    revision=revision+1
                WHERE id_actividad=:id
            """), {"id": task_id, "actor": actor["id_participante"]})
        result = _base_result(operation, case_id=case_id, task_id=task_id,
                              case_client_uuid=operation.case_client_uuid,
                              task_client_uuid=operation.task_client_uuid,
                              case_state="ABIERTO", task_state="EN_EJECUCION",
                              case_revision=int(case["revision"]), task_revision=int(task["revision"]) + (1 if task["estado"] == "PENDIENTE" else 0))
        _record_operation(db, operation, result, case_id, task_id)
        _history(db, operation, actor, "INICIO", case_id, task_id,
                 task["estado"], "EN_EJECUCION")
        db.commit(); return result
    except Exception:
        db.rollback(); raise


def _store_evidence(db: Session, item: BpmEvidenceIn, case_id: int, task_id: int, actor_id: int):
    try:
        content = base64.b64decode(item.content_base64, validate=True)
    except Exception as error:
        raise BpmError("La evidencia no contiene Base64 válido") from error
    digest = hashlib.sha256(content).hexdigest()
    if digest.lower() != item.sha256.lower():
        raise BpmError("La evidencia no coincide con su SHA-256")
    if not content or len(content) > 20 * 1024 * 1024:
        raise BpmError("La evidencia debe tener entre 1 byte y 20 MiB")
    existing = db.execute(text("SELECT sha256 FROM bpm_evidencia WHERE client_uuid=:uuid"), {"uuid": item.client_uuid}).scalar_one_or_none()
    if existing:
        if str(existing).lower() != digest:
            raise BpmConflict("La clave de evidencia ya fue usada con otro contenido")
        return
    db.execute(text("""
        INSERT INTO bpm_evidencia(client_uuid,id_caso,id_actividad,autor_id,
          nombre_archivo,tipo_mime,sha256,tamano,contenido,fecha_captura)
        VALUES(:uuid,:case_id,:task_id,:author,:filename,:mime,:sha,:size,:content,:captured)
    """), {"uuid": item.client_uuid, "case_id": case_id, "task_id": task_id,
             "author": actor_id, "filename": item.filename, "mime": item.mime_type,
             "sha": digest, "size": len(content), "content": content,
             "captured": item.captured_at})


def complete_task(db: Session, operation: BpmOperationIn) -> dict:
    existing = _existing_operation(db, operation)
    if existing: return existing
    if not operation.result:
        raise BpmError("El resultado es obligatorio")
    try:
        task, case, actor = _locked_task(db, operation)
        if case["estado"] != "ABIERTO" or task["estado"] != "EN_EJECUCION":
            raise BpmConflict("La tarea ya no permite finalizar")
        sha = _snapshot_hash(db, int(case["id_bpm_proceso"]))
        if operation.definition_sha256 and operation.definition_sha256.lower() != sha.lower():
            raise BpmConflict("La definición local no corresponde al caso")
        for evidence in operation.evidences:
            _store_evidence(db, evidence, int(case["id_caso"]), int(task["id_actividad"]), int(actor["id_participante"]))
        required = int(db.execute(text("""
            SELECT COALESCE(documentos_requeridos,0) FROM bpm_etapa_config
            WHERE id_bpm_proceso=:p AND id_etapa=:e
        """), {"p": task["id_bpm_proceso"], "e": task["id_etapa"]}).scalar_one_or_none() or 0)
        evidence_count = int(db.execute(text("SELECT COUNT(*) FROM bpm_evidencia WHERE id_actividad=:id"), {"id": task["id_actividad"]}).scalar_one())
        if evidence_count < required:
            raise BpmError(f"La tarea requiere {required} soporte(s) confirmado(s)")
        stage = db.execute(text("SELECT es_final FROM bpm_etapa WHERE id_bpm_proceso=:p AND id_etapa=:e"), {"p": task["id_bpm_proceso"], "e": task["id_etapa"]}).scalar_one()
        transitions = [dict(row) for row in db.execute(text("""
            SELECT * FROM bpm_transicion WHERE id_bpm_proceso=:p
              AND id_etapa_origen=:e AND activo=1
              AND (resultado=:result OR (resultado IS NULL AND UPPER(codigo_regla)='SIEMPRE'))
        """), {"p": task["id_bpm_proceso"], "e": task["id_etapa"], "result": operation.result}).mappings()]
        if not stage and len(transitions) != 1:
            raise BpmError("La tarea requiere exactamente una transición aplicable")
        affected = _participant_by_code(db, str(case["id_participante_afectado"]))
        epoch_minute = int(_now().timestamp() // 60)
        db.execute(text("""
            INSERT INTO bitacora_diaria(id_empleado,id_supervisor,ts_in_min,
              tipo_anotacion,id_proceso,origen_bitacora,observaciones,fecha_in,hora_in,client_uuid)
            VALUES(:employee,:supervisor,:ts,:stage,:stage,'BPM',:observations,CURDATE(),CURTIME(),:uuid)
        """), {"employee": affected["id_participante"],
                 "supervisor": actor["id_participante"] if actor["id_participante"] != affected["id_participante"] else None,
                 "ts": epoch_minute, "stage": task["id_etapa"],
                 "observations": (operation.observations or operation.result)[:200],
                 "uuid": operation.operation_uuid})
        bitacora_id = int(db.execute(text("SELECT LAST_INSERT_ID()")).scalar_one())
        db.execute(text("""
            UPDATE bpm_actividad SET estado='COMPLETADA',id_ejecutor=:actor,
              fecha_ejecucion=NOW(),resultado=:result,observaciones=:observations,
              id_bitacora=:bitacora,revision=revision+1 WHERE id_actividad=:id
        """), {"actor": actor["id_participante"], "result": operation.result,
                 "observations": operation.observations, "bitacora": bitacora_id,
                 "id": task["id_actividad"]})
        next_task_id = None
        case_state = "ABIERTO"
        if stage:
            active = int(db.execute(text("""
                SELECT COUNT(*) FROM bpm_actividad WHERE id_caso=:case_id
                  AND id_actividad<>:task_id AND estado IN ('PENDIENTE','EN_EJECUCION')
            """), {"case_id": case["id_caso"], "task_id": task["id_actividad"]}).scalar_one())
            if active:
                raise BpmConflict("No se puede cerrar mientras existan tareas activas")
            db.execute(text("UPDATE bpm_caso SET estado='CERRADO',fecha_cierre=NOW(),revision=revision+1 WHERE id_caso=:id"), {"id": case["id_caso"]})
            case_state = "CERRADO"
        else:
            if not operation.successor_client_uuid:
                raise BpmError("La finalización con sucesor requiere UUID local para la nueva tarea")
            transition = transitions[0]
            responsible = _resolve_responsible(db, int(task["id_bpm_proceso"]), int(transition["id_etapa_destino"]), str(case["id_participante_afectado"]), operation.responsible_id)
            successor_key = f"{operation.operation_uuid}:successor"[:100]
            next_stage = db.execute(text("SELECT plazo_horas FROM bpm_etapa WHERE id_bpm_proceso=:p AND id_etapa=:e"), {"p": task["id_bpm_proceso"], "e": transition["id_etapa_destino"]}).scalar_one_or_none()
            db.execute(text("""
                INSERT INTO bpm_actividad(id_caso,id_bpm_proceso,id_etapa,id_responsable,
                  estado,fecha_programada,fecha_vencimiento,clave_idempotencia,revision)
                VALUES(:case_id,:process,:stage,:responsible,'PENDIENTE',NOW(),
                  CASE WHEN :hours IS NULL THEN NULL ELSE DATE_ADD(NOW(),INTERVAL :minutes MINUTE) END,:key,1)
            """), {"case_id": case["id_caso"], "process": task["id_bpm_proceso"],
                     "stage": transition["id_etapa_destino"], "responsible": responsible,
                     "hours": next_stage, "minutes": int(float(next_stage or 0) * 60), "key": successor_key})
            next_task_id = int(db.execute(text("SELECT LAST_INSERT_ID()")).scalar_one())
            _map_local_id(db, "TAREA", operation.successor_client_uuid, next_task_id, operation.device)
            db.execute(text("""
                INSERT INTO bpm_enlace_actividad(id_caso,id_bpm_proceso,
                  id_actividad_origen,id_actividad_destino,id_transicion)
                VALUES(:case_id,:process,:origin,:destination,:transition)
            """), {"case_id": case["id_caso"], "process": task["id_bpm_proceso"],
                     "origin": task["id_actividad"], "destination": next_task_id,
                     "transition": transition["id_transicion"]})
            db.execute(text("UPDATE bpm_caso SET revision=revision+1 WHERE id_caso=:id"), {"id": case["id_caso"]})
        result = _base_result(operation, case_id=int(case["id_caso"]), task_id=int(task["id_actividad"]),
                              next_task_id=next_task_id,
                              case_client_uuid=operation.case_client_uuid,
                              task_client_uuid=operation.task_client_uuid,
                              successor_client_uuid=operation.successor_client_uuid,
                              case_state=case_state,
                              task_state="COMPLETADA", case_revision=int(case["revision"])+1,
                              task_revision=int(task["revision"])+1)
        _record_operation(db, operation, result, int(case["id_caso"]), int(task["id_actividad"]))
        _history(db, operation, actor, "FINALIZACION", int(case["id_caso"]), int(task["id_actividad"]),
                 "EN_EJECUCION", "COMPLETADA", detail={"result": operation.result,
                 "next_task_id": next_task_id, "bitacora_id": bitacora_id})
        db.commit(); return result
    except Exception:
        db.rollback(); raise


def admin_operation(db: Session, operation: BpmOperationIn) -> dict:
    existing = _existing_operation(db, operation)
    if existing: return existing
    if not operation.case_id:
        raise BpmError("El caso es obligatorio")
    actor = _actor(db, operation.actor_code)
    if not _is_admin(db, int(actor["id_participante"])):
        raise BpmForbidden("La operación requiere autorización administrativa BPM")
    if not (operation.reason or "").strip():
        raise BpmError("El motivo es obligatorio")
    try:
        case = db.execute(text("SELECT * FROM bpm_caso WHERE id_caso=:id FOR UPDATE"), {"id": operation.case_id}).mappings().first()
        if not case: raise BpmError("El caso no existe")
        if operation.base_revision is not None and int(case["revision"]) != operation.base_revision:
            raise BpmConflict("El caso cambió desde la última sincronización")
        before, after, task_id = str(case["estado"]), str(case["estado"]), operation.task_id
        event = operation.operation_type
        if event == "SUSPEND_CASE":
            if before != "ABIERTO": raise BpmConflict("Solo un caso abierto puede suspenderse")
            after = "SUSPENDIDO"
            db.execute(text("UPDATE bpm_caso SET estado='SUSPENDIDO',revision=revision+1 WHERE id_caso=:id"), {"id": operation.case_id})
        elif event == "RESUME_CASE":
            if before != "SUSPENDIDO": raise BpmConflict("Solo un caso suspendido puede reanudarse")
            after = "ABIERTO"
            db.execute(text("UPDATE bpm_caso SET estado='ABIERTO',revision=revision+1 WHERE id_caso=:id"), {"id": operation.case_id})
        elif event == "CANCEL_CASE":
            if before not in ("ABIERTO", "SUSPENDIDO"): raise BpmConflict("El caso no puede cancelarse")
            after = "CANCELADO"
            db.execute(text("UPDATE bpm_actividad SET estado='CANCELADA',revision=revision+1 WHERE id_caso=:id AND estado IN ('PENDIENTE','EN_EJECUCION')"), {"id": operation.case_id})
            db.execute(text("UPDATE bpm_caso SET estado='CANCELADO',fecha_cierre=NOW(),revision=revision+1 WHERE id_caso=:id"), {"id": operation.case_id})
        elif event == "REASSIGN_TASK":
            if not task_id or not operation.responsible_id: raise BpmError("Tarea y nuevo responsable son obligatorios")
            task = db.execute(text("SELECT * FROM bpm_actividad WHERE id_actividad=:id AND id_caso=:case_id FOR UPDATE"), {"id": task_id, "case_id": operation.case_id}).mappings().first()
            if not task or task["estado"] not in ("PENDIENTE", "EN_EJECUCION"):
                raise BpmConflict("La tarea no admite reasignación")
            affected = str(case["id_participante_afectado"])
            _resolve_responsible(db, int(task["id_bpm_proceso"]), int(task["id_etapa"]), affected, operation.responsible_id)
            db.execute(text("UPDATE bpm_actividad SET id_responsable=:responsible,revision=revision+1 WHERE id_actividad=:id"), {"responsible": operation.responsible_id, "id": task_id})
            db.execute(text("UPDATE bpm_caso SET revision=revision+1 WHERE id_caso=:id"), {"id": operation.case_id})
        else:
            raise BpmError("Operación administrativa no soportada")
        result = _base_result(operation, case_id=operation.case_id, task_id=task_id,
                              case_state=after, case_revision=int(case["revision"])+1)
        _record_operation(db, operation, result, operation.case_id, task_id)
        _history(db, operation, actor, event, operation.case_id, task_id,
                 before, after, operation.reason,
                 {"responsible_id": operation.responsible_id} if operation.responsible_id else None)
        db.commit(); return result
    except Exception:
        db.rollback(); raise


def process_operation(db: Session, operation: BpmOperationIn) -> dict:
    if operation.dependency_uuid:
        dependency = db.execute(text("""
            SELECT estado FROM bpm_operacion WHERE operacion_uuid=:uuid
        """), {"uuid": operation.dependency_uuid}).scalar_one_or_none()
        if dependency != "CONFIRMADA":
            raise BpmConflict("La dependencia causal no está confirmada")
    if operation.operation_type == "OPEN_CASE": return open_case(db, operation)
    if operation.operation_type == "START_TASK": return start_task(db, operation)
    if operation.operation_type == "COMPLETE_TASK": return complete_task(db, operation)
    return admin_operation(db, operation)


def list_tasks(db: Session, actor_code: str, status: str | None = None) -> list[dict]:
    actor = _actor(db, actor_code)
    params = {"actor": actor["id_participante"], "status": status,
              "is_admin": 1 if _is_admin(db, int(actor["id_participante"])) else 0}
    rows = db.execute(text("""
        SELECT a.*,c.asunto,c.estado AS caso_estado,c.id_participante_afectado,
               c.id_participante_creador,c.fecha_apertura,c.fecha_cierre,
               c.revision AS caso_revision,d.nombre AS etapa_nombre,
               r.identificacion_participante AS responsable_codigo,
               ti.client_uuid AS task_client_uuid,ci.client_uuid AS case_client_uuid,
               snap.definicion_sha256,cc.origen_tipo,cc.origen_id,
               cfg.instrucciones,cfg.resultados_json,cfg.documentos_requeridos,
               CASE WHEN a.fecha_vencimiento IS NOT NULL AND a.fecha_vencimiento<NOW()
                         AND a.estado IN ('PENDIENTE','EN_EJECUCION') THEN 1 ELSE 0 END vencida
        FROM bpm_actividad a JOIN bpm_caso c ON c.id_caso=a.id_caso
        JOIN dim_proceso d ON d.id_proceso=a.id_etapa
        LEFT JOIN participante r ON r.id_participante=a.id_responsable
        LEFT JOIN bpm_identidad_local ti ON ti.tipo_entidad='TAREA' AND ti.id_servidor=a.id_actividad
        LEFT JOIN bpm_identidad_local ci ON ci.tipo_entidad='CASO' AND ci.id_servidor=a.id_caso
        LEFT JOIN bpm_definicion_snapshot snap ON snap.id_bpm_proceso=a.id_bpm_proceso
        LEFT JOIN bpm_caso_control cc ON cc.id_caso=a.id_caso
        LEFT JOIN bpm_etapa_config cfg ON cfg.id_bpm_proceso=a.id_bpm_proceso
                                      AND cfg.id_etapa=a.id_etapa
        WHERE (:is_admin=1 OR a.id_responsable=:actor OR a.id_ejecutor=:actor)
          AND (:status IS NULL OR a.estado=:status)
        ORDER BY vencida DESC,a.fecha_vencimiento IS NULL,a.fecha_vencimiento,a.fecha_registro
    """), params).mappings()
    return [dict(row) for row in rows]


def case_detail(db: Session, case_id: int, actor_code: str) -> dict:
    actor = _actor(db, actor_code)
    case = db.execute(text("SELECT * FROM bpm_caso WHERE id_caso=:id"), {"id": case_id}).mappings().first()
    if not case: raise BpmError("El caso no existe")
    permitted = _is_admin(db, int(actor["id_participante"])) or db.execute(text("""
        SELECT 1 FROM bpm_actividad WHERE id_caso=:case_id
          AND (id_responsable=:actor OR id_ejecutor=:actor) LIMIT 1
    """), {"case_id": case_id, "actor": actor["id_participante"]}).first()
    if not permitted: raise BpmForbidden("El actor no puede consultar este caso")
    tasks = [dict(row) for row in db.execute(text("SELECT * FROM bpm_actividad WHERE id_caso=:id ORDER BY id_actividad"), {"id": case_id}).mappings()]
    links = [dict(row) for row in db.execute(text("SELECT * FROM bpm_enlace_actividad WHERE id_caso=:id ORDER BY fecha_creacion"), {"id": case_id}).mappings()]
    history = [dict(row) for row in db.execute(text("SELECT * FROM bpm_historial WHERE id_caso=:id ORDER BY fecha_servidor,id_historial"), {"id": case_id}).mappings()]
    evidences = [dict(row) for row in db.execute(text("""
        SELECT id_evidencia,client_uuid,id_caso,id_actividad,autor_id,nombre_archivo,
               tipo_mime,sha256,tamano,fecha_captura,fecha_confirmacion
        FROM bpm_evidencia WHERE id_caso=:id ORDER BY id_evidencia
    """), {"id": case_id}).mappings()]
    return {"case": dict(case), "tasks": tasks, "links": links,
            "history": history, "evidences": evidences}


def metrics(db: Session, actor_code: str, process_id: int | None = None) -> dict:
    actor = _actor(db, actor_code)
    if not _is_admin(db, int(actor["id_participante"])):
        raise BpmForbidden("Las métricas BPM requieren autorización administrativa")
    params = {"process_id": process_id}
    by_state = [dict(row) for row in db.execute(text("""
        SELECT estado,COUNT(*) cantidad
        FROM bpm_caso
        WHERE (:process_id IS NULL OR id_bpm_proceso=:process_id)
        GROUP BY estado ORDER BY estado
    """), params).mappings()]
    by_stage = [dict(row) for row in db.execute(text("""
        SELECT a.id_etapa,d.nombre,
               SUM(a.estado IN ('PENDIENTE','EN_EJECUCION')) pendientes,
               COUNT(*) ejecuciones,
               AVG(CASE WHEN a.fecha_inicio IS NOT NULL
                        THEN TIMESTAMPDIFF(SECOND,a.fecha_registro,a.fecha_inicio) END) espera_segundos,
               AVG(CASE WHEN a.fecha_ejecucion IS NOT NULL AND a.fecha_inicio IS NOT NULL
                        THEN TIMESTAMPDIFF(SECOND,a.fecha_inicio,a.fecha_ejecucion) END) ejecucion_segundos
        FROM bpm_actividad a JOIN dim_proceso d ON d.id_proceso=a.id_etapa
        WHERE (:process_id IS NULL OR a.id_bpm_proceso=:process_id)
        GROUP BY a.id_etapa,d.nombre ORDER BY a.id_etapa
    """), params).mappings()]
    duration = db.execute(text("""
        SELECT AVG(TIMESTAMPDIFF(SECOND,fecha_apertura,fecha_cierre))
        FROM bpm_caso WHERE fecha_cierre IS NOT NULL
          AND (:process_id IS NULL OR id_bpm_proceso=:process_id)
    """), params).scalar_one_or_none()
    return {"process_id": process_id, "cases_by_state": by_state,
            "stages": by_stage, "average_case_duration_seconds": duration,
            "generated_at": _now()}
