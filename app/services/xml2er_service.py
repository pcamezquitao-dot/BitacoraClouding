from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import hmac
import json
import os
import re
import threading
from xml.etree import ElementTree as ET

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.config import settings


MAX_XML_BYTES = 5 * 1024 * 1024
MAX_XML_DEPTH = 64
MAX_XML_ELEMENTS = 10_000
_IMPORT_LOCK = threading.RLock()
_TOKEN_KEY = os.environ.get("XML2ER_TOKEN_KEY", settings.ADMIN_API_TOKEN).encode("utf-8")
TABLES = ("cat_actividad", "dim_proceso", "bpm_proceso", "bpm_etapa", "bpm_transicion")


class Xml2ErError(ValueError):
    pass


class Xml2ErConflict(Xml2ErError):
    pass


@dataclass(frozen=True)
class Definition:
    activities: tuple[dict, ...]
    nodes: tuple[dict, ...]
    process: dict
    stages: tuple[dict, ...]
    transitions: tuple[dict, ...]
    sha256: str


def _fail(path: str, field: str, value, rule: str):
    raise Xml2ErError(f"{path}: {field}={value!r}; {rule}")


def _required(value: str | None, path: str, field: str, limit: int) -> str:
    if value is None or not value or value != value.strip():
        _fail(path, field, value, "obligatorio, no vacío y sin espacios extremos")
    if len(value) > limit:
        _fail(path, field, value, f"máximo {limit} caracteres")
    return value


def _optional(value: str | None, path: str, field: str, limit: int) -> str | None:
    if value is None:
        return None
    if value == "" or value != value.strip():
        _fail(path, field, value, "si se informa no puede estar vacío ni tener espacios extremos")
    if len(value) > limit:
        _fail(path, field, value, f"máximo {limit} caracteres")
    return value


def _integer(value: str | None, path: str, field: str, minimum: int, maximum: int, default=None):
    if value is None and default is not None:
        return default
    try:
        parsed = int(value or "")
    except ValueError:
        _fail(path, field, value, "debe ser entero")
    if str(parsed) != value and not (value == "0" and parsed == 0):
        _fail(path, field, value, "debe ser entero decimal canónico")
    if not minimum <= parsed <= maximum:
        _fail(path, field, value, f"rango {minimum}..{maximum}")
    return parsed


def _decimal(value: str | None, path: str, field: str, default=None):
    if value is None:
        return default
    try:
        parsed = Decimal(value)
    except InvalidOperation:
        _fail(path, field, value, "debe ser decimal")
    if not parsed.is_finite() or parsed < 0 or parsed > Decimal("99999999.99") or -parsed.as_tuple().exponent > 2:
        _fail(path, field, value, "debe estar entre 0 y 99999999.99 con máximo dos decimales")
    return parsed


def _flag(value: str | None, path: str, field: str, default: int) -> int:
    parsed = default if value is None else _integer(value, path, field, 0, 1)
    return parsed


def _children(element: ET.Element, allowed: set[str], path: str):
    for child in element:
        if child.tag not in allowed:
            _fail(path, child.tag, None, "elemento desconocido")


def _attrs(element: ET.Element, allowed: set[str], path: str):
    unknown = set(element.attrib) - allowed
    if unknown:
        _fail(path, sorted(unknown)[0], element.attrib[sorted(unknown)[0]], "atributo desconocido")


def _unique(items, key, label):
    seen = set()
    for item in items:
        value = item[key]
        if value in seen:
            raise Xml2ErError(f"Referencia duplicada en {label}: {value}")
        seen.add(value)


