import pytest

from app.services.xml2er_service import Xml2ErError, parse_xml
from tests.test_xml2er import VALID


def changed(old: bytes, new: bytes) -> bytes:
    value = VALID.replace(old, new)
    assert value != VALID
    return value


@pytest.mark.parametrize(
    ("label", "xml"),
    [
        ("nombre-actividad-101", changed(b"CP-C31 Registrar", b"A" * 101)),
        ("nombre-nodo-51", changed(b"CP_C31_ROOT", b"N" * 51)),
        ("nombre-corto-101", changed(b"C31_ROOT", b"C" * 101)),
        ("descripcion-nodo-251", changed(b'<nodo ref="root"', b'<nodo ref="root"><descripcion>' + b"D" * 251 + b"</descripcion>")),
        ("version-cero", changed(b'version="1"', b'version="0"')),
        ("version-sobre-uint", changed(b'version="1"', b'version="4294967296"')),
        ("id-negativo", changed(b'id_proceso="31000"', b'id_proceso="-1"')),
        ("id-sobre-int", changed(b'id_proceso="31000"', b'id_proceso="2147483648"')),
        ("tiempo-negativo", changed(b'nombre_corto="C31_ROOT"', b'nombre_corto="C31_ROOT" tiempo_estimado="-1"')),
        ("costo-3-decimales", changed(b'nombre_corto="C31_ROOT"', b'nombre_corto="C31_ROOT" costo_estimado="1.001"')),
        ("flag-fuera-rango", changed(b'activo="0"', b'activo="2"')),
        ("vacio-opcional", changed(b'nombre_corto="C31_ROOT"', b'nombre_corto=""')),
        ("espacio-opcional", changed(b'nombre_corto="C31_ROOT"', b'nombre_corto=" C31_ROOT"')),
    ],
)
def test_cp_c31_015_016_017_rejects_invalid_boundaries(label, xml):
    with pytest.raises(Xml2ErError):
        parse_xml(xml)


def test_cp_c31_017_applies_documented_defaults_and_nulls():
    definition = parse_xml(VALID)
    root = definition.nodes[0]
    assert root["descripcion"] is None
    assert root["precondicion"] is None
    assert root["tiempo_estimado"] == 0
    assert str(root["costo_estimado"]) == "0.00"
    assert root["tipo_proceso"] == 1
    assert definition.process["activo"] == 0
    assert definition.transitions[0]["codigo_regla"] == "SIEMPRE"
    assert definition.transitions[0]["activo"] == 1


def test_cp_c31_018_preserves_unicode_for_database_collation_check():
    xml = changed(b"CP-C31 Registrar", "CP-C31 Actividad Ñá中".encode("utf-8"))
    definition = parse_xml(xml)
    assert definition.activities[0]["nombre"] == "CP-C31 Actividad Ñá中"
