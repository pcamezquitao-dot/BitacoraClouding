#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SERVICE="bitacora-api.service"
APP_DIR="/srv/bitacora"
SOURCE_DIR="/tmp/despliegue_catalogos"
BASE_URL="http://127.0.0.1:8001"
TEST_CODE="P0014"

FILES=(
  "app/main.py"
  "app/routers/catalogos.py"
  "app/routers/participante.py"
  "app/schemas/catalogos.py"
)

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$APP_DIR/deploy-backups/catalogos-$STAMP"
DEPLOYMENT_STARTED=0
ROLLBACK_RUNNING=0
PYTHON_BIN=""
TEST_DIR=""

log() {
  printf '\n[%s] %s\n' "$(date -u +%FT%TZ)" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  return 1
}

restore_backup() {
  local file

  [[ "$DEPLOYMENT_STARTED" -eq 1 ]] || return 0
  [[ -d "$BACKUP_DIR" ]] ||
    fail "No existe el respaldo para revertir: $BACKUP_DIR"

  log "Revirtiendo archivos desde $BACKUP_DIR"

  for file in "${FILES[@]}"; do
    if [[ -f "$BACKUP_DIR/files/$file" ]]; then
      install -D -p "$BACKUP_DIR/files/$file" "$APP_DIR/$file"
    elif [[ -f "$BACKUP_DIR/absent/$file" ]]; then
      rm -f -- "$APP_DIR/$file"
    else
      fail "El respaldo no define el estado anterior de $file"
    fi
  done

  log "Reiniciando únicamente $SERVICE después de la reversión"
  systemctl restart "$SERVICE"
  systemctl is-active --quiet "$SERVICE"
  echo "REVERSIÓN AUTOMÁTICA COMPLETADA" >&2
}

on_error() {
  local rc=$?

  trap - ERR INT TERM
  echo "El despliegue falló con código $rc." >&2

  if [[ "$ROLLBACK_RUNNING" -eq 0 ]]; then
    ROLLBACK_RUNNING=1
    if ! restore_backup; then
      echo "ATENCIÓN: la reversión automática no pudo completarse." >&2
      echo "Respaldo disponible en: $BACKUP_DIR" >&2
    fi
  fi

  exit "$rc"
}

cleanup() {
  if [[ -n "$TEST_DIR" && -d "$TEST_DIR" ]]; then
    rm -rf -- "$TEST_DIR"
  fi
}

trap on_error ERR INT TERM
trap cleanup EXIT

log "1/10 — Verificando el backend activo"

[[ "$(id -u)" -eq 0 ]] ||
  fail "Este script debe ejecutarse como root."

systemctl cat "$SERVICE" >/dev/null
systemctl is-active --quiet "$SERVICE" ||
  fail "$SERVICE no está activo antes del despliegue."

MAIN_PID="$(systemctl show "$SERVICE" --property MainPID --value)"
[[ "$MAIN_PID" =~ ^[1-9][0-9]*$ ]] ||
  fail "MainPID inválido para $SERVICE: $MAIN_PID"
[[ -d "/proc/$MAIN_PID" ]] ||
  fail "No existe el proceso $MAIN_PID."

PROCESS_CWD="$(readlink -f "/proc/$MAIN_PID/cwd")"
[[ "$PROCESS_CWD" == "$APP_DIR" ]] ||
  fail "El servicio ejecuta desde $PROCESS_CWD y no desde $APP_DIR."

PROCESS_COMMAND="$(tr '\0' ' ' < "/proc/$MAIN_PID/cmdline")"
case "$PROCESS_COMMAND" in
  *uvicorn*app.main:app*) ;;
  *) fail "El proceso activo no ejecuta uvicorn app.main:app: $PROCESS_COMMAND" ;;
esac

[[ -f "$APP_DIR/app/main.py" ]] ||
  fail "No existe $APP_DIR/app/main.py."

ss -ltn 2>/dev/null |
  grep -Eq '127\.0\.0\.1:8001|0\.0\.0\.0:8001|\[::\]:8001' ||
  fail "No se encontró FastAPI escuchando en el puerto 8001."

log "2/10 — Identificando el entorno virtual"

if [[ -x "$APP_DIR/.venv/bin/python" ]]; then
  PYTHON_BIN="$APP_DIR/.venv/bin/python"
elif [[ -x "$APP_DIR/venv/bin/python" ]]; then
  PYTHON_BIN="$APP_DIR/venv/bin/python"
else
  fail "No se encontró el Python virtual de $APP_DIR."
fi

