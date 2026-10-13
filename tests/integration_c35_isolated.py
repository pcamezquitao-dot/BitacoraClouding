"""Prueba destructiva controlada exclusiva de bitacora_c35_test.

Requiere C35_BASE_URL, ADMIN_API_TOKEN y ADMIN_ACCESS_KEY. No imprime secretos.
"""
from __future__ import annotations

import json
import os
import uuid
from concurrent.futures import ThreadPoolExecutor

import requests


BASE = os.environ["C35_BASE_URL"].rstrip("/")
TOKEN = os.environ["ADMIN_API_TOKEN"]
KEY = os.environ["ADMIN_ACCESS_KEY"]
PREFIX = "CP-C35-" + uuid.uuid4().hex[:8]
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "X-Admin-Key": KEY,
    "X-Admin-Actor": "CP-C35-AUTOMATICO",
    "X-Admin-Device": "V2035",
}
URL = BASE + "/admin/navegacion"
created: list[int] = []
checks: dict[str, object] = {}


def call(method: str, suffix: str = "", *, expected: int, **kwargs):
    response = requests.request(method, URL + suffix, headers=HEADERS, timeout=30, **kwargs)
    assert response.status_code == expected, (method, suffix, response.status_code, response.text)
    return response.json() if response.content else None


def payload(name: str, **values):
    return {
        "tipo_accion": 1,
        "nombre": name,
        "descripcion": None,
        "id_accion_padre": None,
        "id_proceso": None,
        "request_id": str(uuid.uuid4()),
        **values,
    }


def create(name: str, **values):
    item = call("POST", expected=201, json=payload(name, **values))
    created.append(item["id_accion"])
    return item


initial = call("GET", expected=200)
assert initial == [], initial
checks["initial_empty"] = True

for headers in ({}, {"Authorization": f"Bearer {TOKEN}"}, {
    "Authorization": f"Bearer {TOKEN}", "X-Admin-Key": "incorrecta",
    "X-Admin-Actor": "CP-C35-SIN-PERMISO",
}):
    response = requests.get(URL, headers=headers, timeout=30)
    assert response.status_code == 401
checks["unauthorized_statuses"] = [401, 401, 401]

root = create("  " + PREFIX + " Raiz Z  ")
assert root["nombre"] == PREFIX + " Raiz Z"
assert root["tipo_accion"] == 1 and root["descripcion"] is None
assert root["id_accion_padre"] is None and root["id_proceso"] is None
child = create(PREFIX + " Hija", id_accion_padre=root["id_accion"])
grandchild = create(PREFIX + " Nieta", id_accion_padre=child["id_accion"])
root_a = create(PREFIX + " Raiz A")
duplicate_1 = create(PREFIX + " Duplicada", id_accion_padre=root["id_accion"])
duplicate_2 = create(PREFIX + " Duplicada", id_accion_padre=root["id_accion"])

catalog = call("GET", expected=200)
assert len(catalog) == 6
duplicates = [item for item in catalog if item["nombre"] == PREFIX + " Duplicada"]
assert [item["id_accion"] for item in duplicates] == sorted(item["id_accion"] for item in duplicates)
checks["tree_ids"] = [root["id_accion"], child["id_accion"], grandchild["id_accion"], root_a["id_accion"]]

processes = requests.get(BASE + "/admin/procesos", headers=HEADERS, timeout=30)
assert processes.status_code == 200
process = next(item for item in processes.json() if item["id_proceso"] == 19)
process_before = json.dumps(process, sort_keys=True)

updated = call("PUT", f"/{child['id_accion']}", expected=200, json={
    **payload(PREFIX + " Hija editada", descripcion="D" * 200,
              id_accion_padre=root_a["id_accion"], id_proceso=19),
    "original_fingerprint": child["fingerprint"],
})
assert updated["id_accion"] == child["id_accion"] and updated["id_proceso"] == 19

removed_process = call("PUT", f"/{child['id_accion']}", expected=200, json={
    **payload(PREFIX + " Hija editada", id_accion_padre=root_a["id_accion"]),
    "original_fingerprint": updated["fingerprint"],
})
assert removed_process["id_proceso"] is None

became_root = call("PUT", f"/{child['id_accion']}", expected=200, json={
    **payload(PREFIX + " Hija editada"),
    "original_fingerprint": removed_process["fingerprint"],
})
assert became_root["id_accion_padre"] is None

