#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE="bitacora-api.service"
APP_DIR="/srv/bitacora"
SOURCE_DIR="${1:?Falta SOURCE_DIR}"
BASE_URL="http://127.0.0.1:8001"
REPORT="/root/deploy_susy7_resultado.txt"
BACKUP_DIR="/root/backups/bitacora_susy7_$(date +%Y%m%d_%H%M%S)"
INSTALLED=0

FILES=(
  app/main.py
  app/routers/catalogos.py
  app/routers/participante.py
  app/routers/face_templates.py
  app/routers/empleado_area.py
  app/schemas/catalogos.py
  app/schemas/face_template.py
  app/schemas/participante.py
  app/schemas/empleado_area.py
  app/services/face_template_service.py
  app/services/empleado_area_service.py
  app/core/face_auth.py
  app/core/config.py
)

report_failure() {
  printf 'BACKEND=REVERTIDO\nFASTAPI=FALLO\nMENSAJE=%s\n' "$1" > "$REPORT"
}

rollback() {
  local code=$?
  local line="${BASH_LINENO[0]:-desconocida}"
  trap - ERR
  set +e
  if [[ "$INSTALLED" -eq 1 ]]; then
    while IFS= read -r file; do
      if [[ -f "$BACKUP_DIR/$file" ]]; then
        install -D -m 0644 "$BACKUP_DIR/$file" "$APP_DIR/$file"
      else
        rm -f "$APP_DIR/$file"
      fi
    done < "$BACKUP_DIR/manifest.txt"
    cp -a "$BACKUP_DIR/.env" "$APP_DIR/.env"
    cd "$APP_DIR"
    systemctl restart "$SERVICE"
  fi
  report_failure "Fallo en linea $line; reversión ejecutada (codigo $code)"
  exit "$code"
}
trap rollback ERR

[[ "$(id -u)" -eq 0 ]]
[[ -d "$APP_DIR" && -f "$APP_DIR/app/main.py" ]]
[[ -x "$APP_DIR/venv/bin/python" ]]
PYTHON="$APP_DIR/venv/bin/python"

for file in "${FILES[@]}"; do
  [[ -f "$SOURCE_DIR/$file" ]]
done
"$PYTHON" -m py_compile "${FILES[@]/#/$SOURCE_DIR/}"

mkdir -p "$BACKUP_DIR"
printf '%s\n' "${FILES[@]}" > "$BACKUP_DIR/manifest.txt"
cp -a "$APP_DIR/.env" "$BACKUP_DIR/.env"
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
"$PYTHON" <<'PY'
import base64, secrets
from pathlib import Path

path = Path('.env')
lines = path.read_text(encoding='utf-8').splitlines()
values = {}
for line in lines:
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip()

required = {
    'FACE_TEMPLATE_TABLE': 'participante_face_template',
    'FACE_TEMPLATE_API_TOKEN': secrets.token_urlsafe(32),
    'FACE_TEMPLATE_MASTER_KEY': base64.b64encode(secrets.token_bytes(32)).decode('ascii'),
}
for key, generated in required.items():
    if values.get(key):
        continue
    replaced = False
    for index, line in enumerate(lines):
        if line.startswith(key + '='):
            lines[index] = key + '=' + generated
            replaced = True
            break
    if not replaced:
        lines.append(key + '=' + generated)
path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
PY

"$PYTHON" -m py_compile "${FILES[@]}"
"$PYTHON" -c 'from app.main import app; assert app.openapi()["paths"]'
systemctl restart "$SERVICE"
for _ in {1..20}; do
  systemctl is-active --quiet "$SERVICE" && curl -fsS --max-time 5 "$BASE_URL/health" >/dev/null && break
  sleep 1
done
systemctl is-active --quiet "$SERVICE"
curl -fsS --max-time 10 "$BASE_URL/health" >/dev/null

VALIDATION="$($PYTHON <<'PY'
import base64, hashlib, json, struct, urllib.error, urllib.parse, urllib.request
from sqlalchemy import text
from app.core.config import settings
from app.core.db import engine

BASE='http://127.0.0.1:8001'
TOKEN=settings.FACE_TEMPLATE_API_TOKEN

