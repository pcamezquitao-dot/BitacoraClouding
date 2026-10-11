"""Prepara una tarea Supervisor RH pendiente para el recorrido fisico aislado."""
from __future__ import annotations

import base64
import hashlib
import os
import uuid
from datetime import datetime, timezone

import requests

BASE = os.getenv("C33_BASE_URL", "https://161-22-47-89.sslip.io/bitacora-c33-test/").rstrip("/") + "/"
PREFIX = "CP_C33_FISICO_" + uuid.uuid4().hex[:8]


def payload(kind: str, actor: str, **values) -> dict:
    return {"operation_uuid": str(uuid.uuid4()), "operation_type": kind,
            "actor_code": actor, "device": "V2035_CP_C33",
            "captured_at": datetime.now(timezone.utc).isoformat(), **values}


def post(values: dict) -> dict:
    response = requests.post(BASE + "bpm/operations", json=values, timeout=30)
    assert response.status_code == 200, (response.status_code, response.text)
    return response.json()


definitions = requests.get(BASE + "bpm/definitions", timeout=30).json()
definition = next(item for item in definitions if item["nombre"] == "CP_C33_INASISTENCIA")
case_uuid, task_uuid = str(uuid.uuid4()), str(uuid.uuid4())
opened = post(payload("OPEN_CASE", "CP_C33_RH", process_id=definition["id_bpm_proceso"],
    definition_sha256=definition["sha256"], case_client_uuid=case_uuid,
    task_client_uuid=task_uuid, subject=PREFIX, affected_code="CP_C33_EMP", source_type="MANUAL"))
started = post(payload("START_TASK", "CP_C33_EMP", case_id=opened["case_id"],
    task_id=opened["task_id"], case_client_uuid=case_uuid, task_client_uuid=task_uuid,
    base_revision=1, process_id=definition["id_bpm_proceso"], definition_sha256=definition["sha256"]))
content = (PREFIX + "_SOPORTE_ORIGEN").encode()
completed = post(payload("COMPLETE_TASK", "CP_C33_EMP", case_id=opened["case_id"],
    task_id=opened["task_id"], case_client_uuid=case_uuid, task_client_uuid=task_uuid,
    successor_client_uuid=str(uuid.uuid4()), base_revision=started["task_revision"],
    process_id=definition["id_bpm_proceso"], definition_sha256=definition["sha256"],
    result="SOPORTE_PRESENTADO", observations=PREFIX,
    evidences=[{"client_uuid": str(uuid.uuid4()), "filename": "soporte_origen.txt",
      "mime_type": "text/plain", "sha256": hashlib.sha256(content).hexdigest(),
      "content_base64": base64.b64encode(content).decode(),
      "captured_at": datetime.now(timezone.utc).isoformat()}]))
print({"prefix": PREFIX, "case_id": opened["case_id"], "employee_task_id": opened["task_id"],
       "rh_task_id": completed["next_task_id"], "case_uuid": case_uuid})