"$PYTHON_BIN" -c \
  'import fastapi, sqlalchemy, pydantic; print("Entorno virtual válido")'

log "3/10 — Validando los cuatro archivos transferidos"

for file in "${FILES[@]}"; do
  [[ -s "$SOURCE_DIR/$file" ]] ||
    fail "Falta o está vacío: $SOURCE_DIR/$file"
  echo "Encontrado: $SOURCE_DIR/$file"
done

mapfile -t TRANSFERRED_FILES < <(
  cd "$SOURCE_DIR"
  find app -type f -printf '%p\n' | LC_ALL=C sort
)
mapfile -t EXPECTED_FILES < <(
  printf '%s\n' "${FILES[@]}" | LC_ALL=C sort
)

[[ "${TRANSFERRED_FILES[*]}" == "${EXPECTED_FILES[*]}" ]] ||
  fail "El staging contiene archivos distintos de los cuatro autorizados."

log "4/10 — Validando sintaxis antes de modificar el backend"

"$PYTHON_BIN" - "${FILES[@]/#/$SOURCE_DIR/}" <<'PY'
from pathlib import Path
import sys

for filename in sys.argv[1:]:
    path = Path(filename)
    compile(path.read_text(encoding="utf-8"), str(path), "exec")
    print(f"Sintaxis correcta: {path}")
PY

log "5/10 — Creando respaldo fechado"

mkdir -p "$BACKUP_DIR/files" "$BACKUP_DIR/absent"

{
  echo "timestamp=$STAMP"
  echo "service=$SERVICE"
  echo "main_pid_before=$MAIN_PID"
  echo "working_directory=$APP_DIR"
  echo "source_directory=$SOURCE_DIR"
  echo "base_url=$BASE_URL"
} > "$BACKUP_DIR/deployment.info"

for file in "${FILES[@]}"; do
  if [[ -f "$APP_DIR/$file" ]]; then
    install -D -p "$APP_DIR/$file" "$BACKUP_DIR/files/$file"
    sha256sum "$APP_DIR/$file" >> "$BACKUP_DIR/previous-files.sha256"
  else
    mkdir -p "$BACKUP_DIR/absent/$(dirname "$file")"
    : > "$BACKUP_DIR/absent/$file"
  fi
  sha256sum "$SOURCE_DIR/$file" >> "$BACKUP_DIR/new-files.sha256"
done

echo "Respaldo creado: $BACKUP_DIR"

log "6/10 — Instalando exclusivamente los cuatro archivos"

DEPLOYMENT_STARTED=1

for file in "${FILES[@]}"; do
  source="$SOURCE_DIR/$file"
  target="$APP_DIR/$file"
  target_directory="$(dirname "$target")"
  temporary="$target_directory/.deploy-$STAMP-$(basename "$file")"

  [[ -d "$target_directory" ]] ||
    fail "No existe el directorio: $target_directory"

  if [[ -e "$target" ]]; then
    owner="$(stat -c '%u' "$target")"
    group="$(stat -c '%g' "$target")"
    mode="$(stat -c '%a' "$target")"
  else
    owner="$(stat -c '%u' "$target_directory")"
    group="$(stat -c '%g' "$target_directory")"
    mode="644"
  fi

  install --owner="$owner" --group="$group" --mode="$mode" \
    "$source" "$temporary"
  mv -f -- "$temporary" "$target"
  cmp --silent "$source" "$target" ||
    fail "La copia instalada no coincide: $file"
done

log "7/10 — Validando sintaxis, importación y registro de rutas"

"$PYTHON_BIN" - "${FILES[@]/#/$APP_DIR/}" <<'PY'
from pathlib import Path
import sys

for filename in sys.argv[1:]:
    path = Path(filename)
    compile(path.read_text(encoding="utf-8"), str(path), "exec")
    print(f"Sintaxis instalada correcta: {path}")
PY

cd "$APP_DIR"
PYTHONDONTWRITEBYTECODE=1 "$PYTHON_BIN" - <<'PY'
from app.main import app

expected = {
    "/health": "get",
    "/catalogos/offline": "get",
    "/participante/search": "get",
    "/participante/by_qr/{qr}": "get",
    "/bitacora_diaria/{id_bitacora}": "get",
}
paths = app.openapi()["paths"]

for path, method in expected.items():
    if path not in paths or method not in paths[path]:
        raise SystemExit(f"Ruta o método ausente: {method.upper()} {path}")

print("Importación y registro de rutas correctos.")
PY

log "8/10 — Reiniciando únicamente $SERVICE"