def parse_xml(raw: bytes) -> Definition:
    if not raw or len(raw) > MAX_XML_BYTES:
        raise Xml2ErError(f"El XML debe tener entre 1 y {MAX_XML_BYTES} bytes")
    try:
        decoded = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise Xml2ErError("El XML debe estar codificado en UTF-8") from error
    lowered = decoded.lower()
    if any(marker in lowered for marker in ("<!doctype", "<!entity", "<xi:include", "xinclude")):
        raise Xml2ErError("DOCTYPE, entidades externas y XInclude no están permitidos")
    try:
        root = ET.fromstring(decoded)
    except ET.ParseError as error:
        raise Xml2ErError(f"XML mal formado: {error}") from error
    count = 0
    stack = [(root, 1)]
    while stack:
        current, depth = stack.pop(); count += 1
        if depth > MAX_XML_DEPTH:
            raise Xml2ErError(f"Profundidad XML máxima: {MAX_XML_DEPTH}")
        if count > MAX_XML_ELEMENTS:
            raise Xml2ErError(f"Cantidad máxima de elementos: {MAX_XML_ELEMENTS}")
        stack.extend((child, depth + 1) for child in current)
    if root.tag != "definicion_proceso" or root.attrib != {"contrato_version": "1"}:
        raise Xml2ErError("Se requiere definicion_proceso contrato_version=1; BPMN no se acepta directamente")
    _children(root, {"actividades", "nodos", "proceso"}, "/definicion_proceso")
    sections = {name: root.findall(name) for name in ("actividades", "nodos", "proceso")}
    if any(len(value) != 1 for value in sections.values()):
        raise Xml2ErError("Se requiere exactamente una sección actividades, nodos y proceso")
    activities = []
    for index, item in enumerate(sections["actividades"][0].findall("actividad"), 1):
        path = f"/actividades/actividad[{index}]"; _attrs(item, {"ref", "nombre"}, path); _children(item, {"descripcion"}, path)
        descriptions = item.findall("descripcion")
        if len(descriptions) > 1: _fail(path, "descripcion", None, "máximo una")
        description = descriptions[0].text if descriptions else None
        activities.append({"ref": _required(item.get("ref"), path, "ref", 100), "nombre": _required(item.get("nombre"), path, "nombre", 100), "descripcion": description})
    _unique(activities, "ref", "actividades")
    nodes = []
    for index, item in enumerate(sections["nodos"][0].findall("nodo"), 1):
        path = f"/nodos/nodo[{index}]"
        _attrs(item, {"ref", "id_proceso", "nombre", "nombre_corto", "padre_ref", "padre_id", "actividad_ref", "actividad_nombre", "tiempo_estimado", "costo_estimado", "tipo_proceso"}, path)
        _children(item, {"descripcion", "precondicion"}, path)
        if item.get("padre_ref") and item.get("padre_id"): _fail(path, "padre", None, "padre_ref y padre_id son excluyentes")
        if item.get("actividad_ref") and item.get("actividad_nombre"): _fail(path, "actividad", None, "actividad_ref y actividad_nombre son excluyentes")
        descriptions, preconditions = item.findall("descripcion"), item.findall("precondicion")
        if len(descriptions) > 1 or len(preconditions) > 1: _fail(path, "texto", None, "descripcion y precondicion aparecen máximo una vez")
        type_value = _integer(item.get("tipo_proceso"), path, "tipo_proceso", 1, 1, 1)
        nodes.append({"ref": _required(item.get("ref"), path, "ref", 100), "id_proceso": _integer(item.get("id_proceso"), path, "id_proceso", 0, 2147483647), "nombre": _required(item.get("nombre"), path, "nombre", 50), "nombre_corto": _optional(item.get("nombre_corto"), path, "nombre_corto", 100), "descripcion": _optional(descriptions[0].text if descriptions else None, path, "descripcion", 250), "precondicion": _optional(preconditions[0].text if preconditions else None, path, "precondicion", 250), "padre_ref": item.get("padre_ref"), "padre_id": _integer(item.get("padre_id"), path, "padre_id", 0, 2147483647) if item.get("padre_id") is not None else None, "actividad_ref": item.get("actividad_ref"), "actividad_nombre": item.get("actividad_nombre"), "tiempo_estimado": _integer(item.get("tiempo_estimado"), path, "tiempo_estimado", 0, 2147483647, 0), "costo_estimado": _decimal(item.get("costo_estimado"), path, "costo_estimado", Decimal("0.00")), "tipo_proceso": type_value})
    _unique(nodes, "ref", "nodos"); _unique(nodes, "id_proceso", "nodos")
    process_el = sections["proceso"][0]; path = "/proceso"
    _attrs(process_el, {"nombre", "version", "activo"}, path); _children(process_el, {"descripcion", "etapas", "transiciones"}, path)
    if len(process_el.findall("etapas")) != 1 or len(process_el.findall("transiciones")) != 1: raise Xml2ErError("El proceso requiere exactamente etapas y transiciones")
    descriptions = process_el.findall("descripcion")
    if len(descriptions) > 1: _fail(path, "descripcion", None, "máximo una")
    process = {"nombre": _required(process_el.get("nombre"), path, "nombre", 100), "version": _integer(process_el.get("version"), path, "version", 1, 4294967295), "activo": _flag(process_el.get("activo"), path, "activo", 1), "descripcion": descriptions[0].text if descriptions else None}
    stages = []
    for index, item in enumerate(process_el.find("etapas").findall("etapa"), 1):
        path = f"/proceso/etapas/etapa[{index}]"; _attrs(item, {"ref", "nodo_ref", "nodo_id", "rol_responsable", "plazo_horas", "es_inicial", "es_final"}, path); _children(item, set(), path)
        if bool(item.get("nodo_ref")) == bool(item.get("nodo_id")): _fail(path, "nodo", None, "informe exactamente uno de nodo_ref o nodo_id")
        stages.append({"ref": _required(item.get("ref"), path, "ref", 100), "nodo_ref": item.get("nodo_ref"), "nodo_id": _integer(item.get("nodo_id"), path, "nodo_id", 0, 2147483647) if item.get("nodo_id") is not None else None, "rol_responsable": _optional(item.get("rol_responsable"), path, "rol_responsable", 100), "plazo_horas": _decimal(item.get("plazo_horas"), path, "plazo_horas"), "es_inicial": _flag(item.get("es_inicial"), path, "es_inicial", 0), "es_final": _flag(item.get("es_final"), path, "es_final", 0)})
    _unique(stages, "ref", "etapas")
    transitions = []
    for index, item in enumerate(process_el.find("transiciones").findall("transicion"), 1):
        path = f"/proceso/transiciones/transicion[{index}]"; _attrs(item, {"ref", "origen_ref", "destino_ref", "codigo_regla", "resultado", "activo"}, path); _children(item, set(), path)
        rule = _required(item.get("codigo_regla") or "SIEMPRE", path, "codigo_regla", 300)
        if rule != "SIEMPRE": _fail(path, "codigo_regla", rule, "regla desconocida; contrato actual admite SIEMPRE")
        if _flag(item.get("activo"), path, "activo", 1) != 1: _fail(path, "activo", item.get("activo"), "las transiciones importadas deben estar activas")
        transitions.append({"ref": _required(item.get("ref"), path, "ref", 100), "origen_ref": _required(item.get("origen_ref"), path, "origen_ref", 100), "destino_ref": _required(item.get("destino_ref"), path, "destino_ref", 100), "codigo_regla": rule, "resultado": _optional(item.get("resultado"), path, "resultado", 100), "activo": 1})
    _unique(transitions, "ref", "transiciones")
    definition = Definition(tuple(activities), tuple(nodes), process, tuple(stages), tuple(transitions), hashlib.sha256(raw).hexdigest())
    _validate_graph(definition)
    return definition


