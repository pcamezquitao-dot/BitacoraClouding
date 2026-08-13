from datetime import date

import pytest
from fastapi import HTTPException
from unittest.mock import MagicMock

from app.main import app
from app.routers.participants_admin import participant_admin_identity, router
from app.schemas.participante import ParticipantAdminIn
from app.services.participant_admin_service import _clean, list_participants


def valid_payload(**changes):
    values = dict(
        tipo_documento=1,
        documento="900001",
        identificacion_participante=" test-001 ",
        nombre=" Patricia ",
        apellido=" Prueba ",
        fecha_nacimiento=date(1990, 1, 1),
        sexo="f",
        fecha_entrada=date(2026, 1, 1),
    )
    values.update(changes)
    return ParticipantAdminIn(**values)


def test_participant_admin_routes_registered_without_legacy_token():
    paths = app.openapi()["paths"]
    assert "/admin/participantes" in paths
    assert "/admin/participantes/{participant_id}" in paths
    assert "/admin/participantes/tipos-documento" in paths
    assert router.dependencies == []


def test_write_requires_identified_administrator():
    with pytest.raises(HTTPException) as raised:
        participant_admin_identity("  ", None)
    assert raised.value.status_code == 422


def test_normalizes_required_fields_and_sex():
    values = _clean(valid_payload())
    assert values["identificacion_participante"] == "TEST-001"
    assert values["nombre"] == "Patricia"
    assert values["sexo"] == "F"


def test_rejects_invalid_dates_and_sex():
    with pytest.raises(ValueError):
        _clean(valid_payload(sexo="X"))
    with pytest.raises(ValueError):
        _clean(valid_payload(fecha_salida=date(2025, 12, 31)))


def test_nullable_database_fields_become_null():
    values = _clean(valid_payload(
        fecha_entrada=None,
        fecha_salida=None,
        observaciones="   ",
    ))
    assert values["fecha_entrada"] is None
    assert values["fecha_salida"] is None
    assert values["observaciones"] is None


def test_mariadb_required_fields_remain_required():
    for field in ("apellido", "fecha_nacimiento", "sexo"):
        with pytest.raises((ValueError, TypeError)):
            valid_payload(**{field: None})


def test_participant_list_is_ordered_by_id_participante():
    db = MagicMock()
    count_result = MagicMock()
    count_result.scalar_one.return_value = 0
    rows_result = MagicMock()
    rows_result.mappings.return_value.all.return_value = []
    db.execute.side_effect = [count_result, rows_result]

    list_participants(db, "", 0, 100)

    query = str(db.execute.call_args_list[1].args[0])
    assert "ORDER BY id_participante" in query
