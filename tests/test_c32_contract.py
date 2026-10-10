from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def test_migration_is_additive_and_has_required_contracts():
    sql = (ROOT / "migrations/20261009_c32_motor_bpm.sql").read_text(encoding="utf-8")
    for table in (
        "bpm_definicion_snapshot", "bpm_etapa_config", "bpm_caso_control",
        "bpm_identidad_local", "bpm_operacion", "bpm_historial", "bpm_evidencia",
    ):
        assert f"CREATE TABLE IF NOT EXISTS {table}" in sql
    assert "DROP TABLE" not in sql.upper()
    assert "ADD COLUMN IF NOT EXISTS revision" in sql
    assert "ENUM('CONFIRMADA','CONFLICTO','RECHAZADA')" in sql


def test_server_separates_sync_and_functional_states():
    migration = (ROOT / "migrations/20261009_c32_motor_bpm.sql").read_text(encoding="utf-8")
    service = (ROOT / "app/services/bpm_motor_service.py").read_text(encoding="utf-8")
    assert "bpm_operacion" in migration
    assert "estado ENUM('CONFIRMADA','CONFLICTO','RECHAZADA')" in migration
    assert "PENDIENTE','EN_EJECUCION','COMPLETADA','CANCELADA" not in migration.split("CREATE TABLE IF NOT EXISTS bpm_operacion", 1)[1]
    assert "_existing_operation" in service
    assert "solicitud_sha256" in service


def test_no_arbitrary_rule_execution_or_external_api_offline():
    service = (ROOT / "app/services/bpm_motor_service.py").read_text(encoding="utf-8")
    assert "UNSUPPORTED_RULE_MARKERS" in service
    assert not re.search(r"\beval\s*\(", service)
    assert not re.search(r"\bexec\s*\(", service)
    assert "La definición requiere una regla o conector no soportado" in service


def test_causal_uuid_mapping_and_evidence_integrity_are_present():
    migration = (ROOT / "migrations/20261009_c32_motor_bpm.sql").read_text(encoding="utf-8")
    service = (ROOT / "app/services/bpm_motor_service.py").read_text(encoding="utf-8")
    assert "bpm_identidad_local" in migration
    assert "UNIQUE KEY uq_bpm_evidencia_uuid" in migration
    assert "hashlib.sha256(content).hexdigest()" in service
    assert "_resolve_local_id" in service and "_map_local_id" in service
    assert "dependencia_uuid" in migration
    assert "dependency_uuid" in service
    assert "La dependencia causal no está confirmada" in service


def test_android_queue_is_durable_causal_and_supports_multiple_files():
    entities = (ROOT / "android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/local/BpmLocalEntities.kt").read_text(encoding="utf-8")
    repository = (ROOT / "android/bitacora-android/app/src/main/java/com/cactus/bitacora/feature/bpm/BpmRepository.kt").read_text(encoding="utf-8")
    screen = (ROOT / "android/bitacora-android/app/src/main/java/com/cactus/bitacora/feature/bpm/BpmMotorScreen.kt").read_text(encoding="utf-8")
    assert "bpm_operations_local" in entities and "dependencyUuid" in entities
    assert "dependencyDisposition" in repository
    assert "supports.map" in repository and "sha256(support.bytes)" in repository
    assert "GetMultipleContents" in screen