def _validate_graph(definition: Definition):
    activity_refs = {x["ref"] for x in definition.activities}; node_refs = {x["ref"] for x in definition.nodes}; stage_refs = {x["ref"] for x in definition.stages}
    for node in definition.nodes:
        if node["padre_ref"] and node["padre_ref"] not in node_refs: raise Xml2ErError(f"{node['ref']}: padre_ref inexistente")
        if node["padre_ref"] == node["ref"]: raise Xml2ErError(f"{node['ref']}: autorreferencia")
        if node["actividad_ref"] and node["actividad_ref"] not in activity_refs: raise Xml2ErError(f"{node['ref']}: actividad_ref inexistente")
    parents = {x["ref"]: x["padre_ref"] for x in definition.nodes}
    for ref in parents:
        visited, current = set(), ref
        while current:
            if current in visited: raise Xml2ErError(f"Jerarquía cíclica en {ref}")
            visited.add(current); current = parents.get(current)
    resolved_nodes = []
    node_by_ref = {x["ref"]: x for x in definition.nodes}
    for stage in definition.stages:
        if stage["nodo_ref"] not in node_refs and stage["nodo_id"] is None: raise Xml2ErError(f"{stage['ref']}: nodo_ref inexistente")
        node = node_by_ref.get(stage["nodo_ref"]); resolved_nodes.append(node["id_proceso"] if node else stage["nodo_id"])
        if not stage["es_final"] and (not stage["rol_responsable"] or not node or not (node["actividad_ref"] or node["actividad_nombre"])): raise Xml2ErError(f"{stage['ref']}: etapa no final requiere actividad y rol_responsable")
        if stage["es_inicial"] and stage["es_final"]: raise Xml2ErError(f"{stage['ref']}: inicial y final no pueden coincidir")
    if len(resolved_nodes) != len(set(resolved_nodes)): raise Xml2ErError("Dos etapas de la versión usan el mismo nodo")
    initials = [x for x in definition.stages if x["es_inicial"]]; finals = {x["ref"] for x in definition.stages if x["es_final"]}
    if len(initials) != 1 or not finals: raise Xml2ErError("Se requiere exactamente una etapa inicial y al menos una final")
    outgoing = {ref: [] for ref in stage_refs}
    semantic = set()
    for transition in definition.transitions:
        if transition["origen_ref"] not in stage_refs or transition["destino_ref"] not in stage_refs: raise Xml2ErError(f"{transition['ref']}: extremo inexistente")
        if transition["origen_ref"] == transition["destino_ref"]: raise Xml2ErError(f"{transition['ref']}: origen y destino deben ser distintos")
        key = (transition["origen_ref"], transition["destino_ref"], transition["codigo_regla"], transition["resultado"])
        if key in semantic: raise Xml2ErError(f"{transition['ref']}: transición semántica duplicada")
        semantic.add(key); outgoing[transition["origen_ref"]].append(transition)
    for final in finals:
        if outgoing[final]: raise Xml2ErError(f"{final}: una etapa final no admite salidas")
    for origin, choices in outgoing.items():
        if len(choices) > 1 and (any(x["resultado"] is None for x in choices) or len({x["resultado"] for x in choices}) != len(choices)): raise Xml2ErError(f"{origin}: salidas no exclusivas")
    initial = initials[0]["ref"]
    reachable, pending = set(), [initial]
    while pending:
        current = pending.pop()
        if current in reachable: continue
        reachable.add(current); pending.extend(x["destino_ref"] for x in outgoing[current])
    if reachable != stage_refs: raise Xml2ErError(f"Etapas no alcanzables: {sorted(stage_refs-reachable)}")
    for start in stage_refs:
        seen, pending = set(), [start]
        while pending:
            current = pending.pop()
            if current in seen: continue
            seen.add(current); pending.extend(x["destino_ref"] for x in outgoing[current])
        if not (seen & finals): raise Xml2ErError(f"{start}: no tiene camino a cierre")


