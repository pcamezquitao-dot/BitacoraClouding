import pytest
from pydantic import ValidationError

from app.schemas.activity_catalog import ActivityCatalogIn


def test_name_is_trimmed():
    assert ActivityCatalogIn(nombre="  Revisar novedad  ").nombre == "Revisar novedad"


@pytest.mark.parametrize("value", ["", "   "])
def test_empty_name_is_rejected(value):
    with pytest.raises(ValidationError):
        ActivityCatalogIn(nombre=value)


def test_name_over_100_characters_is_rejected():
    with pytest.raises(ValidationError):
        ActivityCatalogIn(nombre="x" * 101)


def test_empty_description_becomes_null():
    assert ActivityCatalogIn(nombre="Actividad", descripcion="  ").descripcion is None


def test_text_capacity_is_checked_in_utf8_bytes():
    with pytest.raises(ValidationError):
        ActivityCatalogIn(nombre="Actividad", descripcion="á" * 40000)
