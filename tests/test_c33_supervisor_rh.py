from datetime import date
from pathlib import Path

import pytest

from app.schemas.bpm_motor import BpmOperationIn
from app.services.bpm_motor_service import BpmError, _validate_c08_data


def operation(**values):
    return BpmOperationIn(
        operation_uuid="CP_C33_UNIT_00000000000000000001",
        operation_type="COMPLETE_TASK",
        actor_code="CP_C33_RH",
        **values,
    )


def test_c08_absence_dates_are_validated():
    days, minutes = _validate_c08_data(operation(
        novelty_type=2, novelty_area_id=10006,
        novelty_start_date=date(2026, 10, 10), novelty_end_date=date(2026, 10, 12),
    ))
    assert days == 3
    assert minutes is None


def test_c08_invalid_type_is_rejected():
    with pytest.raises(BpmError, match="tipo 1, 2 o 7"):
        _validate_c08_data(operation(
            novelty_type=99, novelty_area_id=10006,
            novelty_start_date=date(2026, 10, 10), novelty_end_date=date(2026, 10, 10),
        ))


def test_c33_contract_is_additive_and_has_required_links():
    sql = Path("migrations/20261010_c33_supervisor_rh.sql").read_text(encoding="utf-8")
    assert "bpm_supervisor_rh_actuacion" in sql
    assert "bpm_supervisor_rh_evidencia" in sql
    for column in ("operacion_uuid", "id_caso", "id_actividad", "id_bitacora", "id_novedad"):
        assert column in sql
    assert "DROP TABLE" not in sql.upper()


def test_android_uses_supervisor_session_and_configured_results():
    screen = Path(
        "android/bitacora-android/app/src/main/java/com/cactus/bitacora/feature/bpm/"
        "SupervisorRhBpmScreen.kt"
    ).read_text(encoding="utf-8")
    assert "actorCode" in screen
    assert "allowedResults" in screen
    assert 'Text("Tareas BPM - Supervisor RH"' in screen
    assert "REQUIERE_CORRECCION" in screen
    assert "Revisar y confirmar" in screen