def _business_node(node, parent_id, activity_id):
    return {"id_proceso": node["id_proceso"], "nombre": node["nombre"], "descripcion": node["descripcion"], "id_proceso_padre": parent_id, "tiempo_estimado": node["tiempo_estimado"], "costo_estimado": Decimal(node["costo_estimado"]), "tipo_proceso": node["tipo_proceso"], "precondicion": node["precondicion"], "id_actividad": activity_id, "nombre_corto": node["nombre_corto"]}


def _semantic_existing(db: Session, process_id: int) -> dict:
    process = dict(db.execute(text("SELECT nombre,version,descripcion,activo FROM bpm_proceso WHERE id_bpm_proceso=:id"), {"id": process_id}).mappings().one())
    stages = [dict(x) for x in db.execute(text("SELECT id_etapa,rol_responsable,plazo_horas,es_inicial,es_final FROM bpm_etapa WHERE id_bpm_proceso=:id ORDER BY id_etapa"), {"id": process_id}).mappings()]
    transitions = [dict(x) for x in db.execute(text("SELECT id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo FROM bpm_transicion WHERE id_bpm_proceso=:id ORDER BY id_etapa_origen,id_etapa_destino,codigo_regla,COALESCE(resultado,'')"), {"id": process_id}).mappings()]
    return {"process": process, "stages": stages, "transitions": transitions}


