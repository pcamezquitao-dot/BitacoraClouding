"""Recorrido API C33 exclusivo de bitacora_c33_test."""
from __future__ import annotations

import base64
import hashlib
import os
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

import requests

BASE = os.getenv("C33_BASE_URL", "https://161-22-47-89.sslip.io/bitacora-c33-test/").rstrip("/") + "/"
PREFIX = "CP_C33_" + uuid.uuid4().hex[:8]


def operation(payload: dict, expected: int = 200) -> dict:
    response = requests.post(BASE + "bpm/operations", json=payload, timeout=30)
    assert response.status_code == expected, (response.status_code, response.text)
    return response.json()


def base(kind: str, actor: str, **values) -> dict:
    return {
        "operation_uuid": str(uuid.uuid4()), "operation_type": kind,
        "actor_code": actor, "device": "CP_C33_API", "captured_at": datetime.now(timezone.utc).isoformat(),
        **values,
    }


definitions = requests.get(BASE + "bpm/definitions", timeout=30).json()
definition = next(item for item in definitions if item["nombre"] == "CP_C33_INASISTENCIA")

case_uuid, first_task_uuid = str(uuid.uuid4()), str(uuid.uuid4())
opened = operation(base("OPEN_CASE", "CP_C33_RH", process_id=definition["id_bpm_proceso"],
    definition_sha256=definition["sha256"], case_client_uuid=case_uuid,
    task_client_uuid=first_task_uuid, subject=PREFIX, affected_code="CP_C33_EMP",
    source_type="MANUAL"))
case_id, first_task_id = opened["case_id"], opened["task_id"]

started = operation(base("START_TASK", "CP_C33_EMP", case_id=case_id, task_id=first_task_id,
    case_client_uuid=case_uuid, task_client_uuid=first_task_uuid, base_revision=1,
    process_id=definition["id_bpm_proceso"], definition_sha256=definition["sha256"]))

content = (PREFIX + "_SOPORTE").encode()
first_done = operation(base("COMPLETE_TASK", "CP_C33_EMP", case_id=case_id, task_id=first_task_id,
    case_client_uuid=case_uuid, task_client_uuid=first_task_uuid, successor_client_uuid=str(uuid.uuid4()),
    base_revision=started["task_revision"], process_id=definition["id_bpm_proceso"],
    definition_sha256=definition["sha256"], result="SOPORTE_PRESENTADO", observations=PREFIX,
    evidences=[{"client_uuid": str(uuid.uuid4()), "filename": "soporte.txt", "mime_type": "text/plain",
      "sha256": hashlib.sha256(content).hexdigest(), "content_base64": base64.b64encode(content).decode(),
      "captured_at": datetime.now(timezone.utc).isoformat()}]))
rh_task_id = first_done["next_task_id"]
rh_task = next(x for x in requests.get(BASE + "bpm/tasks", params={"actor": "CP_C33_RH"}, timeout=30).json()
               if x["id_actividad"] == rh_task_id)

rh_started = operation(base("START_TASK", "CP_C33_RH", case_id=case_id, task_id=rh_task_id,
    case_client_uuid=case_uuid, task_client_uuid=rh_task["task_client_uuid"], base_revision=1,
    process_id=definition["id_bpm_proceso"], definition_sha256=definition["sha256"]))

invalid = base("COMPLETE_TASK", "CP_C33_RH", case_id=case_id, task_id=rh_task_id,
    case_client_uuid=case_uuid, task_client_uuid=rh_task["task_client_uuid"],
    successor_client_uuid=str(uuid.uuid4()), base_revision=rh_started["task_revision"],
    process_id=definition["id_bpm_proceso"], definition_sha256=definition["sha256"],
    result="REQUIERE_CORRECCION", novelty_type=1, novelty_area_id=10006,
    novelty_start_date="2026-10-10", novelty_end_date="2026-10-10")
operation(invalid, 422)

photo, audio, video = b"CP_C33_PHOTO", b"CP_C33_AUDIO", b"CP_C33_VIDEO"
evidences = []
for filename, mime, data in (("foto.jpg", "image/jpeg", photo), ("audio.m4a", "audio/mp4", audio),
                             ("video.mp4", "video/mp4", video)):
    evidences.append({"client_uuid": str(uuid.uuid4()), "filename": filename, "mime_type": mime,
        "sha256": hashlib.sha256(data).hexdigest(), "content_base64": base64.b64encode(data).decode(),
        "captured_at": datetime.now(timezone.utc).isoformat()})

confirmed_payload = {**invalid, "operation_uuid": str(uuid.uuid4()), "observations": "CP_C33_CORREGIR",
                     "evidences": evidences}
# Dos clientes intentan decidir la misma revision. Solo uno puede confirmar.
competing_payload = {**confirmed_payload, "operation_uuid": str(uuid.uuid4()),
                     "observations": "CP_C33_CLIENTE_COMPETIDOR"}
with ThreadPoolExecutor(max_workers=2) as executor:
    responses = list(executor.map(
        lambda payload: requests.post(BASE + "bpm/operations", json=payload, timeout=30),
        (confirmed_payload, competing_payload),
    ))
assert sorted(response.status_code for response in responses) == [200, 409], [
    (response.status_code, response.text) for response in responses
]
confirmed_response = next(response for response in responses if response.status_code == 200)
confirmed_payload = (confirmed_payload if confirmed_response.json()["operation_uuid"] ==
                     confirmed_payload["operation_uuid"] else competing_payload)
confirmed = confirmed_response.json()
replayed = operation(confirmed_payload)
assert replayed == confirmed

different = {**confirmed_payload, "observations": "CP_C33_CONTENIDO_DIFERENTE"}
operation(different, 409)

print({"prefix": PREFIX, "case_id": case_id, "first_task_id": first_task_id,
       "rh_task_id": rh_task_id, "next_task_id": confirmed["next_task_id"],
       "bitacora_id": confirmed["bitacora_id"], "novelty_id": confirmed["novelty_id"],
       "operation_uuid": confirmed["operation_uuid"], "evidences": len(evidences),
       "concurrency_statuses": sorted(response.status_code for response in responses)})
