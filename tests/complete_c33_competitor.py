"""Consume una tarea C33 desde un segundo cliente para probar conflicto offline."""
from __future__ import annotations

import os
import uuid
from datetime import datetime, timezone

import requests

BASE = os.getenv("C33_BASE_URL", "https://161-22-47-89.sslip.io/bitacora-c33-test/").rstrip("/") + "/"
TASK_ID = int(os.environ["C33_TASK_ID"])
tasks = requests.get(BASE + "bpm/tasks", params={"actor": "CP_C33_RH"}, timeout=30).json()
task = next(item for item in tasks if item["id_actividad"] == TASK_ID)
definitions = requests.get(BASE + "bpm/definitions", timeout=30).json()
definition = next(item for item in definitions if item["id_bpm_proceso"] == task["id_bpm_proceso"])
operation_uuid = str(uuid.uuid4())
payload = {
    "operation_uuid": operation_uuid,
    "operation_type": "COMPLETE_TASK",
    "actor_code": "CP_C33_RH",
    "device": "CP_C33_SECOND_CLIENT",
    "captured_at": datetime.now(timezone.utc).isoformat(),
    "base_revision": task["revision"],
    "process_id": task["id_bpm_proceso"],
    "definition_sha256": definition["sha256"],
    "case_id": task["id_caso"],
    "task_id": task["id_actividad"],
    "case_client_uuid": task["case_client_uuid"],
    "task_client_uuid": task["task_client_uuid"],
    "successor_client_uuid": str(uuid.uuid4()),
    "result": "ACEPTADO",
    "observations": "CP_C33_SEGUNDO_CLIENTE",
    "novelty_type": 1,
    "novelty_area_id": 10006,
    "novelty_start_date": "2026-10-10",
    "novelty_end_date": "2026-10-10",
}
response = requests.post(BASE + "bpm/operations", json=payload, timeout=30)
assert response.status_code == 200, (response.status_code, response.text)
print({"operation_uuid": operation_uuid, **response.json()})
