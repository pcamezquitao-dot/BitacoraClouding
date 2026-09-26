#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_DIR="${1:?Falta SOURCE_DIR}"
BACKUP_DIR="${2:?Falta BACKUP_DIR}"
MIGRATION="$SOURCE_DIR/migrations/20260925_c09_transcription_contract.sql"
ROLLBACK="$SOURCE_DIR/migrations/20260925_c09_transcription_contract_rollback.sql"
MIGRATION_STARTED=0

rollback() {
  local code=$?
  trap - ERR
  set +e
  if [[ "$MIGRATION_STARTED" -eq 1 ]]; then
    mariadb bitacora < "$BACKUP_DIR/evidencia_transcripcion_before.sql"
  fi
  printf 'RESULTADO=RESTAURADO_DESDE_RESPALDO\nBACKUP=%s\n' "$BACKUP_DIR" \
    > /root/deploy_c09_transcription_resultado.txt
  exit "$code"
}
trap rollback ERR

[[ -f "$MIGRATION" && -f "$ROLLBACK" ]]
[[ -f "$BACKUP_DIR/show_create_before.txt" ]]
[[ -f "$BACKUP_DIR/evidencia_transcripcion_before.sql.gz" ]]
cd "$BACKUP_DIR"
sha256sum -c SHA256SUMS
gzip -t evidencia_transcripcion_before.sql.gz
[[ "$(mariadb bitacora -N -e 'SELECT COUNT(*) FROM evidencia_transcripcion')" == "0" ]]

cp -a "$MIGRATION" "$BACKUP_DIR/"
cp -a "$ROLLBACK" "$BACKUP_DIR/"
MIGRATION_STARTED=1
mariadb bitacora < "$MIGRATION"

[[ "$(mariadb bitacora -N -e 'SELECT COUNT(*) FROM evidencia_transcripcion')" == "0" ]]
[[ "$(mariadb bitacora -N -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='bitacora' AND TABLE_NAME='evidencia_transcripcion' AND COLUMN_NAME='id_evidencia'")" == "1" ]]
[[ "$(mariadb bitacora -N -e "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='bitacora' AND TABLE_NAME='evidencia_transcripcion' AND COLUMN_NAME='texto_transcrito'")" == "YES" ]]
mariadb bitacora -e 'SHOW CREATE TABLE evidencia_transcripcion\G' \
  > "$BACKUP_DIR/show_create_after.txt"

status="$(curl -sS -o "$BACKUP_DIR/endpoint_after.json" -w '%{http_code}' \
  http://127.0.0.1:8001/bitacora-area-evidencias/999999/transcripcion)"
[[ "$status" == "404" ]]

sha256sum "$BACKUP_DIR/show_create_after.txt" "$BACKUP_DIR/endpoint_after.json" \
  > "$BACKUP_DIR/SHA256_AFTER"
printf 'RESULTADO=MIGRADO\nFILAS=0\nENDPOINT_HTTP=%s\nBACKUP=%s\n' \
  "$status" "$BACKUP_DIR" > /root/deploy_c09_transcription_resultado.txt
trap - ERR
