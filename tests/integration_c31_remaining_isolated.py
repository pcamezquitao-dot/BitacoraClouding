"""C31 remaining cases against bitacora_c31_test only."""
from dataclasses import dataclass
from pathlib import Path
import json
import socket
import time
import urllib.request

from sqlalchemy import text

from app.core.admin_auth import AdminIdentity
from app.core.config import settings
from app.core.db import SessionLocal
from app.services.xml2er_service import (
    Xml2ErConflict,
    Xml2ErError,
    import_definition,
    parse_xml,
    preview,
)

BASE = Path("/tmp/CP-C31-fisico.xml").read_bytes()
IDENTITY = AdminIdentity("CP-C31-RESTANTES", "integration")
results = {}


def check(case, condition, evidence):
    assert condition, f"{case}: {evidence}"
    results[case] = evidence


def replace(old: bytes, new: bytes):
    value = BASE.replace(old, new)
    assert value != BASE
    return value


with SessionLocal() as db:
    check("ENTORNO", db.execute(text("SELECT DATABASE()" )).scalar_one() == "bitacora_c31_test", settings.DB_NAME)

    # 007: same collation key, incompatible description.
    xml = replace(b"Actividad fisica aislada C31.", b"Descripcion incompatible CP-C31.")
    _, report = preview(db, xml, "007.xml")
    check("CP-C31-007", report["estado"] == "CON ERRORES" and any("descripci" in x for x in report["conflictos"]), report["conflictos"])

    # 008: reuse an existing parent and activity by their persisted identifiers.
    xml = b'''<?xml version="1.0" encoding="UTF-8"?>
<definicion_proceso contrato_version="1"><actividades></actividades><nodos>
<nodo ref="work" id_proceso="932101" nombre="CP_C31_REF_WORK" nombre_corto="CP31_REF_WORK" padre_id="932000" actividad_nombre="CP-C31 FISICO Registrar"/>
<nodo ref="end" id_proceso="932102" nombre="CP_C31_REF_END" nombre_corto="CP31_REF_END" padre_id="932000"/>
</nodos><proceso nombre="CP_C31_REF" version="1" activo="0"><etapas>
<etapa ref="e1" nodo_ref="work" rol_responsable="supervisor" es_inicial="1"/><etapa ref="e2" nodo_ref="end" es_final="1"/>
</etapas><transiciones><transicion ref="t1" origen_ref="e1" destino_ref="e2"/></transiciones></proceso></definicion_proceso>'''
    _, report = preview(db, xml, "008.xml")
    check("CP-C31-008", report["estado"] == "VALIDADO" and report["cantidades"]["dim_proceso"]["nuevos"] == 2, {"estado": report["estado"], "nodos_nuevos": 2, "conflictos": report["conflictos"]})

    # 009: persisted ID with changed content and new ID with occupied name.
    by_id = replace(b'nombre="CP_C31_FISICO_RAIZ"', b'nombre="CP_C31_FISICO_RAIZ_CAMBIO"')
    _, report_id = preview(db, by_id, "009-id.xml")
    by_name = replace(b'id_proceso="932000"', b'id_proceso="932900"')
    _, report_name = preview(db, by_name, "009-name.xml")
    check("CP-C31-009", report_id["estado"] == report_name["estado"] == "CON ERRORES", [report_id["conflictos"], report_name["conflictos"]])

    # 011/014: an existing version cannot change a business value.
    changed = replace(b"Proceso fisico aislado y trazable CP-C31.", b"Proceso fisico MODIFICADO CP-C31.")
    _, changed_report = preview(db, changed, "changed.xml")
    try:
        import_definition(db, changed, "changed.xml", changed_report["token_validacion"], IDENTITY)
        conflict = False
    except Xml2ErConflict:
        conflict = True
    check("CP-C31-011", conflict, "version existente rechazada")
    check("CP-C31-014", conflict, "cambio semantico no equivalente")

    # 015-017: parser limits, numeric ranges, NULL/defaults and no truncation.
    accepted = replace(b'nombre="CP_C31_FISICO_RAIZ"', b'nombre="' + b"N" * 50 + b'"')
    check("CP-C31-015", parse_xml(accepted).nodes[0]["nombre"] == "N" * 50, "limite 50 aceptado; >50 cubierto por pytest")
    try:
        parse_xml(replace(b'id_proceso="932000"', b'id_proceso="2147483648"'))
        range_rejected = False
    except Xml2ErError:
        range_rejected = True
    check("CP-C31-016", range_rejected, "entero fuera de rango rechazado")
    defaults = parse_xml(BASE)
    check("CP-C31-017", defaults.nodes[0]["descripcion"] is None and defaults.nodes[0]["tiempo_estimado"] == 0 and str(defaults.nodes[0]["costo_estimado"]) == "0.00", "NULL/defaults exactos")

    # 018: actual collation behavior plus preview collision.
    collation_equal = db.execute(text("SELECT CAST('CP-C31 FISICO Registrar' AS CHAR) COLLATE utf8mb4_unicode_ci = CAST('cp-c31 fisico registrar' AS CHAR) COLLATE utf8mb4_unicode_ci")).scalar_one()
    case_variant = replace(b"CP-C31 FISICO Registrar", b"cp-c31 fisico registrar")
    _, case_report = preview(db, case_variant, "018.xml")
    check("CP-C31-018", collation_equal == 1 and case_report["cantidades"]["cat_actividad"]["reutilizados"] >= 1, case_report["cantidades"]["cat_actividad"])

    # 031: incompatible schema is surfaced and writes remain unchanged.
    before = db.execute(text("SELECT COUNT(1) FROM bpm_proceso WHERE nombre='CP_C31_FISICO'" )).scalar_one()
    class BrokenSchema:
        def execute(self, *_args, **_kwargs):
            raise RuntimeError("tabla requerida ausente (simulada)")
    try:
        preview(BrokenSchema(), BASE, "031.xml")
        schema_failed = False
    except RuntimeError:
        schema_failed = True
    after = db.execute(text("SELECT COUNT(1) FROM bpm_proceso WHERE nombre='CP_C31_FISICO'" )).scalar_one()
    check("CP-C31-031", schema_failed and before == after, f"conteo {before}->{after}")

