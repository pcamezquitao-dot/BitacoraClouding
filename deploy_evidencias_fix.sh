#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="/srv/bitacora"
SOURCE_DIR="${1:?Falta SOURCE_DIR}"
SERVICE="bitacora-api.service"
BACKUP="/root/backups/evidencias_fix_$(date +%Y%m%d_%H%M%S)"
FILES=(app/routers/bitacora_area_evidencia.py app/services/evidencia_file_service.py)
INSTALLED=0

rollback() {
  local code=$?
  trap - ERR
  set +e
  if [[ "$INSTALLED" -eq 1 ]]; then
    for file in "${FILES[@]}"; do
      install -D -m 0644 "$BACKUP/$file" "$APP_DIR/$file"
    done
    systemctl restart "$SERVICE"
  fi
  printf 'BACKEND=REVERTIDO\nFASTAPI=%s\nRESPALDO=%s\n' \
    "$(systemctl is-active "$SERVICE" 2>/dev/null)" "$BACKUP" > /root/deploy_evidencias_fix_resultado.txt
  exit "$code"
}
trap rollback ERR

PYTHON="$APP_DIR/venv/bin/python"
[[ -x "$PYTHON" && -f "$APP_DIR/app/main.py" ]]
for file in "${FILES[@]}"; do [[ -f "$SOURCE_DIR/$file" ]]; done
"$PYTHON" -m py_compile "${FILES[@]/#/$SOURCE_DIR/}"

for file in "${FILES[@]}"; do
  mkdir -p "$BACKUP/$(dirname "$file")"
  cp -a "$APP_DIR/$file" "$BACKUP/$file"
  install -D -m 0644 "$SOURCE_DIR/$file" "$APP_DIR/$file"
done
INSTALLED=1

cd "$APP_DIR"
"$PYTHON" -c "from app.routers.face_templates import router; print('IMPORT OK')"
"$PYTHON" -c "from app.main import app; print('MAIN OK')"
systemctl restart "$SERVICE"
for _ in {1..20}; do
  if systemctl is-active --quiet "$SERVICE" && curl -fsS http://127.0.0.1:8001/health >/dev/null; then break; fi
  sleep 1
done
systemctl is-active --quiet "$SERVICE"
curl -fsS http://127.0.0.1:8001/health >/dev/null
"$PYTHON" - <<'PY'
from app.main import app
paths=app.openapi()['paths']
assert '/bitacora-area-evidencias/upload' in paths
assert '/face-templates' in paths
assert '/health' in paths
PY
printf 'BACKEND=ACTUALIZADO\nFASTAPI=active\nRESPALDO=%s\n' "$BACKUP" \
  > /root/deploy_evidencias_fix_resultado.txt
trap - ERR
