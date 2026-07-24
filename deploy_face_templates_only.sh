#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE="bitacora-api.service"
APP_DIR="/srv/bitacora"
SOURCE_DIR="${1:?Falta SOURCE_DIR}"
BASE_URL="http://127.0.0.1:8001"
PUBLIC_HEALTH_URL="http://161.22.47.89/bitacora/health"
BACKUP_DIR="/root/backups/face_templates_$(date +%Y%m%d_%H%M%S)"
REPORT="/root/deploy_face_templates_resultado.txt"
INSTALLED=0

FILES=(
  app/main.py
  app/routers/face_templates.py
  app/schemas/face_template.py
  app/services/face_template_service.py
)

rollback() {
  local code=$?
  local line="${BASH_LINENO[0]:-desconocida}"
  trap - ERR
  set +e
  if [[ "$INSTALLED" -eq 1 ]]; then
    for file in "${FILES[@]}"; do
      if [[ -f "$BACKUP_DIR/$file" ]]; then
        install -D -m 0644 "$BACKUP_DIR/$file" "$APP_DIR/$file"
      else
        rm -f "$APP_DIR/$file"
      fi
    done
    systemctl restart "$SERVICE"
    for _ in {1..20}; do
      systemctl is-active --quiet "$SERVICE" && \
        curl -fsS --max-time 5 "$BASE_URL/health" >/dev/null && break
      sleep 1
    done
  fi
  printf 'RESULTADO=REVERTIDO\nERROR_LINEA=%s\nERROR_CODIGO=%s\nRESPALDO=%s\n' \
    "$line" "$code" "$BACKUP_DIR" > "$REPORT"
  exit "$code"
}
trap rollback ERR

[[ "$(id -u)" -eq 0 ]]
[[ -d "$APP_DIR" && -f "$APP_DIR/app/main.py" ]]
[[ -x "$APP_DIR/venv/bin/python" ]]
PYTHON="$APP_DIR/venv/bin/python"

mapfile -t received < <(cd "$SOURCE_DIR" && find app -type f -print | sort)
mapfile -t expected < <(printf '%s\n' "${FILES[@]}" | sort)
[[ "${received[*]}" == "${expected[*]}" ]]

for file in "${FILES[@]}"; do
  [[ -f "$SOURCE_DIR/$file" ]]
done
[[ -f "$SOURCE_DIR/app/routers/face_templates.py" ]]
[[ ! -e "$SOURCE_DIR/app/routers/Face_Templates.py" ]]

"$PYTHON" -m py_compile "${FILES[@]/#/$SOURCE_DIR/}"

mkdir -p "$BACKUP_DIR"
printf '%s\n' "${FILES[@]}" > "$BACKUP_DIR/manifest.txt"
for file in "${FILES[@]}"; do
  if [[ -f "$APP_DIR/$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp -a "$APP_DIR/$file" "$BACKUP_DIR/$file"
  fi
done

for file in "${FILES[@]}"; do
  install -D -m 0644 "$SOURCE_DIR/$file" "$APP_DIR/$file"
done
INSTALLED=1

cd "$APP_DIR"
FACE_IMPORT="$($PYTHON -c "from app.routers.face_templates import router; print('FACE IMPORT OK')")"
MAIN_IMPORT="$($PYTHON -c "from app.main import app; print('MAIN IMPORT OK')")"
[[ "$FACE_IMPORT" == "FACE IMPORT OK" ]]
[[ "$MAIN_IMPORT" == "MAIN IMPORT OK" ]]

systemctl restart "$SERVICE"
for _ in {1..20}; do
  systemctl is-active --quiet "$SERVICE" && \
    curl -fsS --max-time 5 "$BASE_URL/health" >/dev/null && break
  sleep 1
done
systemctl is-active --quiet "$SERVICE"
curl -fsS --max-time 10 "$BASE_URL/health" >/dev/null
curl -fsS --max-time 10 "$PUBLIC_HEALTH_URL" >/dev/null

VALIDATION="$($PYTHON <<'PY'
import base64
import hashlib
import json
import struct
import urllib.request

from sqlalchemy import text

from app.core.config import settings
from app.core.db import engine

BASE = "http://127.0.0.1:8001"
TOKEN = settings.FACE_TEMPLATE_API_TOKEN
assert TOKEN, "FACE_TEMPLATE_API_TOKEN no configurado"
assert settings.FACE_TEMPLATE_MASTER_KEY, "FACE_TEMPLATE_MASTER_KEY no configurada"


def request(path, method="GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Authorization": "Bearer " + TOKEN}
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as response:
        body = response.read()
        return response.status, json.loads(body) if body else None


with urllib.request.urlopen(BASE + "/openapi.json", timeout=20) as response:
    paths = json.load(response)["paths"]
assert "/face-templates" in paths
assert "post" in paths["/face-templates"]

with engine.connect() as db:
    participant = db.execute(text("""
        SELECT p.id_participante, p.identificacion_participante
        FROM participante p
        LEFT JOIN participante_face_template ft
          ON ft.id_participante = p.id_participante AND ft.active = 1
        WHERE ft.id_face_template IS NULL
        ORDER BY p.id_participante
        LIMIT 1
    """)).mappings().first()
    assert participant is not None, "No hay participante libre para prueba no destructiva"
    before = db.execute(text(
        "SELECT COUNT(*) FROM participante_face_template WHERE id_participante=:id"
    ), {"id": participant["id_participante"]}).scalar_one()

raw = struct.pack(">128f", *([0.1875] * 128))
digest = hashlib.sha256(raw).hexdigest()
payload = {
    "client_uuid": "face-deploy-validation-v1",
    "id_participante": participant["id_participante"],
    "participant_code": participant["identificacion_participante"],
    "display_name": "Validacion facial controlada",
    "embedding_base64": base64.b64encode(raw).decode("ascii"),
    "embedding_sha256": digest,
    "model_version": "deploy-validation-128",
    "device_id": "server-validation",
}
first_status, first = request("/face-templates", "POST", payload)
second_status, second = request("/face-templates", "POST", payload)
assert first_status == 200 and second_status == 200
assert first["id_face_template"] == second["id_face_template"]

with engine.connect() as db:
    rows = db.execute(text("""
        SELECT id_face_template, id_participante, embedding_sha256, active
        FROM participante_face_template
        WHERE id_participante=:id AND embedding_sha256=:digest
        ORDER BY id_face_template
    """), {"id": participant["id_participante"], "digest": digest}).mappings().all()
    after = db.execute(text(
        "SELECT COUNT(*) FROM participante_face_template WHERE id_participante=:id"
    ), {"id": participant["id_participante"]}).scalar_one()

assert after == before + 1
assert len(rows) == 1
assert bool(rows[0]["active"])
print(json.dumps({
    "endpoint": "POST /face-templates",
    "http_first": first_status,
    "http_retry": second_status,
    "id_face_template": rows[0]["id_face_template"],
    "id_participante": rows[0]["id_participante"],
    "matching_rows": len(rows),
    "duplicate_created": False,
}, separators=(",", ":")))
PY
)"

systemctl is-active --quiet "$SERVICE"
journalctl -u "$SERVICE" -n 80 --no-pager > /root/deploy_face_templates_journal.txt
printf 'RESULTADO=ACTUALIZADO\nRESPALDO=%s\nFACE_IMPORT=%s\nMAIN_IMPORT=%s\nSERVICIO=ACTIVO\nVALIDACION=%s\n' \
  "$BACKUP_DIR" "$FACE_IMPORT" "$MAIN_IMPORT" "$VALIDATION" > "$REPORT"
trap - ERR