def _token(definition: Definition) -> str:
    payload = f"{definition.sha256}:{definition.process['nombre']}:{definition.process['version']}"
    return hmac.new(_TOKEN_KEY, payload.encode(), hashlib.sha256).hexdigest()


def preview(db: Session, raw: bytes, filename: str) -> tuple[Definition, dict]:
    definition = parse_xml(raw)
    base = db.execute(text("SELECT DATABASE()")) .scalar_one()
    counts = {table: {"nuevos": 0, "reutilizados": 0, "conflictos": 0} for table in TABLES}
    conflicts, maps = [], {"actividades": {}, "nodos": {}, "etapas": {}, "transiciones": {}, "proceso": {}}
    activities_by_ref = {}
    for item in definition.activities:
        row = db.execute(text("SELECT id_actividad,nombre,descripcion FROM cat_actividad WHERE nombre=:n"), {"n": item["nombre"]}).mappings().first()
        if not row: counts["cat_actividad"]["nuevos"] += 1
        elif (row["descripcion"] or None) != (item["descripcion"] or None): conflicts.append(f"actividad {item['ref']}: descripción incompatible"); counts["cat_actividad"]["conflictos"] += 1
        else: counts["cat_actividad"]["reutilizados"] += 1; maps["actividades"][item["ref"]] = row["id_actividad"]
        activities_by_ref[item["ref"]] = row["id_actividad"] if row and (row["descripcion"] or None) == (item["descripcion"] or None) else None
    node_by_ref = {x["ref"]: x for x in definition.nodes}
    for item in definition.nodes:
        row = db.execute(text("SELECT * FROM dim_proceso WHERE id_proceso=:id"), {"id": item["id_proceso"]}).mappings().first()
        by_name = db.execute(text("SELECT id_proceso FROM dim_proceso WHERE nombre=:n OR (:c IS NOT NULL AND nombre_corto=:c)"), {"n": item["nombre"], "c": item["nombre_corto"]}).scalars().all()
        if item["padre_id"] is not None and not db.execute(text("SELECT 1 FROM dim_proceso WHERE id_proceso=:id"), {"id": item["padre_id"]}).scalar():
            conflicts.append(f"nodo {item['ref']}: padre_id inexistente {item['padre_id']}"); counts["dim_proceso"]["conflictos"] += 1
        if item["actividad_nombre"] and not db.execute(text("SELECT 1 FROM cat_actividad WHERE nombre=:n"), {"n": item["actividad_nombre"]}).scalar():
            conflicts.append(f"nodo {item['ref']}: actividad_nombre inexistente {item['actividad_nombre']}"); counts["dim_proceso"]["conflictos"] += 1
        if not row and by_name: conflicts.append(f"nodo {item['ref']}: nombre o nombre_corto ya pertenece a {by_name}"); counts["dim_proceso"]["conflictos"] += 1
        elif not row: counts["dim_proceso"]["nuevos"] += 1
        else:
            parent_id = node_by_ref[item["padre_ref"]]["id_proceso"] if item["padre_ref"] else item["padre_id"]
            activity_id = activities_by_ref.get(item["actividad_ref"]) if item["actividad_ref"] else None
            if item["actividad_nombre"]:
                activity_id = db.execute(text("SELECT id_actividad FROM cat_actividad WHERE nombre=:n"), {"n": item["actividad_nombre"]}).scalar()
            expected = _business_node(item, parent_id, activity_id)
            differences = [key for key, value in expected.items() if row[key] != value]
            if differences: conflicts.append(f"nodo {item['ref']}: campos incompatibles {differences}"); counts["dim_proceso"]["conflictos"] += 1
            else: counts["dim_proceso"]["reutilizados"] += 1; maps["nodos"][item["ref"]] = item["id_proceso"]
    existing = db.execute(text("SELECT id_bpm_proceso FROM bpm_proceso WHERE nombre=:n AND version=:v"), {"n": definition.process["nombre"], "v": definition.process["version"]}).scalar()
    valid_roles = {str(value) for value in db.execute(text("SELECT descripcion FROM tipos_participante WHERE activo=1")).scalars()}
    for stage in definition.stages:
        if stage["rol_responsable"] and stage["rol_responsable"] not in valid_roles:
            conflicts.append(f"etapa {stage['ref']}: rol_responsable no existe o está inactivo: {stage['rol_responsable']}")
            counts["bpm_etapa"]["conflictos"] += 1
    if existing: counts["bpm_proceso"]["reutilizados"] = 1; maps["proceso"]["id_bpm_proceso"] = existing
    else: counts["bpm_proceso"]["nuevos"] = 1
    counts["bpm_etapa"]["nuevos" if not existing else "reutilizados"] = len(definition.stages)
    counts["bpm_transicion"]["nuevos" if not existing else "reutilizados"] = len(definition.transitions)
    state = "CON ERRORES" if conflicts else "VALIDADO"
    return definition, {"estado": state, "archivo": filename, "sha256": definition.sha256, "entorno": settings.APP_ENV, "base": base, "proceso": definition.process["nombre"], "version": definition.process["version"], "cantidades": counts, "conflictos": conflicts, "correspondencias": maps, "token_validacion": None if conflicts else _token(definition)}


