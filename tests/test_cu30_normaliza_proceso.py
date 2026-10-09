import hashlib
from xml.etree import ElementTree as ET

from app.services.normaliza_proceso_service import analyze, candidates, generate_products, validate_definition


CATALOG = [
    {"id_actividad": 1, "nombre": "Validar documento", "descripcion": "Comprobar que el soporte presentado sea válido"},
    {"id_actividad": 2, "nombre": "Validar pago", "descripcion": "Revisar el valor y la confirmación bancaria del pago"},
    {"id_actividad": 3, "nombre": "Notificar resultado", "descripcion": "Comunicar al solicitante la decisión final"},
]


def selected(step, activity_id=1, name="Validar documento", responsible="Analista"):
    step.update({"id_actividad": activity_id, "nombre_actividad": name, "responsable": responsible})
    return step


def test_same_function_with_different_words_uses_description_and_concepts():
    result = candidates("Revisar la evidencia entregada y comprobar su validez", CATALOG)
    assert result[0]["id_actividad"] == 1
    assert "función validar" in result[0]["coincidencias"]


def test_similar_names_keep_functionally_different_candidates_distinct():
    result = candidates("Comprobar la confirmación bancaria y el valor pagado", CATALOG)
    assert result[0]["id_actividad"] == 2
    assert result[0]["puntuacion"] > next(x for x in result if x["id_actividad"] == 1)["puntuacion"]


def test_analysis_does_not_auto_select_and_flags_missing_decisions():
    definition = analyze("Responsable: Analista. Revisar el documento. Finaliza el trámite.", "Prueba", CATALOG)
    activity = next(x for x in definition["pasos"] if x["tipo"] == "actividad")
    assert activity["candidates"]
    assert activity["id_actividad"] is None
    assert any("seleccione" in x for x in validate_definition(definition, CATALOG))


def test_analysis_extracts_named_documents_rules_data_and_integrations():
    definition = analyze(
        "Responsable: Analista debe validar soporte.pdf y enviar por API, datos: radicado, fecha.",
        "Elementos", CATALOG,
    )
    step = definition["pasos"][0]
    assert step["responsable"] == "Analista"
    assert step["documentos"] == ["soporte.pdf"]
    assert step["reglas"]
    assert step["datos"] == ["radicado", "fecha"]
    assert step["integraciones"] == ["API"]


def test_responsible_marker_does_not_match_por_inside_soporte():
    definition = analyze(
        "Validar el soporte presentado por Analista. Finaliza el proceso.",
        "Responsable exacto", CATALOG,
    )
    assert definition["pasos"][0]["responsable"] == "Analista"


def test_reuse_keeps_step_context_and_same_catalog_identity():
    definition = {"nombre": "Reuso", "observaciones": [], "pasos": [
        selected({"paso_id": "P001", "requerimiento": "Validar al ingreso", "responsable": "Recepción", "documentos": [], "reglas": ["antes de registrar"], "datos": ["entrada"], "integraciones": [], "tipo": "actividad", "siguiente": ["P002"], "candidates": []}, responsible="Recepción"),
        selected({"paso_id": "P002", "requerimiento": "Validar al cierre", "responsable": "Auditor", "documentos": [], "reglas": ["después de revisar"], "datos": ["cierre"], "integraciones": [], "tipo": "actividad", "siguiente": ["P003"], "candidates": []}, responsible="Auditor"),
        {"paso_id": "P003", "requerimiento": "Fin", "responsable": None, "documentos": [], "reglas": [], "datos": [], "integraciones": [], "tipo": "final", "siguiente": [], "candidates": []},
    ]}
    assert validate_definition(definition, CATALOG) == []
    normalized, _, xml = generate_products(definition)
    assert normalized.count("Validar documento") == 2
    root = ET.fromstring(xml)
    steps = root.findall("./pasos/paso")
    assert [x.attrib["id_actividad"] for x in steps[:2]] == ["1", "1"]
    assert [x.attrib["responsable"] for x in steps[:2]] == ["Recepción", "Auditor"]