# 032: client sends a fresh import then loses the response; retry is idempotent.
lost = (BASE.replace(b"9320", b"9380")
        .replace(b"CP_C31_FISICO", b"CP_C31_LOST")
        .replace(b"CP31_F_", b"CP31_L_")
        .replace(b"CP-C31 FISICO", b"CP-C31 LOST"))
headers = {"Authorization": "Bearer " + settings.ADMIN_API_TOKEN, "X-Admin-Actor": "CP-C31-LOST", "Content-Type": "application/json"}
with SessionLocal() as db:
    _, validation = preview(db, lost, "lost.xml")
    assert validation["estado"] == "VALIDADO", validation
payload = json.dumps({"archivo": "lost.xml", "xml": lost.decode(), "token_validacion": validation["token_validacion"]}).encode()
request_bytes = (b"POST /admin/xml2er/importar HTTP/1.1\r\nHost: 127.0.0.1\r\n" + f"Authorization: Bearer {settings.ADMIN_API_TOKEN}\r\nX-Admin-Actor: CP-C31-LOST\r\nContent-Type: application/json\r\nContent-Length: {len(payload)}\r\nConnection: close\r\n\r\n".encode() + payload)
sock = socket.create_connection(("127.0.0.1", 8014), timeout=10)
sock.sendall(request_bytes)
sock.close()  # deliberate response loss
time.sleep(2)
req = urllib.request.Request("http://127.0.0.1:8014/admin/xml2er/importar", data=payload, headers=headers, method="POST")
# The first request is intentionally abandoned while the server still owns
# the import lock.  Allow its bounded 15-second database-lock window plus
# normal request processing before judging the recovery retry.
with urllib.request.urlopen(req, timeout=45) as response:
    retried = json.load(response)
with SessionLocal() as db:
    count = db.execute(text("SELECT COUNT(1) FROM bpm_proceso WHERE nombre='CP_C31_LOST' AND version=1")).scalar_one()
check("CP-C31-032", retried["estado"] == "SIN CAMBIOS" and count == 1, {"estado": retried["estado"], "procesos": count})

print(json.dumps(results, ensure_ascii=False, indent=2, default=str))
