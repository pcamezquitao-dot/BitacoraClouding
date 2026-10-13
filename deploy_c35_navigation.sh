#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_DIR="${1:?Falta SOURCE_DIR}"
APP_DIR=/srv/bitacora
SERVICE=bitacora-api.service
BACKUP_DIR="/srv/backups/c35-$(date +%Y%m%d_%H%M%S)"
FILES=(
  app/main.py
  app/core/config.py
  app/core/admin_auth.py
  app/routers/admin_access.py
  app/routers/navigation_admin.py
  app/schemas/navigation_admin.py
  app/services/navigation_admin_service.py
)
installed=0

rollback() {
  code=$?
  trap - ERR
  set +e
  if [[ "$installed" == 1 ]]; then
    while IFS= read -r file; do
      if [[ -f "$BACKUP_DIR/$file" ]]; then
        install -D -m 0644 "$BACKUP_DIR/$file" "$APP_DIR/$file"
      else
        rm -f "$APP_DIR/$file"
      fi
    done < "$BACKUP_DIR/manifest.txt"
    cp -a "$BACKUP_DIR/.env" "$APP_DIR/.env"
    systemctl restart "$SERVICE"
  fi
  echo "DEPLOYMENT=ROLLED_BACK"
  exit "$code"
}
trap rollback ERR

[[ -n "${ADMIN_ACCESS_KEY_VALUE:-}" ]]
mkdir -p "$BACKUP_DIR"
printf '%s\n' "${FILES[@]}" > "$BACKUP_DIR/manifest.txt"
cp -a "$APP_DIR/.env" "$BACKUP_DIR/.env"
for file in "${FILES[@]}"; do
  [[ -f "$SOURCE_DIR/$file" ]]
  if [[ -f "$APP_DIR/$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp -a "$APP_DIR/$file" "$BACKUP_DIR/$file"
  fi
done

for file in "${FILES[@]}"; do install -D -m 0644 "$SOURCE_DIR/$file" "$APP_DIR/$file"; done
installed=1

cd "$APP_DIR"
ADMIN_ACCESS_KEY_VALUE="$ADMIN_ACCESS_KEY_VALUE" venv/bin/python - <<'PY'
import os
from pathlib import Path
path=Path('.env')
lines=path.read_text(encoding='utf-8').splitlines()
key=os.environ['ADMIN_ACCESS_KEY_VALUE']
updated=False
for index,line in enumerate(lines):
    if line.startswith('ADMIN_ACCESS_KEY='):
        lines[index]='ADMIN_ACCESS_KEY='+key
        updated=True
        break
if not updated:
    lines.append('ADMIN_ACCESS_KEY='+key)
path.write_text('\n'.join(lines)+'\n',encoding='utf-8')
PY

venv/bin/python -m py_compile "${FILES[@]}"
venv/bin/python -c 'from app.main import app; assert "/admin/navegacion" in app.openapi()["paths"]'
systemctl restart "$SERVICE"
for _ in {1..20}; do
  systemctl is-active --quiet "$SERVICE" && curl -fsS --max-time 5 http://127.0.0.1:8001/health >/dev/null && break
  sleep 1
done
systemctl is-active --quiet "$SERVICE"
curl -fsS --max-time 10 http://127.0.0.1:8001/health >/dev/null
venv/bin/python - <<'PY'
import urllib.error, urllib.request
from app.core.config import settings
base='http://127.0.0.1:8001/admin/access/verify'
common={'Authorization':'Bearer '+settings.ADMIN_API_TOKEN,'X-Admin-Actor':'DEPLOY-C35','X-Admin-Device':'SERVER'}
def status(headers):
    try:
        with urllib.request.urlopen(urllib.request.Request(base,headers=headers),timeout=10) as response: return response.status
    except urllib.error.HTTPError as error: return error.code
assert status({**common,'X-Admin-Key':settings.ADMIN_ACCESS_KEY}) == 200
assert status({**common,'X-Admin-Key':'incorrecta'}) == 401
assert status(common) == 401
PY

echo "DEPLOYMENT=OK"
echo "BACKUP=$BACKUP_DIR"
trap - ERR