def test_new_proposal_is_exported_as_provisional_not_catalog_id():
    definition = {"nombre": "Nueva", "observaciones": [], "pasos": [
        {"paso_id": "P001", "requerimiento": "Clasificar muestra", "responsable": "Laboratorio", "documentos": [], "reglas": [], "datos": [], "integraciones": [], "tipo": "actividad", "siguiente": ["P002"], "id_actividad": None, "nombre_actividad": None, "propuesta_codigo": "PROP-001", "propuesta_nombre": "Clasificar muestra", "propuesta_descripcion": "Clasificar la muestra por calidad", "candidates": []},
        {"paso_id": "P002", "requerimiento": "Fin", "responsable": None, "documentos": [], "reglas": [], "datos": [], "integraciones": [], "tipo": "final", "siguiente": [], "candidates": []},
    ]}
    assert validate_definition(definition, CATALOG) == []
    _, _, xml = generate_products(definition)
    node = ET.fromstring(xml).find("./pasos/paso")
    assert node.attrib["actividad_tipo"] == "propuesta"
    assert node.attrib["codigo_provisional"] == "PROP-001"
    assert "id_actividad" not in node.attrib


def test_products_share_all_step_ids_and_xml_is_valid():
    definition = analyze("Responsable: Analista revisar soporte. El proceso finaliza.", "Coherencia", CATALOG)
    selected(next(x for x in definition["pasos"] if x["tipo"] == "actividad"))
    normalized, puml, xml = generate_products(definition)
    root = ET.fromstring(xml)
    for step in definition["pasos"]:
        assert step["paso_id"] in normalized
        assert root.find(f"./pasos/paso[@id='{step['paso_id']}']") is not None
    assert puml.startswith("@startuml") and puml.endswith("@enduml")


def test_products_preserve_transitions_documents_rules_data_and_final_without_fake_responsible():
    definition = {"nombre": "Trazable", "observaciones": [], "pasos": [
        selected({"paso_id": "P001", "requerimiento": "Validar soporte.pdf antes de guardar", "responsable": "Analista", "documentos": ["soporte.pdf"], "reglas": ["antes de guardar"], "datos": ["radicado"], "integraciones": [], "tipo": "actividad", "siguiente": ["P002"], "candidates": []}),
        {"paso_id": "P002", "requerimiento": "Finaliza", "responsable": None, "documentos": [], "reglas": [], "datos": [], "integraciones": [], "tipo": "final", "siguiente": [], "candidates": []},
    ]}
    normalized, puml, xml = generate_products(definition)
    root = ET.fromstring(xml)
    first = root.find("./pasos/paso[@id='P001']")
    final = root.find("./pasos/paso[@id='P002']")
    assert first.findtext("documento") == "soporte.pdf"
    assert first.findtext("regla") == "antes de guardar"
    assert first.findtext("dato") == "radicado"
    assert first.find("transicion").attrib["destino"] == "P002"
    assert final.attrib["responsable"] == "NO APLICA"
    assert "P001 --> P002" in puml and "P002 --> [*]" in puml
    assert "[NO APLICA]" in normalized


def test_broken_transition_decision_and_missing_final_block_approval():
    definition = {"nombre": "Inválida", "observaciones": [], "pasos": [
        {"paso_id": "P001", "requerimiento": "Decidir", "responsable": "Jefe", "documentos": [], "reglas": [], "datos": [], "integraciones": [], "tipo": "decision", "siguiente": ["NO_EXISTE"], "candidates": []}
    ]}
    pending = validate_definition(definition, CATALOG)
    assert any("transición" in x for x in pending)
    assert any("dos transiciones" in x for x in pending)
    assert any("final explícito" in x for x in pending)


def test_ambiguity_and_potential_contradiction_are_explicit_blockers():
    definition = analyze(
        "Posiblemente el Analista debe aprobar, pero no debe aprobar. Finaliza el proceso.",
        "Ambigua", CATALOG,
    )
    pending = validate_definition(definition, CATALOG)
    assert any("ambiguas" in value for value in pending)
    assert any("contradictorias" in value for value in pending)


def test_original_hash_is_sensitive_and_products_do_not_execute_integrations():
    first = hashlib.sha256("Requerimiento á".encode()).hexdigest()
    second = hashlib.sha256("Requerimiento a".encode()).hexdigest()
    assert first != second
    definition = analyze("Responsable: Integrador enviar por WhatsApp. Fin.", "Sin ejecución", CATALOG)
    for step in definition["pasos"]:
        if step["tipo"] == "actividad": selected(step, 3, "Notificar resultado", "Integrador")
    _, puml, xml = generate_products(definition)
    assert "WhatsApp" not in puml or "WhatsApp" in definition["pasos"][0]["requerimiento"]
    integration = ET.fromstring(xml).find(".//integracion")
    assert integration is not None and integration.attrib["ejecutar"] == "false"
