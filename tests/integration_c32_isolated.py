"""Recorrido de integración C32 contra un backend/base exclusivamente aislados."""
from __future__ import annotations

import base64
import hashlib
import json
import os
import time
import urllib.error
import urllib.request
from uuid import uuid4


BASE = os.environ.get("C32_BASE_URL", "http://127.0.0.1:8015/").rstrip("/") + "/"


def request(path, payload=None, expected=200):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            body, status = response.read().decode(), response.status
    except urllib.error.HTTPError as error:
        body, status = error.read().decode(), error.code
    assert status == expected, (status, body)
    return json.loads(body) if body else None


def op(kind, actor, case_uuid=None, task_uuid=None, revision=None, **extra):
    return {
        "operation_uuid": str(uuid4()), "operation_type": kind,
        "actor_code": actor, "device": "CP_C32_INTEGRATION",
        "captured_at": "2026-10-09T22:30:00", "base_revision": revision,
        "case_client_uuid": case_uuid, "task_client_uuid": task_uuid, **extra,
    }


def evidence(label):
    raw = f"soporte sintético {label}".encode()
    return {
        "client_uuid": str(uuid4()), "filename": f"{label}.txt", "mime_type": "text/plain",
        "sha256": hashlib.sha256(raw).hexdigest(),
        "content_base64": base64.b64encode(raw).decode(),
        "captured_at": "2026-10-09T22:30:00",
    }


def send(payload, expected=200):
    return request("bpm/operations", payload, expected)


def run():
    definitions = request("bpm/definitions")
    definition = next(item for item in definitions if item["nombre"] == "CP_C32_INASISTENCIA")
    process_id, sha = definition["id_bpm_proceso"], definition["sha256"]

    novelty_case = "c32-nov-case-00000000000000000001"
    novelty_task = "c32-nov-task-00000000000000000001"
    novelty_open = op("OPEN_CASE", "P0001", process_id=process_id, definition_sha256=sha,
                      case_client_uuid=novelty_case, task_client_uuid=novelty_task,
                      subject="CP_C32 desde Novedades", affected_code="CP_C32_EMP",
                      source_type="NOVEDAD", source_id="932001",
                      source_table="supervisor_novedad")
    novelty_open["operation_uuid"] = "c32-nov-open-00000000000000000001"
    novelty = send(novelty_open)
    assert send(novelty_open) == novelty
    novelty_detail = request(f"bpm/cases/{novelty['case_id']}?actor=P0001")
    assert novelty_detail["case"]["estado"] == "ABIERTO"
    employee_tasks = request("bpm/tasks?actor=CP_C32_EMP")
    downloaded = next(item for item in employee_tasks if item["id_caso"] == novelty["case_id"])
    for field in ("case_client_uuid", "task_client_uuid", "definicion_sha256",
                  "caso_estado", "id_participante_creador", "origen_tipo"):
        assert downloaded[field] is not None, (field, downloaded)

    case_uuid, support1, review1 = str(uuid4()), str(uuid4()), str(uuid4())
    opening = op("OPEN_CASE", "P0001", process_id=process_id, definition_sha256=sha,
                 case_client_uuid=case_uuid, task_client_uuid=support1,
                 subject="CP_C32 recorrido integral", affected_code="CP_C32_EMP", source_type="MANUAL")
    opened = send(opening)
    assert send(opening) == opened

    started = send(op("START_TASK", "CP_C32_EMP", case_uuid, support1, 1,
                      process_id=process_id, definition_sha256=sha))
    assert started["task_state"] == "EN_EJECUCION"
    completed = send(op("COMPLETE_TASK", "CP_C32_EMP", case_uuid, support1, 2,
                        process_id=process_id, definition_sha256=sha,
                        successor_client_uuid=review1, result="SOPORTE_PRESENTADO",
                        observations="Soporte inicial", evidences=[evidence("inicial")]))
    assert completed["next_task_id"]

    send(op("START_TASK", "CP_C32_RH", case_uuid, review1, 1,
            process_id=process_id, definition_sha256=sha))
    support2 = str(uuid4())
    correction = send(op("COMPLETE_TASK", "CP_C32_RH", case_uuid, review1, 2,
                         process_id=process_id, definition_sha256=sha,
                         successor_client_uuid=support2, result="REQUIERE_CORRECCION",
                         observations="Corrija el soporte"))
    assert correction["next_task_id"] != completed["next_task_id"]

    send(op("START_TASK", "CP_C32_EMP", case_uuid, support2, 1,
            process_id=process_id, definition_sha256=sha))
    review2 = str(uuid4())
    send(op("COMPLETE_TASK", "CP_C32_EMP", case_uuid, support2, 2,
            process_id=process_id, definition_sha256=sha,
            successor_client_uuid=review2, result="SOPORTE_PRESENTADO",
            observations="Soporte corregido", evidences=[evidence("corregido")]))

    send(op("START_TASK", "CP_C32_RH", case_uuid, review2, 1,
            process_id=process_id, definition_sha256=sha))
    final_task = str(uuid4())
    send(op("COMPLETE_TASK", "CP_C32_RH", case_uuid, review2, 2,
            process_id=process_id, definition_sha256=sha,
            successor_client_uuid=final_task, result="ACEPTADO", observations="Aceptado"))
    send(op("START_TASK", "CP_C32_RH", case_uuid, final_task, 1,
            process_id=process_id, definition_sha256=sha))
    closed = send(op("COMPLETE_TASK", "CP_C32_RH", case_uuid, final_task, 2,
                     process_id=process_id, definition_sha256=sha,
                     result="CERRAR", observations="Cierre final"))
    assert closed["case_state"] == "CERRADO"

    detail = request(f"bpm/cases/{closed['case_id']}?actor=CP_C32_RH")
    assert detail["case"]["estado"] == "CERRADO"
    assert len(detail["tasks"]) == 5
    assert len(detail["links"]) == 4
    assert len(detail["evidences"]) == 2
    assert all(task["id_bitacora"] for task in detail["tasks"] if task["estado"] == "COMPLETADA")
    metrics = request(f"bpm/metrics?actor=P0001&process_id={process_id}")
    assert metrics["stages"] and metrics["cases_by_state"]
    request(f"bpm/metrics?actor=CP_C32_EMP&process_id={process_id}", expected=403)

    changed = dict(opening); changed["subject"] = "contenido diferente"
    request("bpm/operations", changed, 409)
    print(json.dumps({"case_id": closed["case_id"], "tasks": len(detail["tasks"]),
                      "links": len(detail["links"]), "evidences": len(detail["evidences"]),
                      "history": len(detail["history"]), "status": "APPROVED"}))


if __name__ == "__main__":
    run()
