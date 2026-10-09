import hashlib
import json
import re
import unicodedata
import uuid
import subprocess
from datetime import datetime
from xml.etree import ElementTree as ET

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.config import settings
from app.schemas.normaliza_proceso import StructuredDefinition


class Cu30NotFound(ValueError): pass
class Cu30Conflict(ValueError): pass


def render_plantuml_svg(source: str) -> str:
    try:
        completed = subprocess.run(
            ["java", "-Djava.awt.headless=true", "-jar", settings.PLANTUML_JAR_PATH, "-pipe", "-tsvg"],
            input=source.encode("utf-8"), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            check=True, timeout=20,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise Cu30Conflict("El renderizador PlantUML no está disponible o rechazó el diagrama") from error
    svg = completed.stdout.decode("utf-8", errors="replace")
    if "<svg" not in svg:
        raise Cu30Conflict("PlantUML no produjo un SVG válido")
    return svg


CONCEPTS = {
    "validar": {"validar", "verificar", "comprobar", "revisar"},
    "registrar": {"registrar", "guardar", "almacenar", "radicar"},
    "aprobar": {"aprobar", "autorizar", "aceptar"},
    "notificar": {"notificar", "avisar", "informar", "comunicar"},
    "consultar": {"consultar", "buscar", "obtener", "leer"},
    "documento": {"documento", "archivo", "soporte", "evidencia"},
    "solicitud": {"solicitud", "peticion", "requerimiento", "tramite"},
}
STOP = {"el", "la", "los", "las", "un", "una", "de", "del", "y", "o", "en", "por", "para", "con", "se", "su", "al"}


def _plain(value: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", value.lower()) if unicodedata.category(c) != "Mn")


def _tokens(value: str) -> set[str]:
    raw = {x for x in re.findall(r"[a-z0-9]+", _plain(value)) if len(x) > 1 and x not in STOP}
    result = set(raw)
    for concept, words in CONCEPTS.items():
        if raw & words:
            result.add(f"concept:{concept}")
    return result


def catalog_snapshot(db: Session) -> tuple[list[dict], str]:
    rows = [dict(r) for r in db.execute(text(
        "SELECT id_actividad,nombre,descripcion FROM cat_actividad ORDER BY id_actividad"
    )).mappings().all()]
    digest = hashlib.sha256(json.dumps(rows, ensure_ascii=False, sort_keys=True, default=str).encode()).hexdigest()
    return rows, digest


def candidates(requirement: str, catalog: list[dict], limit: int = 5) -> list[dict]:
    wanted = _tokens(requirement)
    ranked = []
    for item in catalog:
        name = _tokens(item["nombre"])
        description = _tokens(item.get("descripcion") or "")
        available = name | description
        shared = wanted & available
        union = wanted | available
        score = (len(shared) / len(union) if union else 0.0) + 0.15 * len({x for x in shared if x.startswith("concept:")})
        differences = sorted(x for x in wanted - available if not x.startswith("concept:"))[:8]
        ranked.append({
            **item,
            "coincidencias": sorted(x.replace("concept:", "función ") for x in shared),
            "diferencias": differences,
            "puntuacion": round(min(score, 1.0), 4),
        })
    return sorted(ranked, key=lambda x: (-x["puntuacion"], x["nombre"].casefold(), x["id_actividad"]))[:limit]


def analyze(original: str, title: str, catalog: list[dict]) -> dict:
    chunks = [x.strip(" -\t") for x in re.split(r"(?:\r?\n)+|(?<=;)\s+|(?<=\.)\s+(?=[A-ZÁÉÍÓÚÑ])", original) if x.strip(" -\t")]
    steps = []
    for index, chunk in enumerate(chunks, 1):
        lowered = _plain(chunk)
        kind = "final" if re.search(r"\b(fin|finaliza|termina)\b", lowered) else "espera" if re.search(r"\b(espera|plazo|dias|horas)\b", lowered) else "decision" if re.search(r"\b(si|decide|decision|segun)\b", lowered) else "actividad"
        role_match = re.search(
            r"\b(?:responsable|por)\b\s*[:=]?\s*([A-ZÁÉÍÓÚÑ][\wÁÉÍÓÚÑáéíóúñ ]{2,40}?)"
            r"(?=\s+(?:debe|realiza|ejecuta|valida|revisa|aprueba|envía|envia)\b|[.;]|$)",
            chunk,
            flags=re.I,
        )
        integrations = re.findall(r"\b(?:API|Gmail|WhatsApp|SAP|correo)\b", chunk, flags=re.I)
        documents = re.findall(r"\b[\w-]+\.(?:pdf|docx?|xlsx?|xml|csv)\b", chunk, flags=re.I)
        data_matches = re.findall(r"\b(?:dato|datos|campo|campos)\s*[:=]\s*([^.;]+)", chunk, flags=re.I)
        data_items = [item.strip() for group in data_matches for item in re.split(r",|\by\b", group) if item.strip()]
        step_id = f"P{index:03d}"
        steps.append({
            "paso_id": step_id,
            "requerimiento": chunk,
            "responsable": role_match.group(1).strip() if role_match else None,
            "documentos": documents,
            "reglas": [chunk] if any(x in lowered for x in ("debe", "solo", "excepto", "prohib")) else [],
            "datos": data_items,
            "integraciones": integrations,
            "tipo": kind,
            "siguiente": [f"P{index + 1:03d}"] if index < len(chunks) else [],
            "id_actividad": None,
            "nombre_actividad": None,
            "propuesta_codigo": None,
            "propuesta_nombre": None,
            "propuesta_descripcion": None,
            "candidates": candidates(chunk, catalog) if kind == "actividad" else [],
        })
    plain_original = _plain(original)
    observations = []
    if re.search(r"\b(quiza|quizas|posiblemente|tal vez|segun corresponda|etcetera|etc)\b", plain_original):
        observations.append("Aclarar expresiones ambiguas del requerimiento original")
    if "debe" in plain_original and "no debe" in plain_original:
        observations.append("Resolver reglas potencialmente contradictorias (debe/no debe)")
    return {"nombre": title, "pasos": steps, "observaciones": observations}


def validate_definition(definition: dict, catalog: list[dict]) -> list[str]:
    pending = [f"Observación pendiente: {value}" for value in definition.get("observaciones", []) if value.strip()]
    by_id = {x["id_actividad"]: x for x in catalog}
    ids = [x["paso_id"] for x in definition["pasos"]]
    if len(ids) != len(set(ids)):
        pending.append("Existen identificadores de paso duplicados")
    valid_ids = set(ids)
    for step in definition["pasos"]:
        prefix = step["paso_id"]
        for target in step.get("siguiente", []):
            if target not in valid_ids:
                pending.append(f"{prefix}: transición a {target} no existe")
        if step["tipo"] == "decision" and len(step.get("siguiente", [])) < 2:
            pending.append(f"{prefix}: la decisión requiere al menos dos transiciones")
        if step["tipo"] == "actividad":
            activity_id = step.get("id_actividad")
            proposal = step.get("propuesta_codigo")
            if activity_id is None and not proposal:
                pending.append(f"{prefix}: seleccione una actividad o complete una propuesta")
            if activity_id is not None:
                current = by_id.get(activity_id)
                if not current:
                    pending.append(f"{prefix}: la actividad seleccionada ya no existe")
                elif current["nombre"] != step.get("nombre_actividad"):
                    pending.append(f"{prefix}: el nombre de la actividad cambió; actualice la selección")
            if proposal and (not step.get("propuesta_nombre") or not step.get("propuesta_descripcion")):
                pending.append(f"{prefix}: la propuesta requiere nombre y descripción")
        if not step.get("responsable") and step["tipo"] == "actividad":
            pending.append(f"{prefix}: falta responsable")
    if definition["pasos"] and not any(x["tipo"] == "final" for x in definition["pasos"]):
        pending.append("La definición requiere al menos un final explícito")
    return sorted(set(pending))


def generate_products(definition: dict) -> tuple[str, str, str]:
    lines = [f"Proceso: {definition['nombre']}"]
    puml = ["@startuml", f"title {definition['nombre']}"]
    root = ET.Element("proceso", {"version_gramatica": "cu30-1", "nombre": definition["nombre"]})
    steps_el = ET.SubElement(root, "pasos")
    has_final = False
    for step in definition["pasos"]:
        selected = step.get("nombre_actividad") or step.get("propuesta_nombre") or step["requerimiento"]
        responsible = step.get("responsable") or ("PENDIENTE" if step["tipo"] == "actividad" else "NO APLICA")
        lines.append(f"{step['paso_id']}. [{responsible}] {selected}. Requisito: {step['requerimiento']}")
        safe_label = str(selected).replace('"', "'").replace("\n", " ")
        stereotype = " <<choice>>" if step["tipo"] == "decision" else ""
        puml.append(f'state "{step["paso_id"]}: {safe_label}\\nResponsable: {responsible}" as {step["paso_id"]}{stereotype}')
        if step["tipo"] == "final": has_final = True
        attrs = {"id": step["paso_id"], "tipo": step["tipo"], "responsable": responsible}
        if step.get("id_actividad") is not None:
            attrs.update({"actividad_tipo": "catalogo", "id_actividad": str(step["id_actividad"]), "actividad_nombre": step["nombre_actividad"]})
        elif step.get("propuesta_codigo"):
            attrs.update({"actividad_tipo": "propuesta", "codigo_provisional": step["propuesta_codigo"], "actividad_nombre": step["propuesta_nombre"]})
        element = ET.SubElement(steps_el, "paso", attrs)
        ET.SubElement(element, "requerimiento").text = step["requerimiento"]
        for target in step.get("siguiente", []): ET.SubElement(element, "transicion", {"destino": target})
        for document in step.get("documentos", []): ET.SubElement(element, "documento").text = document
        for rule in step.get("reglas", []): ET.SubElement(element, "regla").text = rule
        for datum in step.get("datos", []): ET.SubElement(element, "dato").text = datum
        for integration in step.get("integraciones", []): ET.SubElement(element, "integracion", {"nombre": integration, "ejecutar": "false"})
    if definition["pasos"]:
        puml.append(f'[*] --> {definition["pasos"][0]["paso_id"]}')
    for step in definition["pasos"]:
        for target in step.get("siguiente", []): puml.append(f'{step["paso_id"]} --> {target}')
        if step["tipo"] == "final": puml.append(f'{step["paso_id"]} --> [*]')
    puml.append("@enduml")
    xml = ET.tostring(root, encoding="unicode")
    ET.fromstring(xml)
    return "\n".join(lines), "\n".join(puml), xml


def _digest_products(definition: dict, normalized: str, plantuml: str, xml: str) -> str:
    value = json.dumps({"definition": definition, "normalized": normalized, "plantuml": plantuml, "xml": xml}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(value.encode()).hexdigest()


def _version_out(row) -> dict:
    value = dict(row)
    return {
        "id_version": value["id_version"], "numero": value["numero"], "estado": value["estado"],
        "definicion": json.loads(value["definicion_json"]), "texto_normalizado": value["texto_normalizado"],
        "plantuml": value["plantuml"], "xml_definicion": value["xml_definicion"],
        "pendientes": json.loads(value["pendientes_json"]), "catalogo_sha256": value["catalogo_sha256"],
        "contenido_sha256": value["contenido_sha256"], "aprobado_por": value["aprobado_por"],
        "aprobado_en": value["aprobado_en"].isoformat() if value["aprobado_en"] else None,
    }


def _audit(db, work_id: str, version_id: str | None, action: str, identity: AdminIdentity, detail=None):
    db.execute(text("INSERT INTO cu30_auditoria(id_trabajo,id_version,accion,actor,detalle_json) VALUES(:w,:v,:a,:actor,:d)"),
               {"w": work_id, "v": version_id, "a": action, "actor": identity.actor, "d": json.dumps(detail, ensure_ascii=False) if detail else None})


def create_work(db: Session, title: str, original: str, identity: AdminIdentity) -> dict:
    catalog, catalog_hash = catalog_snapshot(db)
    definition = analyze(original, title, catalog)
    pending = validate_definition(definition, catalog)
    normalized, plantuml, xml = generate_products(definition)
    work_id, version_id = str(uuid.uuid4()), str(uuid.uuid4())
    content_hash = _digest_products(definition, normalized, plantuml, xml)
    original_hash = hashlib.sha256(original.encode()).hexdigest()
    db.execute(text("INSERT INTO cu30_trabajo(id_trabajo,titulo,requerimiento_original,requerimiento_sha256,creado_por) VALUES(:id,:t,:o,:h,:a)"), {"id": work_id, "t": title.strip(), "o": original, "h": original_hash, "a": identity.actor})
    db.execute(text("""INSERT INTO cu30_version(id_version,id_trabajo,numero,definicion_json,texto_normalizado,plantuml,xml_definicion,pendientes_json,catalogo_sha256,contenido_sha256,creado_por)
        VALUES(:id,:w,1,:d,:n,:p,:x,:q,:c,:h,:a)"""), {"id": version_id, "w": work_id, "d": json.dumps(definition, ensure_ascii=False), "n": normalized, "p": plantuml, "x": xml, "q": json.dumps(pending, ensure_ascii=False), "c": catalog_hash, "h": content_hash, "a": identity.actor})
    _audit(db, work_id, version_id, "CREAR", identity, {"original_sha256": original_hash})
    db.commit()
    return get_work(db, work_id)


def list_works(db: Session) -> list[dict]:
    rows = db.execute(text("SELECT id_trabajo,titulo,estado,version_actual,creado_por,creado_en,actualizado_en FROM cu30_trabajo ORDER BY actualizado_en DESC")).mappings().all()
    return [{**dict(r), "creado_en": r["creado_en"].isoformat(), "actualizado_en": r["actualizado_en"].isoformat()} for r in rows]


def get_work(db: Session, work_id: str) -> dict:
    work = db.execute(text("SELECT * FROM cu30_trabajo WHERE id_trabajo=:id"), {"id": work_id}).mappings().first()
    if not work: raise Cu30NotFound("El trabajo CU30 no existe")
    version = db.execute(text("SELECT * FROM cu30_version WHERE id_trabajo=:id AND numero=:n"), {"id": work_id, "n": work["version_actual"]}).mappings().first()
    return {**dict(work), "creado_en": work["creado_en"].isoformat(), "actualizado_en": work["actualizado_en"].isoformat(), "version": _version_out(version)}


def regenerate(db: Session, work_id: str, definition_model: StructuredDefinition, expected: int, identity: AdminIdentity) -> dict:
    work = get_work(db, work_id)
    if work["version_actual"] != expected: raise Cu30Conflict("El trabajo cambió; actualice antes de guardar")
    definition = definition_model.model_dump()
    catalog, catalog_hash = catalog_snapshot(db)
    for step in definition["pasos"]:
        step["candidates"] = candidates(step["requerimiento"], catalog) if step["tipo"] == "actividad" else []
    pending = validate_definition(definition, catalog)
    normalized, plantuml, xml = generate_products(definition)
    number = expected + 1
    version_id = str(uuid.uuid4())
    content_hash = _digest_products(definition, normalized, plantuml, xml)
    db.execute(text("""INSERT INTO cu30_version(id_version,id_trabajo,numero,definicion_json,texto_normalizado,plantuml,xml_definicion,pendientes_json,catalogo_sha256,contenido_sha256,creado_por)
      VALUES(:id,:w,:n,:d,:t,:p,:x,:q,:c,:h,:a)"""), {"id": version_id, "w": work_id, "n": number, "d": json.dumps(definition, ensure_ascii=False), "t": normalized, "p": plantuml, "x": xml, "q": json.dumps(pending, ensure_ascii=False), "c": catalog_hash, "h": content_hash, "a": identity.actor})
    db.execute(text("UPDATE cu30_trabajo SET version_actual=:n,estado='BORRADOR' WHERE id_trabajo=:id AND version_actual=:expected"), {"n": number, "id": work_id, "expected": expected})
    _audit(db, work_id, version_id, "REGENERAR", identity, {"version": number})
    db.commit()
    return get_work(db, work_id)


def approve(db: Session, work_id: str, expected: int, identity: AdminIdentity) -> dict:
    work = get_work(db, work_id)
    if work["version_actual"] != expected: raise Cu30Conflict("El trabajo cambió; actualice antes de aprobar")
    version = work["version"]
    catalog, current_hash = catalog_snapshot(db)
    pending = validate_definition(version["definicion"], catalog)
    if current_hash != version["catalogo_sha256"]:
        pending.append("El catálogo cambió desde la generación; regenere antes de aprobar")
    if pending: raise Cu30Conflict("No se puede aprobar: " + "; ".join(sorted(set(pending))))
    now = datetime.now()
    updated = db.execute(text("""UPDATE cu30_version SET estado='APROBADA',aprobado_por=:a,aprobado_en=:now
      WHERE id_trabajo=:id AND numero=:n AND estado='PENDIENTE' AND contenido_sha256=:h"""), {"a": identity.actor, "now": now, "id": work_id, "n": expected, "h": version["contenido_sha256"]})
    if updated.rowcount != 1: raise Cu30Conflict("La versión ya no está pendiente de aprobación")
    db.execute(text("UPDATE cu30_trabajo SET estado='APROBADA' WHERE id_trabajo=:id"), {"id": work_id})
    _audit(db, work_id, version["id_version"], "APROBAR", identity, {"contenido_sha256": version["contenido_sha256"]})
    db.commit()
    return {"id_trabajo": work_id, "numero_version": expected, "estado": "APROBADA", "aprobado_por": identity.actor, "aprobado_en": now.isoformat(), "contenido_sha256": version["contenido_sha256"], "disponible_para_cu31": True}