systemctl restart "$SERVICE"
systemctl is-active --quiet "$SERVICE" ||
  fail "$SERVICE no quedó activo."

NEW_MAIN_PID="$(systemctl show "$SERVICE" --property MainPID --value)"
[[ "$NEW_MAIN_PID" =~ ^[1-9][0-9]*$ ]] ||
  fail "MainPID inválido después del reinicio."
[[ "$NEW_MAIN_PID" != "$MAIN_PID" ]] ||
  fail "No se confirmó el reinicio: el PID no cambió."

log "9/10 — Esperando la disponibilidad de FastAPI"

READY=0
for attempt in $(seq 1 20); do
  if curl --silent --show-error --fail --max-time 5 \
      "$BASE_URL/health" >/dev/null; then
    READY=1
    break
  fi
  sleep 2
done
[[ "$READY" -eq 1 ]] ||
  fail "FastAPI no respondió correctamente después del reinicio."

log "10/10 — Ejecutando las pruebas HTTP obligatorias"

TEST_DIR="$(mktemp -d)"

curl --silent --show-error --fail-with-body --max-time 15 \
  "$BASE_URL/health" > "$TEST_DIR/health.json"

curl --silent --show-error --fail-with-body --max-time 60 \
  "$BASE_URL/catalogos/offline" > "$TEST_DIR/catalogos.json"

curl --silent --show-error --fail-with-body --max-time 30 \
  --get --data-urlencode "q=$TEST_CODE" \
  "$BASE_URL/participante/search" > "$TEST_DIR/search.json"

curl --silent --show-error --fail-with-body --max-time 30 \
  "$BASE_URL/participante/by_qr/P0014" > "$TEST_DIR/by-qr.json"

"$PYTHON_BIN" - \
  "$TEST_DIR/health.json" \
  "$TEST_DIR/catalogos.json" \
  "$TEST_DIR/search.json" \
  "$TEST_DIR/by-qr.json" <<'PY'
import json
import sys

health, catalog, search, participant = (
    json.load(open(path, encoding="utf-8")) for path in sys.argv[1:]
)

if not isinstance(health, dict):
    raise SystemExit("/health no devolvió un objeto JSON.")

required = {"generated_at", "participantes", "areas", "empleado_areas"}
missing = required.difference(catalog)
if missing:
    raise SystemExit(f"/catalogos/offline está incompleto: {sorted(missing)}")

if not isinstance(search, list) or not search:
    raise SystemExit("/participante/search?q=P0014 no devolvió resultados.")

if participant.get("identificacion_participante", "").strip().upper() != "P0014":
    raise SystemExit("/participante/by_qr/P0014 devolvió otro participante.")

print("/health: correcto")
print("/catalogos/offline: correcto")
print("/participante/search?q=P0014: correcto")
print("/participante/by_qr/P0014: correcto")
PY

# Prueba de no regresión de solo lectura. La ruta existente puede responder 404
# si la bitácora 0 no existe; OpenAPI ya confirmó que la ruta sigue publicada.
BITACORA_STATUS="$(
  curl --silent --show-error --max-time 30 \
    --output "$TEST_DIR/bitacora.json" \
    --write-out '%{http_code}' \
    "$BASE_URL/bitacora_diaria/0"
)"

case "$BITACORA_STATUS" in
  200)
    echo "/bitacora_diaria/0: correcto (HTTP 200)"
    ;;
  404)
    "$PYTHON_BIN" - "$TEST_DIR/bitacora.json" <<'PY'
import json
import sys

result = json.load(open(sys.argv[1], encoding="utf-8"))
if not isinstance(result, dict) or "detail" not in result:
    raise SystemExit("El HTTP 404 no tiene el formato esperado de FastAPI.")
print("/bitacora_diaria/0: ruta correcta; registro inexistente (HTTP 404)")
PY
    ;;
  *)
    cat "$TEST_DIR/bitacora.json" >&2 || true
    fail "/bitacora_diaria/0 respondió HTTP $BITACORA_STATUS."
    ;;
esac

systemctl is-active --quiet "$SERVICE" ||
  fail "$SERVICE dejó de estar activo durante las pruebas."

trap - ERR INT TERM
DEPLOYMENT_STARTED=0

log "DESPLIEGUE COMPLETADO CORRECTAMENTE"
echo "Servicio: $SERVICE"
echo "Backend: $APP_DIR"
echo "Respaldo: $BACKUP_DIR"
echo "Nginx: sin modificaciones"
echo "MariaDB: sin modificaciones"
