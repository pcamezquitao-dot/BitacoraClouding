"""CP-C28 backend integration runner. Execute only with DB_NAME=bitacora_c28_test."""
import json
import threading
import urllib.error
import urllib.parse
import urllib.request
from uuid import uuid4

from sqlalchemy import text

from app.core.config import settings
from app.core.db import engine


BASE = "http://127.0.0.1:8011"
PREFIX = "CP-C28-"
SUFFIX = uuid4().hex[:8]
HEADERS = {
    "Authorization": "Bearer " + settings.ADMIN_API_TOKEN,
    "X-Admin-Actor": "CP-C28",
    "X-Admin-Device": "integration-runner",
    "Content-Type": "application/json",
}
results = {}


def request(path, method="GET", payload=None, headers=HEADERS):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            body = response.read()
            return response.status, json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        body = error.read()
        return error.code, json.loads(body) if body else None


def mark(code, condition, evidence):
    assert condition, f"{code}: {evidence}"
    results[code] = evidence


assert settings.DB_NAME == "bitacora_c28_test"
with engine.begin() as db:
    existing = db.execute(
        text("SELECT id_actividad,nombre FROM cat_actividad WHERE nombre LIKE 'CP-C28-%'")
    ).mappings().all()
    print("PRE_CLEAN_SELECT", [dict(row) for row in existing])
    if existing:
        db.execute(text("DELETE FROM dim_proceso WHERE id_actividad IN "
                        "(SELECT id_actividad FROM cat_actividad WHERE nombre LIKE 'CP-C28-%')"))
        db.execute(text("DELETE FROM cat_actividad WHERE nombre LIKE 'CP-C28-%'"))

status, empty = request("/admin/actividades")
mark("CP-C28-03", status == 200 and empty == [], "HTTP 200 y lista vacia")

name1 = f"CP-C28-Validar-excusa-{SUFFIX}"
status, created = request("/admin/actividades", "POST", {
    "nombre": name1, "descripcion": "Revisar evidencia presentada"
})
mark("CP-C28-02", status == 201, f"HTTP {status}; tabla efectiva cat_actividad")
mark("CP-C28-04", created["id_actividad"] > 0 and created["nombre"] == name1, created)
id1 = created["id_actividad"]

name2 = f"CP-C28-Sin-descripcion-{SUFFIX}"
status, optional = request("/admin/actividades", "POST", {"nombre": name2, "descripcion": ""})
mark("CP-C28-05", status == 201 and optional["descripcion"] is None, optional)
id2 = optional["id_actividad"]

for value in ("", "   "):
    status, _ = request("/admin/actividades", "POST", {"nombre": value})
    assert status == 422
mark("CP-C28-06", True, "vacio y espacios rechazados HTTP 422")

name100 = "CP-C28-" + "x" * 93
status100, row100 = request("/admin/actividades", "POST", {"nombre": name100})
status101, _ = request("/admin/actividades", "POST", {"nombre": "CP-C28-" + "y" * 94})
mark("CP-C28-07", status100 == 201 and len(row100["nombre"]) == 100 and status101 == 422,
     f"100={status100}, 101={status101}")

trimmed = f"CP-C28-Recortada-{SUFFIX}"
status, trimrow = request("/admin/actividades", "POST", {"nombre": f"  {trimmed}  "})
mark("CP-C28-08", status == 201 and trimrow["nombre"] == trimmed, trimrow)

status, _ = request("/admin/actividades", "POST", {"nombre": name1})
mark("CP-C28-09", status == 409, "duplicado exacto HTTP 409")
status, _ = request("/admin/actividades", "POST", {"nombre": name1.upper()})
mark("CP-C28-10", status == 409, "utf8mb4_unicode_ci rechaza variante mayuscula HTTP 409")

updated_name = f"CP-C28-Actualizada-{SUFFIX}"
status, updated = request(f"/admin/actividades/{id1}", "PUT", {
    "nombre": updated_name, "descripcion": "Descripcion actualizada"
})
mark("CP-C28-11", status == 200 and updated["id_actividad"] == id1, updated)
status, _ = request(f"/admin/actividades/{id1}", "PUT", {
    "nombre": name2, "descripcion": "No debe persistir"
})
status_detail, after_conflict = request(f"/admin/actividades/{id1}")
mark("CP-C28-12", status == 409 and after_conflict["nombre"] == updated_name,
     "conflicto HTTP 409 y original conservado")

status, found = request("/admin/actividades?search=" + urllib.parse.quote("Actualizada"))
status_detail, detail = request(f"/admin/actividades/{id1}")
mark("CP-C28-14", status == 200 and len(found) == 1 and status_detail == 200
     and detail["descripcion"] == "Descripcion actualizada", detail)

concurrent_name = f"CP-C28-Concurrente-{SUFFIX}"
codes = []
def concurrent_create():
    codes.append(request("/admin/actividades", "POST", {"nombre": concurrent_name})[0])
threads = [threading.Thread(target=concurrent_create) for _ in range(2)]
[thread.start() for thread in threads]
[thread.join() for thread in threads]
mark("CP-C28-17", sorted(codes) == [201, 409], codes)

status, _ = request("/admin/actividades", headers={"X-Admin-Actor": "sin-token"})
mark("CP-C28-18", status == 401, "llamada directa sin token HTTP 401")

with engine.begin() as db:
    db.execute(text("""
        INSERT INTO dim_proceso (id_proceso,nombre,descripcion,id_actividad,nombre_corto)
        VALUES (98002801,:n1,'CP-C28 proceso uno',:activity,'CP28P1'),
               (98002802,:n2,'CP-C28 proceso dos',:activity,'CP28P2')
    """), {"n1": f"CP-C28 Proceso 1 {SUFFIX}", "n2": f"CP-C28 Proceso 2 {SUFFIX}", "activity": id1})
    refs = db.execute(text("SELECT COUNT(*) FROM dim_proceso WHERE id_actividad=:id"), {"id": id1}).scalar_one()
mark("CP-C28-19", refs == 2, f"dos procesos referencian id_actividad={id1}")
status, shared = request(f"/admin/actividades/{id1}", "PUT", {
    "nombre": updated_name, "descripcion": "Descripcion compartida"
})
with engine.connect() as db:
    refs_after = db.execute(text("SELECT COUNT(*) FROM dim_proceso WHERE id_actividad=:id"), {"id": id1}).scalar_one()
mark("CP-C28-20", status == 200 and refs_after == 2 and shared["id_actividad"] == id1,
     "descripcion modificada; dos referencias e ID conservados")

long_text = "Linea uno\nLinea áéíóú " + "z" * 1000
status, longrow = request(f"/admin/actividades/{id2}", "PUT", {"nombre": name2, "descripcion": long_text})
too_long = "á" * 40000
status_too_long, _ = request(f"/admin/actividades/{id2}", "PUT", {"nombre": name2, "descripcion": too_long})
mark("CP-C28-22", status == 200 and longrow["descripcion"] == long_text and status_too_long == 422,
     f"texto integro; exceso HTTP {status_too_long}")

status, persisted = request(f"/admin/actividades/{id2}")
mark("CP-C28-23", status == 200 and persisted["id_actividad"] == id2, persisted)
status, _ = request("/admin/actividades/999999999", "PUT", {"nombre": "CP-C28-Ausente"})
mark("CP-C28-24", status == 404, "registro ausente HTTP 404")

with engine.connect() as db:
    plural = db.execute(text("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cat_actividades'")).scalar_one()
mark("CP-C28-21", plural == 0, "sin tabla plural ni operaciones fuera de alcance")

print(json.dumps(results, ensure_ascii=False, indent=2, default=str))
