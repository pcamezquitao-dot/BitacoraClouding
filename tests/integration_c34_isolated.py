"""Integración de solo lectura contra fixtures exclusivos de bitacora_c34_test."""
import os
import requests


BASE = os.environ["C34_BASE_URL"].rstrip("/")


def post(code):
    return requests.post(BASE + "/portal-general/identificar", json={"codigo": code}, timeout=20)


valid = post("CP-C34-ACTIVO")
assert valid.status_code == 200, valid.text
portal = valid.json()
assert portal["estado_menu"] == "OK"
assert portal["participante"]["tipo_descripcion"] == "CP_C34_ROL"
assert [item["id_accion"] for item in portal["opciones"]] == [93414, 93412, 93411, 93413]
assert next(item for item in portal["opciones"] if item["id_accion"] == 93411)["id_bpm_proceso"] == 93431
assert next(item for item in portal["opciones"] if item["id_accion"] == 93413)["estado_destino"] == "SIN_FLUJO_BPM"

for code in ("NO-EXISTE", "CP-C34-INACTIVO"):
    response = post(code)
    assert response.status_code == 403, (code, response.status_code, response.text)
assert post("CP-C34-MULTI").status_code == 409
assert post("CP-C34-SIN-TIPO").status_code == 403

missing = post("CP-C34-SIN-RAIZ")
assert missing.status_code == 200 and missing.json()["estado_menu"] == "RAIZ_AUSENTE"
duplicate = post("CP-C34-DUPLICADA")
assert duplicate.status_code == 200 and duplicate.json()["estado_menu"] == "RAIZ_DUPLICADA"
empty = post("CP-C34-SIN-HIJOS")
assert empty.status_code == 200 and empty.json()["estado_menu"] == "SIN_HIJOS"

available = requests.get(BASE + "/portal-general/CP-C34-ACTIVO/opciones/93411", timeout=20)
assert available.status_code == 200 and available.json()["estado_destino"] == "DISPONIBLE"
assert requests.get(BASE + "/portal-general/CP-C34-ACTIVO/opciones/93412", timeout=20).status_code == 409
assert requests.get(BASE + "/portal-general/CP-C34-ACTIVO/opciones/93413", timeout=20).status_code == 409
assert requests.get(BASE + "/portal-general/CP-C34-SIN-RAIZ/opciones/93411", timeout=20).status_code == 403

print({"portal": portal["estado_menu"], "options": len(portal["opciones"]),
       "valid_bpm": available.json()["id_bpm_proceso"], "writes": 0})
