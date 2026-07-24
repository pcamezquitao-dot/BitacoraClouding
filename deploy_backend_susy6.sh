#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE="bitacora-api.service"
APP_DIR="/srv/bitacora"
SOURCE_DIR="${1:-/tmp/despliegue_susy6}"
BASE_URL="http://127.0.0.1:8001"
REPORT="/root/deploy_susy6_resultado.txt"
EXPECTED_COUNT=64
REQUIRED_ID=68
EXPECTED_EVIDENCE_COUNT=2
BACKUP_DIR=""
INSTALLED=0
BACKEND_STATUS="REVERTIDO"
FASTAPI_STATUS="FALLO"
BITACORA_COUNT="NO_DISPONIBLE"
HAS_ID68="NO"
EVIDENCE_COUNT="NO_DISPONIBLE"

write_report() {
    local message="${1:-Proceso completado}"
    {
        printf 'RESPALDO_REMOTO=%s\n' "${BACKUP_DIR:-NO_CREADO}"
        printf 'BACKEND=%s\n' "$BACKEND_STATUS"
        printf 'FASTAPI=%s\n' "$FASTAPI_STATUS"
        printf 'BITACORAS=%s\n' "$BITACORA_COUNT"
        printf 'ID_BITACORA_68=%s\n' "$HAS_ID68"
        printf 'EVIDENCIAS_68=%s\n' "$EVIDENCE_COUNT"
        printf 'MENSAJE=%s\n' "$message"
    } > "$REPORT"
}

rollback_on_error() {
    local exit_code=$?
    local failed_line="${BASH_LINENO[0]:-desconocida}"
    trap - ERR
    set +e
    if [[ "$INSTALLED" -eq 1 && -n "$BACKUP_DIR" && -d "$BACKUP_DIR" ]]; then
        install -m 0644 "$BACKUP_DIR/app/routers/bitacora_uc03.py" \
            "$APP_DIR/app/routers/bitacora_uc03.py"
        install -m 0644 "$BACKUP_DIR/app/schemas/bitacora.py" \
            "$APP_DIR/app/schemas/bitacora.py"
        systemctl restart "$SERVICE"
        systemctl is-active --quiet "$SERVICE" && FASTAPI_STATUS="ACTIVO"
    fi
    BACKEND_STATUS="REVERTIDO"
    write_report "Fallo en linea $failed_line; reversión automática ejecutada (codigo $exit_code)"
    exit "$exit_code"
}
trap rollback_on_error ERR

[[ "$(id -u)" -eq 0 ]]
[[ "$APP_DIR" == "/srv/bitacora" ]]
[[ -f "$APP_DIR/app/main.py" ]]
[[ -f "$SOURCE_DIR/app/routers/bitacora_uc03.py" ]]
[[ -f "$SOURCE_DIR/app/schemas/bitacora.py" ]]

if [[ -x "$APP_DIR/venv/bin/python" ]]; then
    PYTHON="$APP_DIR/venv/bin/python"
elif [[ -x "$APP_DIR/.venv/bin/python" ]]; then
    PYTHON="$APP_DIR/.venv/bin/python"
else
    printf 'No se encontró el Python del entorno virtual de %s\n' "$APP_DIR" >&2
    false
fi

"$PYTHON" -m py_compile \
    "$SOURCE_DIR/app/routers/bitacora_uc03.py" \
    "$SOURCE_DIR/app/schemas/bitacora.py"

BACKUP_DIR="/root/backups/bitacora_susy6_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR/app/routers" "$BACKUP_DIR/app/schemas"
cp -a "$APP_DIR/app/routers/bitacora_uc03.py" "$BACKUP_DIR/app/routers/"
cp -a "$APP_DIR/app/schemas/bitacora.py" "$BACKUP_DIR/app/schemas/"

install -m 0644 "$SOURCE_DIR/app/routers/bitacora_uc03.py" \
    "$APP_DIR/app/routers/bitacora_uc03.py"
install -m 0644 "$SOURCE_DIR/app/schemas/bitacora.py" \
    "$APP_DIR/app/schemas/bitacora.py"
INSTALLED=1