def import_definition(db: Session, raw: bytes, filename: str, supplied_token: str, identity: AdminIdentity) -> dict:
    with _IMPORT_LOCK:
        definition, report = preview(db, raw, filename)
        if report["conflictos"]: raise Xml2ErConflict("; ".join(report["conflictos"]))
        if not supplied_token or not hmac.compare_digest(supplied_token, _token(definition)): raise Xml2ErConflict("La validación no corresponde al archivo actual; vuelva a validar")
        try:
            db.execute(text("SELECT GET_LOCK('bitacora_xml2er_import', 15)"))
            definition, report = preview(db, raw, filename)
            if report["conflictos"]: raise Xml2ErConflict("; ".join(report["conflictos"]))
            activity_ids = {}
            for item in definition.activities:
                activity_id = db.execute(text("SELECT id_actividad FROM cat_actividad WHERE nombre=:n"), {"n": item["nombre"]}).scalar()
                if activity_id is None:
                    result = db.execute(text("INSERT INTO cat_actividad(nombre,descripcion) VALUES(:n,:d)"), {"n": item["nombre"], "d": item["descripcion"]}); activity_id = result.lastrowid
                activity_ids[item["ref"]] = int(activity_id)
            node_by_ref = {x["ref"]: x for x in definition.nodes}; pending = list(definition.nodes); node_ids = {}
            while pending:
                progressed = False
                for item in pending[:]:
                    if item["padre_ref"] and item["padre_ref"] not in node_ids: continue
                    parent_id = node_ids.get(item["padre_ref"]) if item["padre_ref"] else item["padre_id"]
                    activity_id = activity_ids.get(item["actividad_ref"]) if item["actividad_ref"] else None
                    if item["actividad_nombre"]: activity_id = db.execute(text("SELECT id_actividad FROM cat_actividad WHERE nombre=:n"), {"n": item["actividad_nombre"]}).scalar_one()
                    if not db.execute(text("SELECT 1 FROM dim_proceso WHERE id_proceso=:id"), {"id": item["id_proceso"]}).scalar():
                        db.execute(text("""INSERT INTO dim_proceso(id_proceso,nombre,descripcion,id_proceso_padre,tiempo_estimado,costo_estimado,tipo_proceso,precondicion,id_actividad,nombre_corto)
                            VALUES(:id_proceso,:nombre,:descripcion,:id_proceso_padre,:tiempo_estimado,:costo_estimado,:tipo_proceso,:precondicion,:id_actividad,:nombre_corto)"""), _business_node(item, parent_id, activity_id))
                    node_ids[item["ref"]] = item["id_proceso"]; pending.remove(item); progressed = True
                if not progressed: raise Xml2ErConflict("No fue posible resolver el orden padre-descendiente")
            existing = db.execute(text("SELECT id_bpm_proceso FROM bpm_proceso WHERE nombre=:n AND version=:v"), {"n": definition.process["nombre"], "v": definition.process["version"]}).scalar()
            if existing:
                expected_stages = sorted([{"id_etapa": node_ids.get(x["nodo_ref"], x["nodo_id"]), "rol_responsable": x["rol_responsable"], "plazo_horas": x["plazo_horas"], "es_inicial": x["es_inicial"], "es_final": x["es_final"]} for x in definition.stages], key=lambda x: x["id_etapa"])
                stage_ids = {x["ref"]: node_ids.get(x["nodo_ref"], x["nodo_id"]) for x in definition.stages}
                expected_transitions = sorted([{"id_etapa_origen": stage_ids[x["origen_ref"]], "id_etapa_destino": stage_ids[x["destino_ref"]], "codigo_regla": x["codigo_regla"], "resultado": x["resultado"], "activo": x["activo"]} for x in definition.transitions], key=lambda x: (x["id_etapa_origen"], x["id_etapa_destino"], x["codigo_regla"], x["resultado"] or ""))
                persisted = _semantic_existing(db, int(existing))
                process_expected = {"nombre": definition.process["nombre"], "version": definition.process["version"], "descripcion": definition.process["descripcion"], "activo": definition.process["activo"]}
                if persisted != {"process": process_expected, "stages": expected_stages, "transitions": expected_transitions}: raise Xml2ErConflict("La versión existente tiene una definición diferente; use una versión nueva")
                process_id, state = int(existing), "SIN CAMBIOS"
            else:
                result = db.execute(text("INSERT INTO bpm_proceso(nombre,version,descripcion,activo) VALUES(:nombre,:version,:descripcion,:activo)"), definition.process); process_id = int(result.lastrowid)
                stage_ids = {}
                for item in definition.stages:
                    stage_id = node_ids.get(item["nodo_ref"], item["nodo_id"]); stage_ids[item["ref"]] = stage_id
                    db.execute(text("INSERT INTO bpm_etapa(id_bpm_proceso,id_etapa,rol_responsable,plazo_horas,es_inicial,es_final) VALUES(:p,:e,:r,:h,:i,:f)"), {"p": process_id, "e": stage_id, "r": item["rol_responsable"], "h": item["plazo_horas"], "i": item["es_inicial"], "f": item["es_final"]})
                transition_ids = {}
                for item in definition.transitions:
                    result = db.execute(text("INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo) VALUES(:p,:o,:d,:c,:r,1)"), {"p": process_id, "o": stage_ids[item["origen_ref"]], "d": stage_ids[item["destino_ref"]], "c": item["codigo_regla"], "r": item["resultado"]}); transition_ids[item["ref"]] = int(result.lastrowid)
                state = "IMPORTADO"
            db.commit()
            if existing:
                stage_ids = {x["ref"]: node_ids.get(x["nodo_ref"], x["nodo_id"]) for x in definition.stages}
                transition_ids = {x["ref"]: int(row["id_transicion"]) for x, row in zip(definition.transitions, db.execute(text("SELECT id_transicion FROM bpm_transicion WHERE id_bpm_proceso=:id ORDER BY id_etapa_origen,id_etapa_destino,codigo_regla,COALESCE(resultado,'')"), {"id": process_id}).mappings())}
            return {**report, "estado": state, "confirmado": True, "usuario": identity.actor, "fecha_hora": datetime.now(timezone.utc).isoformat(), "correspondencias": {"actividades": activity_ids, "nodos": node_ids, "etapas": stage_ids, "transiciones": transition_ids, "proceso": {"id_bpm_proceso": process_id}}}
        except Exception:
            db.rollback(); raise
        finally:
            try: db.execute(text("SELECT RELEASE_LOCK('bitacora_xml2er_import')"))
            except Exception: pass
