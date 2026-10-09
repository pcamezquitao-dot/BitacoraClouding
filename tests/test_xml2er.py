from pathlib import Path

import pytest

from app.services.xml2er_service import Xml2ErError, parse_xml


VALID = b'''<?xml version="1.0" encoding="UTF-8"?>
<definicion_proceso contrato_version="1">
 <actividades><actividad ref="a1" nombre="CP-C31 Registrar"><descripcion>Prueba</descripcion></actividad></actividades>
 <nodos>
  <nodo ref="root" id_proceso="31000" nombre="CP_C31_ROOT" nombre_corto="C31_ROOT"/>
  <nodo ref="work" id_proceso="31001" nombre="CP_C31_WORK" nombre_corto="C31_WORK" padre_ref="root" actividad_ref="a1"/>
  <nodo ref="end" id_proceso="31002" nombre="CP_C31_END" nombre_corto="C31_END" padre_ref="root"/>
 </nodos>
 <proceso nombre="CP_C31" version="1" activo="0"><etapas>
  <etapa ref="e1" nodo_ref="work" rol_responsable="supervisor" es_inicial="1"/>
  <etapa ref="e2" nodo_ref="end" es_final="1"/>
 </etapas><transiciones><transicion ref="t1" origen_ref="e1" destino_ref="e2"/></transiciones></proceso>
</definicion_proceso>'''


def test_valid_contract_maps_stage_to_node():
    definition = parse_xml(VALID)
    assert len(definition.activities) == 1
    assert definition.stages[0]["nodo_ref"] == "work"
    assert definition.nodes[1]["id_proceso"] == 31001


@pytest.mark.parametrize("mutation", [
    lambda value: value.replace(b'<definicion_proceso', b'<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><definicion_proceso'),
    lambda value: value.replace(b'contrato_version="1"', b'contrato_version="2"'),
    lambda value: value.replace(b'origen_ref="e1"', b'origen_ref="missing"'),
    lambda value: value.replace(b'nombre="CP-C31 Registrar"', b'nombre=" CP-C31 Registrar "'),
    lambda value: value.replace(b'<nodo ref="end"', b'<nodo ref="work"'),
    lambda value: value.replace(b'<transicion ref="t1"', b'<transicion ref="t1" codigo_regla="eval(x)"'),
])
def test_invalid_xml_is_rejected(mutation):
    with pytest.raises(Xml2ErError):
        parse_xml(mutation(VALID))


def test_semantically_equivalent_order_parses():
    changed = VALID.replace(
        b'<nodo ref="work" id_proceso="31001" nombre="CP_C31_WORK" nombre_corto="C31_WORK" padre_ref="root" actividad_ref="a1"/>',
        b'<nodo actividad_ref="a1" padre_ref="root" nombre_corto="C31_WORK" nombre="CP_C31_WORK" id_proceso="31001" ref="work"/>'
    )
    assert parse_xml(changed).process == parse_xml(VALID).process


def test_size_limit_is_enforced():
    with pytest.raises(Xml2ErError, match="5242880"):
        parse_xml(b"x" * (5 * 1024 * 1024 + 1))