cd "$APP_DIR"
"$PYTHON" -m py_compile app/routers/bitacora_uc03.py app/schemas/bitacora.py app/main.py
"$PYTHON" -c 'from app.main import app; assert app is not None'

systemctl restart "$SERVICE"
systemctl is-active --quiet "$SERVICE"
FASTAPI_STATUS="ACTIVO"

for _ in {1..20}; do
    if curl -fsS --max-time 5 "$BASE_URL/health" >/dev/null; then
        break
    fi
    sleep 1
done
curl -fsS --max-time 10 "$BASE_URL/health" >/dev/null

OPENAPI_FILE="$(mktemp)"
BITACORAS_FILE="$(mktemp)"
EVIDENCIAS_FILE="$(mktemp)"
trap 'rm -f "$OPENAPI_FILE" "$BITACORAS_FILE" "$EVIDENCIAS_FILE"' EXIT
curl -fsS --max-time 15 "$BASE_URL/openapi.json" -o "$OPENAPI_FILE"

LIST_ENDPOINT="$($PYTHON - "$OPENAPI_FILE" <<'PY'
import json, sys
paths = json.load(open(sys.argv[1], encoding="utf-8"))["paths"]
candidates = []
for path, operations in paths.items():
    normalized = path.lower()
    if "get" in operations and "bitacora" in normalized and "diaria" in normalized and "{" not in path:
        candidates.append(path)
if not candidates:
    raise SystemExit("No se encontró endpoint GET de listado de bitácoras")
print(sorted(candidates, key=lambda value: (value != "/bitacora_diaria", len(value)))[0])
PY
)"

BASE_URL="$BASE_URL" LIST_ENDPOINT="$LIST_ENDPOINT" OUTPUT_FILE="$BITACORAS_FILE" \
"$PYTHON" <<'PY'
import json, os, urllib.parse, urllib.request
base = os.environ["BASE_URL"]
endpoint = os.environ["LIST_ENDPOINT"]
rows, offset, limit = [], 0, 200
while True:
    query = urllib.parse.urlencode({"offset": offset, "limit": limit})
    with urllib.request.urlopen(f"{base}{endpoint}?{query}", timeout=15) as response:
        page = json.load(response)
    if not isinstance(page, list):
        raise SystemExit("El endpoint de bitácoras no devolvió una lista JSON")
    rows.extend(page)
    if len(page) < limit:
        break
    offset += len(page)
with open(os.environ["OUTPUT_FILE"], "w", encoding="utf-8") as output:
    json.dump(rows, output)
PY

read -r BITACORA_COUNT HAS_ID68 < <("$PYTHON" - "$BITACORAS_FILE" "$REQUIRED_ID" <<'PY'
import json, sys
rows = json.load(open(sys.argv[1], encoding="utf-8"))
required = int(sys.argv[2])
print(len(rows), "SI" if any(int(row.get("id_bitacora", -1)) == required for row in rows) else "NO")
PY
)
[[ "$BITACORA_COUNT" -eq "$EXPECTED_COUNT" ]]
[[ "$HAS_ID68" == "SI" ]]

curl -fsS --max-time 15 \
    "$BASE_URL/bitacora-area-evidencias?id_bitacora=$REQUIRED_ID&offset=0&limit=200" \
    -o "$EVIDENCIAS_FILE"
EVIDENCE_COUNT="$($PYTHON - "$EVIDENCIAS_FILE" <<'PY'
import json, sys
rows = json.load(open(sys.argv[1], encoding="utf-8"))
if not isinstance(rows, list):
    raise SystemExit("La consulta de evidencias no devolvió una lista JSON")
print(len(rows))
PY
)"
[[ "$EVIDENCE_COUNT" -eq "$EXPECTED_EVIDENCE_COUNT" ]]

# Regresión de una consulta individual existente; no crea ni modifica datos.
curl -fsS --max-time 15 "$BASE_URL/bitacora_diaria/$REQUIRED_ID" >/dev/null

BACKEND_STATUS="ACTUALIZADO"
write_report "Despliegue y validaciones completados correctamente; endpoint=$LIST_ENDPOINT"
trap - ERR
exit 0
