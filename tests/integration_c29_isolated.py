"""Integration runner for bitacora_c29_test only."""
import json
import threading
import urllib.error
import urllib.request

from sqlalchemy import text

from app.core.config import settings
from app.core.db import engine

BASE = "http://127.0.0.1:8012"
IDS = list(range(99029001, 99029016))
HEADERS = {
    "Authorization": "Bearer " + settings.ADMIN_API_TOKEN,
    "X-Admin-Actor": "CP-C29",
    "X-Admin-Device": "integration-runner",
    "Content-Type": "application/json",
}
results = {}


def request(path, method="GET", payload=None, headers=HEADERS):
    body = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        data = error.read()
        return error.code, json.loads(data) if data else None


def mark(code, condition, evidence):
    assert condition, f"{code}: {evidence}"
    results[code] = evidence


def payload(process_id, name, parent=None, activity=None, reference=None, **extra):
    value = {
        "id_proceso": process_id, "nombre": name, "descripcion": None,
        "id_proceso_padre": parent, "tiempo_estimado": 0, "costo_estimado": 0,
        "tipo_proceso": 1, "precondicion": None, "id_actividad": activity,
        "nombre_corto": reference,
    }
    value.update(extra)
    return value


assert settings.DB_NAME == "bitacora_c29_test"
with engine.begin() as db:
    existing = db.execute(text("SELECT id_proceso,nombre FROM dim_proceso WHERE id_proceso BETWEEN :first AND :last"), {"first": IDS[0], "last": IDS[-1]}).all()
    assert not existing, f"IDs reservados ocupados: {existing}"

status, rows = request("/admin/procesos")
mark("CP-C29-001", status == 200 and len(rows) >= 41, f"HTTP {status}; {len(rows)} procesos")
status, _ = request("/admin/procesos", headers={})
mark("CP-C29-002", status == 401, "sin token HTTP 401")

root = payload(IDS[0], "CP-C29 Raíz", reference="CP29-RAIZ")
status, root_out = request("/admin/procesos", "POST", root)
mark("CP-C29-006", status == 201 and root_out["id_proceso_padre"] is None, root_out)
child = payload(IDS[1], "CP-C29 Hijo", IDS[0], reference="CP29-HIJO")
grand = payload(IDS[2], "CP-C29 Nieto", IDS[1], reference="CP29-NIETO")
mark("CP-C29-007", request("/admin/procesos", "POST", child)[0] == 201 and request("/admin/procesos", "POST", grand)[0] == 201, "raíz-hijo-nieto creados")

status, same = request("/admin/procesos", "POST", root)
mark("CP-C29-027", status == 201 and same["id_proceso"] == IDS[0], "reintento idéntico idempotente")
status, _ = request("/admin/procesos", "POST", payload(IDS[0], "CP-C29 Código ocupado"))
mark("CP-C29-008", status == 409, "código ocupado HTTP 409")
status, _ = request("/admin/procesos", "POST", payload(IDS[3], "cp-c29 raíz"))
mark("CP-C29-010", status == 409, "nombre equivalente según collation HTTP 409")
status, _ = request("/admin/procesos", "POST", payload(IDS[3], "CP-C29 Ref duplicada", reference="cp29-raiz"))
mark("CP-C29-011", status == 409, "referencia equivalente HTTP 409")

status, _ = request("/admin/procesos", "POST", payload(IDS[3], "CP-C29 Ciclo", parent=IDS[3]))
mark("CP-C29-018", status == 409, "autoparentesco HTTP 409")

activity_id = None
with engine.connect() as db:
    activity_id = db.execute(text("SELECT MIN(id_actividad) FROM cat_actividad")).scalar_one()
assert activity_id is not None
p4 = payload(IDS[3], "CP-C29 Actividad A", activity=activity_id)
p5 = payload(IDS[4], "CP-C29 Actividad B", activity=activity_id)
out4 = request("/admin/procesos", "POST", p4)[1]
out5 = request("/admin/procesos", "POST", p5)[1]
mark("CP-C29-016", out4["id_actividad"] == out5["id_actividad"] == activity_id, "actividad compartida")

updated = {**root, "nombre": "CP-C29 Raíz Editada", "original_fingerprint": root_out["fingerprint"]}
status, updated_out = request(f"/admin/procesos/{IDS[0]}", "PUT", updated)
mark("CP-C29-019", status == 200 and updated_out["id_proceso"] == IDS[0], updated_out)
stale = {**updated, "descripcion": "obsoleta"}
status, _ = request(f"/admin/procesos/{IDS[0]}", "PUT", stale)
mark("CP-C29-030", status == 409, "huella obsoleta HTTP 409")

move = {**grand, "id_proceso_padre": IDS[0], "original_fingerprint": request(f"/admin/procesos/{IDS[2]}")[1]["fingerprint"]}
status, moved = request(f"/admin/procesos/{IDS[2]}", "PUT", move)
mark("CP-C29-020", status == 200 and moved["id_proceso_padre"] == IDS[0], moved)
cycle = {**updated, "id_proceso_padre": IDS[2], "original_fingerprint": updated_out["fingerprint"]}
status, _ = request(f"/admin/procesos/{IDS[0]}", "PUT", cycle)
mark("CP-C29-018B", status == 409, "ciclo indirecto HTTP 409")

status, _ = request(f"/admin/procesos/{IDS[0]}", "DELETE")
mark("CP-C29-024", status == 409, "padre con hijos protegido")
status, _ = request("/admin/procesos/1", "DELETE")
mark("CP-C29-025", status == 409, "proceso productivo referenciado protegido")

leaf = payload(IDS[5], "CP-C29 Hoja eliminable")
request("/admin/procesos", "POST", leaf)
status, deleted = request(f"/admin/procesos/{IDS[5]}", "DELETE")
with engine.connect() as db:
    absent = db.execute(text("SELECT COUNT(*) FROM dim_proceso WHERE id_proceso=:id"), {"id": IDS[5]}).scalar_one()
mark("CP-C29-023", status == 200 and absent == 0, deleted)

codes = []
def create_competing(process_id):
    competing = payload(process_id, "CP-C29 Concurrente", reference="CP29-CONC")
    codes.append(request("/admin/procesos", "POST", competing)[0])
threads = [threading.Thread(target=create_competing, args=(process_id,)) for process_id in IDS[6:8]]
for thread in threads: thread.start()
for thread in threads: thread.join()
with engine.connect() as db:
    count = db.execute(text("SELECT COUNT(*) FROM dim_proceso WHERE nombre=:name"), {"name": "CP-C29 Concurrente"}).scalar_one()
mark("CP-C29-029", len(codes) == 2 and sorted(codes) == [201, 409] and count == 1, {"codes": codes, "count": count})

print(json.dumps(results, ensure_ascii=False, indent=2, default=str))
