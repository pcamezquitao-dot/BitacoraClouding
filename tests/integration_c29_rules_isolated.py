"""Additional C29 contract checks against bitacora_c29_test only."""
import json
import urllib.error
import urllib.request
from decimal import Decimal

from sqlalchemy import text

from app.core.config import settings
from app.core.db import engine

BASE = "http://127.0.0.1:8012"
FIRST, LAST = 99029301, 99029340
HEADERS = {
    "Authorization": "Bearer " + settings.ADMIN_API_TOKEN,
    "X-Admin-Actor": "CP-C29-RULES",
    "X-Admin-Device": "integration-rules",
    "Content-Type": "application/json",
}
results = {}


def request(path, method="GET", payload=None, headers=HEADERS):
    body = None if payload is None else json.dumps(payload).encode()
    call = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(call, timeout=20) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def payload(process_id, name="CP-C29 Reglas", **changes):
    value = {
        "id_proceso": process_id, "nombre": name, "descripcion": None,
        "id_proceso_padre": None, "tiempo_estimado": 0,
        "costo_estimado": 0, "tipo_proceso": 1, "precondicion": None,
        "id_actividad": None, "nombre_corto": None,
    }
    value.update(changes)
    return value


def check(case, condition, evidence):
    assert condition, f"{case}: {evidence}"
    results[case] = evidence


assert settings.DB_NAME == "bitacora_c29_test"
with engine.begin() as db:
    db.execute(text("DELETE FROM dim_proceso WHERE id_proceso BETWEEN :first AND :last"), {"first": FIRST, "last": LAST})
    protected_before = {
        table: db.execute(text(f"SELECT COUNT(*) FROM {table}")).scalar_one()
        for table in ("cat_actividad", "bitacora_diaria", "bpm_proceso")
    }

invalid = [
    ("nombre-vacio", payload(FIRST, "   "), 422),
    ("nombre-51", payload(FIRST, "x" * 51), 422),
    ("referencia-101", payload(FIRST, nombre_corto="x" * 101), 422),
    ("descripcion-251", payload(FIRST, descripcion="x" * 251), 422),
    ("precondicion-251", payload(FIRST, precondicion="x" * 251), 422),
    ("tiempo-negativo", payload(FIRST, tiempo_estimado=-1), 422),
    ("tiempo-decimal", payload(FIRST, tiempo_estimado=1.5), 422),
    ("costo-negativo", payload(FIRST, costo_estimado=-0.01), 422),
    ("costo-tres-decimales", payload(FIRST, costo_estimado=0.001), 422),
    ("costo-exceso", payload(FIRST, costo_estimado=100000000), 422),
    ("tipo-distinto-1", payload(FIRST, tipo_proceso=2), 422),
]
for label, value, expected in invalid:
    status, _ = request("/admin/procesos", "POST", value)
    check(f"VALIDACION-{label}", status == expected, f"HTTP {status}")

status, _ = request("/admin/procesos", "POST", payload(FIRST, id_proceso_padre=2147483647))
check("CP-C29-017-PADRE", status == 409, f"HTTP {status}")
status, _ = request("/admin/procesos", "POST", payload(FIRST, id_actividad=4294967295))
check("CP-C29-017-ACTIVIDAD", status == 409, f"HTTP {status}")

valid = payload(
    FIRST, "  CP-C29 Límite válido  ", descripcion="d" * 250,
    tiempo_estimado=2147483647, costo_estimado=99999999.99,
    precondicion="texto; DROP TABLE imaginaria;", nombre_corto="  CP29-LIMITE  ",
)
status, created = request("/admin/procesos", "POST", valid)
check("CP-C29-009-012-013-014", status == 201 and created["nombre"] == "CP-C29 Límite válido" and created["nombre_corto"] == "CP29-LIMITE", created)

status, stale = request("/admin/procesos", "POST", payload(FIRST + 1, "CP-C29 Eliminado durante edición"))
fingerprint = stale["fingerprint"]
check("PREPARAR-ELIMINADO", status == 201, stale)
check("ELIMINAR-PREPARADO", request(f"/admin/procesos/{FIRST + 1}", "DELETE")[0] == 200, "HTTP 200")
status, _ = request(f"/admin/procesos/{FIRST + 1}", "PUT", {**payload(FIRST + 1, "CP-C29 Cambio tardío"), "original_fingerprint": fingerprint})
check("CP-C29-030-ELIMINADO", status == 404, f"HTTP {status}")

with engine.begin() as db:
    db.execute(text("DELETE FROM dim_proceso WHERE id_proceso BETWEEN :first AND :last"), {"first": FIRST, "last": LAST})
    protected_after = {
        table: db.execute(text(f"SELECT COUNT(*) FROM {table}")).scalar_one()
        for table in protected_before
    }
check("CP-C29-033", protected_before == protected_after, {"before": protected_before, "after": protected_after})
print(json.dumps(results, ensure_ascii=False, indent=2, default=str))
