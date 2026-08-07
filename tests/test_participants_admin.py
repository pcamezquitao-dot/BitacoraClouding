from datetime import date

import pytest
from fastapi import HTTPException

from app.main import app
from app.routers.participants_admin import participant_admin_identity, router
from app.schemas.participante import ParticipantAdminIn
from app.services.participant_admin_service import _clean


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