def request(path, method='GET', payload=None, auth=False):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {'Content-Type':'application/json'}
    if auth:
        headers['Authorization']='Bearer '+TOKEN
    req=urllib.request.Request(BASE+path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as response:
        body=response.read()
        return response.status, json.loads(body) if body else None

status, health=request('/health')
assert status == 200
status, catalogs=request('/catalogos/offline')
assert status == 200

with engine.connect() as db:
    expected_participants=db.execute(text('SELECT COUNT(*) FROM participante')).scalar_one()
    expected_areas=db.execute(text('SELECT COUNT(*) FROM areas_administrativas')).scalar_one()
    expected_assignments=db.execute(text("SELECT COUNT(*) FROM empleado_area WHERE fecha_final IS NULL OR fecha_final >= CURDATE()" )).scalar_one()
    employee=db.execute(text("""
        SELECT ea.id_participante, p.identificacion_participante
        FROM empleado_area ea JOIN participante p ON p.id_participante=ea.id_participante
        WHERE (ea.fecha_final IS NULL OR ea.fecha_final >= CURDATE())
          AND (ea.cargo IS NULL OR ea.cargo NOT IN (3,4))
        ORDER BY ea.id_participante LIMIT 1
    """)).mappings().one()
    supervisor=db.execute(text("""
        SELECT ea.id_participante, p.identificacion_participante
        FROM empleado_area ea JOIN participante p ON p.id_participante=ea.id_participante
        WHERE (ea.fecha_final IS NULL OR ea.fecha_final >= CURDATE()) AND ea.cargo=3
        ORDER BY ea.id_participante LIMIT 1
    """)).mappings().one()
    before=db.execute(text('SELECT COUNT(*) FROM participante_face_template')).scalar_one()

assert len(catalogs['participantes']) == expected_participants
assert len(catalogs['areas']) == expected_areas
assert len(catalogs['empleado_areas']) == expected_assignments

for role, row in [('empleado', employee), ('supervisor', supervisor)]:
    code=urllib.parse.quote(row['identificacion_participante'])
    assert request('/participante/by_qr/'+code)[0] == 200
    assert request('/participante/search?q='+code)[1]
    assignments=request(f"/empleado-area/{row['id_participante']}/activas")[1]
    if role == 'supervisor':
        assert any(item.get('cargo') == 3 for item in assignments)
    else:
        assert any(item.get('cargo') not in (3,4) for item in assignments)

raw=struct.pack('>128f', *([0.125]*128))
payload={
    'client_uuid':'susy7-server-validation',
    'id_participante':employee['id_participante'],
    'participant_code':employee['identificacion_participante'],
    'display_name':'Prueba SUSY7',
    'embedding_base64':base64.b64encode(raw).decode(),
    'embedding_sha256':hashlib.sha256(raw).hexdigest(),
    'model_version':'FaceNet-160/128',
    'device_id':'susy7-validation'
}
first=request('/face-templates', 'POST', payload, True)[1]
retry=request('/face-templates/enroll', 'POST', payload, True)[1]
assert first['id_face_template'] == retry['id_face_template']
assert request(f"/face-templates/by-participante/{employee['id_participante']}", auth=True)[0] == 200
assert request('/face-templates/sync', auth=True)[0] == 200
assert request('/face-templates/authorized/active', auth=True)[0] == 200

with engine.connect() as db:
    after=db.execute(text('SELECT COUNT(*) FROM participante_face_template')).scalar_one()
assert after == before + 1

bitacoras=request('/bitacora_diaria?offset=0&limit=200')[1]
assert len(bitacoras) == 64
assert any(row['id_bitacora'] == 68 for row in bitacoras)
evidences=request('/bitacora-area-evidencias?id_bitacora=68&offset=0&limit=200')[1]
assert len(evidences) == 2

print(json.dumps({
    'participants':len(catalogs['participantes']),
    'areas':len(catalogs['areas']),
    'assignments':len(catalogs['empleado_areas']),
    'employee':employee['id_participante'],
    'supervisor':supervisor['id_participante'],
    'face_rows_created':after-before,
    'bitacoras':len(bitacoras),
    'evidences68':len(evidences)
}))
PY
)"

printf 'BACKEND=ACTUALIZADO\nFASTAPI=ACTIVO\nRESPALDO=%s\nVALIDACION=%s\n' \
  "$BACKUP_DIR" "$VALIDATION" > "$REPORT"
trap - ERR