invalid_payloads = [
    payload("", request_id=str(uuid.uuid4())),
    payload("X" * 151),
    payload(PREFIX + " tipo", tipo_accion=2),
    payload(PREFIX + " desc", descripcion="D" * 201),
    payload(PREFIX + " padre", id_accion_padre=99999999),
    payload(PREFIX + " proceso", id_proceso=99999999),
]
statuses = []
for invalid in invalid_payloads:
    response = requests.post(URL, headers=HEADERS, json=invalid, timeout=30)
    statuses.append(response.status_code)
assert statuses == [422, 422, 422, 422, 409, 409], statuses
checks["validation_statuses"] = statuses

name_150 = create("N" * 150)
desc_null = create(PREFIX + " Descripcion NULL", descripcion="   ")
assert desc_null["descripcion"] is None

for parent_id in (root["id_accion"], duplicate_1["id_accion"]):
    current = call("GET", f"/{root['id_accion']}", expected=200)
    response = requests.put(URL + f"/{root['id_accion']}", headers=HEADERS, timeout=30, json={
        **payload(root["nombre"], id_accion_padre=parent_id),
        "original_fingerprint": current["fingerprint"],
    })
    assert response.status_code == 409
checks["cycle_statuses"] = [409, 409]

response = requests.delete(URL + f"/{root['id_accion']}", headers=HEADERS, timeout=30)
assert response.status_code == 409
checks["parent_delete_status"] = 409

idem_payload = payload(PREFIX + " Idempotente")
idem_first = call("POST", expected=201, json=idem_payload)
created.append(idem_first["id_accion"])
idem_second = call("POST", expected=201, json=idem_payload)
assert idem_first["id_accion"] == idem_second["id_accion"]
checks["idempotent_id"] = idem_first["id_accion"]

stale_source = call("GET", f"/{root_a['id_accion']}", expected=200)
winner_payload = {**payload(PREFIX + " Concurrencia A"), "original_fingerprint": stale_source["fingerprint"]}
loser_payload = {**payload(PREFIX + " Concurrencia B"), "original_fingerprint": stale_source["fingerprint"]}
with ThreadPoolExecutor(max_workers=2) as pool:
    responses = list(pool.map(
        lambda body: requests.put(URL + f"/{root_a['id_accion']}", headers=HEADERS, json=body, timeout=30),
        (winner_payload, loser_payload),
    ))
assert sorted(item.status_code for item in responses) == [200, 409]
checks["concurrent_edit_statuses"] = [200, 409]

race_parent = create(PREFIX + " Padre concurrencia")
race_child_payload = payload(PREFIX + " Hija concurrencia", id_accion_padre=race_parent["id_accion"])
with ThreadPoolExecutor(max_workers=2) as pool:
    create_future = pool.submit(requests.post, URL, headers=HEADERS, json=race_child_payload, timeout=30)
    delete_future = pool.submit(requests.delete, URL + f"/{race_parent['id_accion']}", headers=HEADERS, timeout=30)
    create_response, delete_response = create_future.result(), delete_future.result()
assert 409 in (create_response.status_code, delete_response.status_code)
assert ({create_response.status_code, delete_response.status_code} in ({201, 409}, {200, 409}))
if create_response.status_code == 201:
    created.append(create_response.json()["id_accion"])
else:
    created.remove(race_parent["id_accion"])
checks["concurrent_parent_delete_statuses"] = sorted(
    (create_response.status_code, delete_response.status_code)
)

leaf = create(PREFIX + " Hoja eliminar", id_proceso=19)
process_snapshot = process_before
call("DELETE", f"/{leaf['id_accion']}", expected=200)
created.remove(leaf["id_accion"])
assert requests.get(BASE + "/admin/procesos", headers=HEADERS, timeout=30).status_code == 200
process_after = next(item for item in requests.get(BASE + "/admin/procesos", headers=HEADERS, timeout=30).json()
                     if item["id_proceso"] == 19)
assert json.dumps(process_after, sort_keys=True) == process_snapshot
checks["process_19_unchanged"] = True

for action_id in reversed(created):
    response = requests.delete(URL + f"/{action_id}", headers=HEADERS, timeout=30)
    if response.status_code == 409:
        continue
    assert response.status_code == 200, (action_id, response.status_code, response.text)
for action_id in reversed(created):
    requests.delete(URL + f"/{action_id}", headers=HEADERS, timeout=30)

final = call("GET", expected=200)
assert final == [], final
checks["final_empty"] = True
print(json.dumps({"prefix": PREFIX, "checks": checks}, ensure_ascii=False, sort_keys=True))
