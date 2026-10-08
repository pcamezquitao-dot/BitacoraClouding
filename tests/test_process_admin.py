from decimal import Decimal

import pytest
from pydantic import ValidationError

from app.schemas.process_admin import ProcessWrite
from app.services.process_admin_service import process_fingerprint


def valid(**overrides):
    values = {"id_proceso": 900001, "nombre": "  Proceso prueba  "}
    values.update(overrides)
    return ProcessWrite(**values)


def test_process_write_normalizes_text_and_defaults():
    item = valid(descripcion="  ", nombre_corto=" REF-1 ")
    assert item.nombre == "Proceso prueba"
    assert item.descripcion is None
    assert item.nombre_corto == "REF-1"
    assert item.tiempo_estimado == 0
    assert item.costo_estimado == Decimal("0.00")
    assert item.tipo_proceso == 1


@pytest.mark.parametrize("field,value", [("id_proceso", -1), ("nombre", "   "), ("tiempo_estimado", -1), ("costo_estimado", -1)])
def test_invalid_values_are_rejected(field, value):
    with pytest.raises(ValidationError):
        valid(**{field: value})


def test_contract_lengths_are_enforced():
    with pytest.raises(ValidationError):
        valid(nombre="x" * 51)
    with pytest.raises(ValidationError):
        valid(nombre_corto="x" * 101)
    with pytest.raises(ValidationError):
        valid(descripcion="x" * 251)


def test_only_process_type_one_is_allowed():
    assert valid(tipo_proceso=1).tipo_proceso == 1
    with pytest.raises(ValidationError):
        valid(tipo_proceso=2)


def test_fingerprint_changes_when_a_value_changes():
    row = valid().model_dump()
    assert process_fingerprint(row) != process_fingerprint({**row, "nombre": "Otro"})
