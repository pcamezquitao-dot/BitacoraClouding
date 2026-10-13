from sqlalchemy import text
from sqlalchemy.orm import Session


class PortalError(ValueError):
    pass


class PortalForbidden(PortalError):
    pass


class PortalConfigurationError(PortalError):
    pass


def _participant(db: Session, raw_code: str) -> dict:
    code = raw_code.strip().upper()
    row = db.execute(text("""
        SELECT p.id_participante,p.identificacion_participante AS codigo,
               TRIM(CONCAT_WS(' ',p.nombre,p.apellido)) AS nombre_completo
        FROM participante p
        WHERE UPPER(TRIM(p.identificacion_participante))=:code
          AND (p.fecha_salida IS NULL OR p.fecha_salida>=CURRENT_DATE)
        LIMIT 1
    """), {"code": code}).mappings().first()
    if not row:
        raise PortalForbidden("El participante no existe o no está activo")
    roles = db.execute(text("""
        SELECT DISTINCT tp.codigo,tp.descripcion
        FROM empleado_area ea
        JOIN tipos_participante tp ON tp.codigo=ea.cargo AND tp.activo=1
        WHERE ea.id_participante=:id AND ea.activo=1
          AND ea.fecha_inicia<=CURRENT_DATE
          AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURRENT_DATE)
        ORDER BY tp.codigo
    """), {"id": row["id_participante"]}).mappings().all()
    if not roles:
        raise PortalForbidden("El participante no tiene un tipo activo")
    if len(roles) != 1:
        raise PortalConfigurationError("El participante tiene más de un tipo activo; no se puede determinar una raíz única")
    return {**dict(row), "tipo_codigo": int(roles[0]["codigo"]),
            "tipo_descripcion": str(roles[0]["descripcion"])}


def _roots(db: Session, role: str) -> list[dict]:
    return [dict(row) for row in db.execute(text("""
        SELECT id_accion,nombre FROM dim_accion
        WHERE id_accion_padre IS NULL
          AND UPPER(TRIM(nombre))=UPPER(TRIM(:role))
        ORDER BY id_accion
    """), {"role": role}).mappings()]


def _item(row: dict) -> dict:
    bpm_ids = [int(value) for value in str(row.get("bpm_ids") or "").split(",") if value]
    bpm_names = [value for value in str(row.get("bpm_names") or "").split("||") if value]
    if row["id_proceso"] is None:
        state = "SIN_PROCESO"
    elif row["nombre_proceso"] is None:
        state = "PROCESO_INEXISTENTE"
    elif len(bpm_ids) == 0:
        state = "SIN_FLUJO_BPM"
    elif len(bpm_ids) > 1:
        state = "FLUJO_BPM_AMBIGUO"
    elif not bool(row["bpm_activo"]):
        state = "FLUJO_BPM_INACTIVO"
    else:
        state = "DISPONIBLE"
    return {
        "id_accion": int(row["id_accion"]), "nombre": row["nombre"],
        "descripcion": row["descripcion"], "id_proceso": row["id_proceso"],
        "nombre_proceso": row["nombre_proceso"],
        "id_bpm_proceso": bpm_ids[0] if len(bpm_ids) == 1 else None,
        "nombre_bpm": bpm_names[0] if len(bpm_names) == 1 else None,
        "bpm_activo": bool(row["bpm_activo"]) if len(bpm_ids) == 1 else False,
        "estado_destino": state,
    }


def _children(db: Session, root_id: int) -> list[dict]:
    rows = db.execute(text("""
        SELECT a.id_accion,a.nombre,a.descripcion,a.id_proceso,p.nombre AS nombre_proceso,
               GROUP_CONCAT(DISTINCT bp.id_bpm_proceso ORDER BY bp.id_bpm_proceso) AS bpm_ids,
               GROUP_CONCAT(DISTINCT bp.nombre ORDER BY bp.id_bpm_proceso SEPARATOR '||') AS bpm_names,
               COALESCE(MIN(bp.activo),0) AS bpm_activo
        FROM dim_accion a
        LEFT JOIN dim_proceso p ON p.id_proceso=a.id_proceso
        LEFT JOIN bpm_etapa be ON be.id_etapa=p.id_proceso
        LEFT JOIN bpm_proceso bp ON bp.id_bpm_proceso=be.id_bpm_proceso
        WHERE a.id_accion_padre=:root
        GROUP BY a.id_accion,a.nombre,a.descripcion,a.id_proceso,p.nombre
        ORDER BY LOWER(TRIM(a.nombre)),a.id_accion
    """), {"root": root_id}).mappings().all()
    return [_item(dict(row)) for row in rows]


def load_portal(db: Session, code: str) -> dict:
    participant = _participant(db, code)
    roots = _roots(db, participant["tipo_descripcion"])
    base = {"participante": participant, "id_raiz": None, "opciones": []}
    if not roots:
        return {**base, "estado_menu": "RAIZ_AUSENTE",
                "mensaje_menu": "No existe una raíz de navegación para el tipo del participante"}
    if len(roots) > 1:
        return {**base, "estado_menu": "RAIZ_DUPLICADA",
                "mensaje_menu": "Existe más de una raíz de navegación para el tipo del participante"}
    root_id = int(roots[0]["id_accion"])
    options = _children(db, root_id)
    return {**base, "id_raiz": root_id, "estado_menu": "OK" if options else "SIN_HIJOS",
            "mensaje_menu": None if options else "La raíz no tiene opciones configuradas",
            "opciones": options}


def authorize_item(db: Session, code: str, action_id: int) -> dict:
    portal = load_portal(db, code)
    option = next((item for item in portal["opciones"] if item["id_accion"] == action_id), None)
    if not option:
        raise PortalForbidden("La opción no pertenece al portal del participante")
    if option["estado_destino"] != "DISPONIBLE":
        raise PortalConfigurationError({
            "SIN_PROCESO": "La opción no tiene proceso asociado",
            "PROCESO_INEXISTENTE": "El proceso asociado no existe",
            "SIN_FLUJO_BPM": "El proceso no tiene flujo BPM asociado",
            "FLUJO_BPM_AMBIGUO": "El proceso pertenece a más de un flujo BPM",
            "FLUJO_BPM_INACTIVO": "El flujo BPM no está habilitado",
        }[option["estado_destino"]])
    return option

