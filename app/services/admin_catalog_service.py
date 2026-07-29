import json

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.admin_auth import AdminIdentity
from app.core.config import settings
from app.services.admin_catalog_policy import (
    normalize_type_description,
    validate_assignment_period,
    validate_capabilities,
)


class AdministrativeAreaNotFound(ValueError):
    pass


def list_participant_types(db: Session) -> list[dict]:
    rows = db.execute(
        text(
            """
            SELECT tp.codigo, tp.descripcion, tp.activo,
                   GROUP_CONCAT(tpc.codigo_capacidad
                       ORDER BY tpc.codigo_capacidad) AS capacidades
            FROM tipos_participante AS tp
            LEFT JOIN tipo_participante_capacidad AS tpc
              ON tpc.codigo_tipo = tp.codigo
            GROUP BY tp.codigo, tp.descripcion, tp.activo
            ORDER BY tp.descripcion, tp.codigo
            """
        )
    ).mappings().all()
    return [
        {
            **dict(row),
            "activo": bool(row["activo"]),
            "capacidades": (
                row["capacidades"].split(",") if row["capacidades"] else []
            ),
        }
        for row in rows
    ]


def create_participant_type(
    db: Session,
    description: str,
    capabilities: list[str],
    identity: AdminIdentity,
) -> dict:
    normalized_description = normalize_type_description(description)
    try:
        active_rows = db.execute(
            text(
                """
                SELECT codigo
                FROM capacidades_participante
                WHERE activo = TRUE
                """
            )
        ).scalars().all()
        normalized_capabilities = validate_capabilities(
            capabilities,
            active_capabilities=set(active_rows),
        )
        duplicate = db.execute(
            text(
                """
                SELECT codigo
                FROM tipos_participante
                WHERE UPPER(TRIM(descripcion)) = UPPER(:descripcion)
                LIMIT 1
                """
            ),
            {"descripcion": normalized_description},
        ).scalar_one_or_none()
        if duplicate is not None:
            raise ValueError("Ya existe un tipo con esa descripción")

        inserted = db.execute(
            text(
                """
                INSERT INTO tipos_participante (descripcion, activo)
                VALUES (:descripcion, TRUE)
                """
            ),
            {"descripcion": normalized_description},
        )
        type_code = int(inserted.lastrowid)
        for capability in sorted(normalized_capabilities):
            db.execute(
                text(
                    """
                    INSERT INTO tipo_participante_capacidad
                        (codigo_tipo, codigo_capacidad)
                    VALUES (:codigo_tipo, :codigo_capacidad)
                    """
                ),
                {"codigo_tipo": type_code, "codigo_capacidad": capability},
            )
        result = {
            "codigo": type_code,
            "descripcion": normalized_description,
            "activo": True,
            "capacidades": sorted(normalized_capabilities),
        }
        _audit(
            db,
            catalog="tipos_participante",
            key=str(type_code),
            operation="CREAR",
            before=None,
            after=result,
            identity=identity,
        )
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise


def update_participant_type(
    db: Session,
    type_code: int,
    description: str,
    capabilities: list[str],
    identity: AdminIdentity,
) -> dict:
    normalized_description = normalize_type_description(description)
    try:
        before = _participant_type_for_update(db, type_code)
        if before is None:
            raise ValueError("El tipo no existe")
        active_rows = db.execute(
            text(
                "SELECT codigo FROM capacidades_participante "
                "WHERE activo=TRUE"
            )
        ).scalars().all()
        normalized_capabilities = validate_capabilities(
            capabilities,
            active_capabilities=set(active_rows),
        )
        duplicate = db.execute(
            text(
                """
                SELECT codigo FROM tipos_participante
                WHERE UPPER(TRIM(descripcion))=UPPER(:descripcion)
                  AND codigo<>:codigo
                LIMIT 1
                """
            ),
            {"descripcion": normalized_description, "codigo": type_code},
        ).scalar_one_or_none()
        if duplicate is not None:
            raise ValueError("Ya existe un tipo con esa descripción")
        db.execute(
            text(
                "UPDATE tipos_participante SET descripcion=:descripcion "
                "WHERE codigo=:codigo"
            ),
            {"descripcion": normalized_description, "codigo": type_code},
        )
        db.execute(
            text(
                "DELETE FROM tipo_participante_capacidad "
                "WHERE codigo_tipo=:codigo"
            ),
            {"codigo": type_code},
        )
        for capability in sorted(normalized_capabilities):
            db.execute(
                text(
                    """
                    INSERT INTO tipo_participante_capacidad
                        (codigo_tipo, codigo_capacidad)
                    VALUES (:codigo, :capacidad)
                    """
                ),
                {"codigo": type_code, "capacidad": capability},
            )
        result = {
            "codigo": type_code,
            "descripcion": normalized_description,
            "activo": before["activo"],
            "capacidades": sorted(normalized_capabilities),
        }
        _audit(
            db,
            "tipos_participante",
            str(type_code),
            "EDITAR",
            before,
            result,
            identity,
        )
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise


def set_participant_type_status(
    db: Session,
    type_code: int,
    active: bool,
    identity: AdminIdentity,
) -> dict:
    try:
        before = _participant_type_for_update(db, type_code)
        if before is None:
            raise ValueError("El tipo no existe")
        db.execute(
            text(
                "UPDATE tipos_participante SET activo=:activo "
                "WHERE codigo=:codigo"
            ),
            {"activo": active, "codigo": type_code},
        )
        result = {**before, "activo": active}
        _audit(
            db,
            "tipos_participante",
            str(type_code),
            "ACTIVAR" if active else "DESACTIVAR",
            before,
            result,
            identity,
        )
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise


def list_area_tree(db: Session) -> list[dict]:
    return [
        dict(row)
        for row in db.execute(
            text(
                """
                WITH RECURSIVE arbol AS (
                    SELECT id_Area_Administrativa AS id_area,
                           descripcion, nombre_corto, nodo_padre AS id_padre,
                           0 AS nivel,
                           CAST(descripcion AS CHAR(2000)) AS ruta,
                           CAST(
                               LPAD(id_Area_Administrativa, 10, '0')
                               AS CHAR(2000)
                           ) AS orden
                    FROM areas_administrativas
                    WHERE nodo_padre IS NULL
                       OR nodo_padre = 0
                       OR NOT EXISTS (
                           SELECT 1
                           FROM areas_administrativas AS padre
                           WHERE padre.id_Area_Administrativa =
                                 areas_administrativas.nodo_padre
                       )
                    UNION ALL
                    SELECT a.id_Area_Administrativa, a.descripcion,
                           a.nombre_corto, a.nodo_padre, arbol.nivel + 1,
                           CONCAT(arbol.ruta, ' > ', a.descripcion),
                           CONCAT(
                               arbol.orden,
                               '.',
                               LPAD(a.id_Area_Administrativa, 10, '0')
                           )
                    FROM areas_administrativas AS a
                    JOIN arbol ON a.nodo_padre = arbol.id_area
                )
                SELECT id_area, descripcion, nombre_corto, id_padre, nivel, ruta
                FROM arbol
                ORDER BY orden
                """
            )
        ).mappings().all()
    ]


def create_administrative_area(
    db: Session,
    payload,
    identity: AdminIdentity,
) -> dict:
    description = payload.descripcion.strip()
    short_name = (payload.nombre_corto or "").strip() or None
    parent_id = payload.nodo_padre or None
    try:
        if parent_id is not None and not _area_exists(db, parent_id):
            raise ValueError("El área padre no existe")
        _ensure_area_not_duplicate(db, description, parent_id)
        inserted = db.execute(
            text(
                """
                INSERT INTO areas_administrativas
                    (descripcion, nombre_corto, nodo_padre)
                VALUES (:descripcion, :nombre_corto, :nodo_padre)
                """
            ),
            {
                "descripcion": description,
                "nombre_corto": short_name,
                "nodo_padre": parent_id,
            },
        )
        result = {
            "id_area_administrativa": inserted.lastrowid,
            "descripcion": description,
            "nombre_corto": short_name,
            "nodo_padre": parent_id,
        }
        _audit(
            db,
            "areas_administrativas",
            str(inserted.lastrowid),
            "CREAR",
            None,
            result,
            identity,
        )
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise


def update_administrative_area(
    db: Session,
    area_id: int,
    payload,
    identity: AdminIdentity,
) -> dict:
    description = payload.descripcion.strip()
    short_name = (payload.nombre_corto or "").strip() or None
    parent_id = payload.nodo_padre or None
    try:
        before = _get_area(db, area_id)
        if before is None:
            raise AdministrativeAreaNotFound(
                "El área administrativa no existe"
            )
        if parent_id == area_id:
            raise ValueError("Un área no puede ser hija de sí misma")
        if parent_id is not None:
            if not _area_exists(db, parent_id):
                raise ValueError("El área padre no existe")
            descendant = db.execute(
                text(
                    """
                    WITH RECURSIVE descendientes AS (
                        SELECT id_Area_Administrativa
                        FROM areas_administrativas
                        WHERE nodo_padre = :id_area
                        UNION ALL
                        SELECT hija.id_Area_Administrativa
                        FROM areas_administrativas AS hija
                        JOIN descendientes AS padre
                          ON hija.nodo_padre = padre.id_Area_Administrativa
                    )
                    SELECT 1
                    FROM descendientes
                    WHERE id_Area_Administrativa = :id_padre
                    LIMIT 1
                    """
                ),
                {"id_area": area_id, "id_padre": parent_id},
            ).scalar_one_or_none()
            if descendant is not None:
                raise ValueError(
                    "Un área no puede depender de una de sus descendientes"
                )
        _ensure_area_not_duplicate(
            db,
            description,
            parent_id,
            exclude_area_id=area_id,
        )
        db.execute(
            text(
                """
                UPDATE areas_administrativas
                SET descripcion=:descripcion,
                    nombre_corto=:nombre_corto,
                    nodo_padre=:nodo_padre
                WHERE id_Area_Administrativa=:id_area
                """
            ),
            {
                "descripcion": description,
                "nombre_corto": short_name,
                "nodo_padre": parent_id,
                "id_area": area_id,
            },
        )
        result = {
            "id_area_administrativa": area_id,
            "descripcion": description,
            "nombre_corto": short_name,
            "nodo_padre": parent_id,
        }
        _audit(
            db,
            "areas_administrativas",
            str(area_id),
            "EDITAR",
            before,
            result,
            identity,
        )
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise


def delete_administrative_area(
    db: Session,
    area_id: int,
    identity: AdminIdentity,
) -> None:
    try:
        before = _get_area(db, area_id)
        if before is None:
            raise AdministrativeAreaNotFound(
                "El área administrativa no existe"
            )
        child = db.execute(
            text(
                "SELECT 1 FROM areas_administrativas "
                "WHERE nodo_padre=:id_area LIMIT 1"
            ),
            {"id_area": area_id},
        ).scalar_one_or_none()
        if child is not None:
            raise ValueError(
                "No se puede eliminar porque tiene ramas hijas. "
                "Elimine primero las ramas dependientes."
            )
        dependency = db.execute(
            text(
                """
                SELECT 'empleado_area'
                FROM empleado_area
                WHERE id_area=:id_area
                UNION ALL
                SELECT 'bitacora_area_observacion'
                FROM bitacora_area_observacion
                WHERE id_area=:id_area
                UNION ALL
                SELECT 'bitacora_area_evidencia'
                FROM bitacora_area_evidencia
                WHERE id_area=:id_area
                LIMIT 1
                """
            ),
            {"id_area": area_id},
        ).scalar_one_or_none()
        if dependency is not None:
            raise ValueError(
                "No se puede eliminar esta área porque está siendo utilizada "
                "por otros registros."
            )
        db.execute(
            text(
                "DELETE FROM areas_administrativas "
                "WHERE id_Area_Administrativa=:id_area"
            ),
            {"id_area": area_id},
        )
        _audit(
            db,
            "areas_administrativas",
            str(area_id),
            "ELIMINAR",
            before,
            None,
            identity,
        )
        db.commit()
    except Exception:
        db.rollback()
        raise


def _get_area(db: Session, area_id: int) -> dict | None:
    row = db.execute(
        text(
            """
            SELECT id_Area_Administrativa AS id_area_administrativa,
                   descripcion, nombre_corto, nodo_padre
            FROM areas_administrativas
            WHERE id_Area_Administrativa=:id_area
            LIMIT 1
            """
        ),
        {"id_area": area_id},
    ).mappings().one_or_none()
    return dict(row) if row else None


def _area_exists(db: Session, area_id: int) -> bool:
    return (
        db.execute(
            text(
                "SELECT 1 FROM areas_administrativas "
                "WHERE id_Area_Administrativa=:id_area LIMIT 1"
            ),
            {"id_area": area_id},
        ).scalar_one_or_none()
        is not None
    )


def _ensure_area_not_duplicate(
    db: Session,
    description: str,
    parent_id: int | None,
    exclude_area_id: int | None = None,
) -> None:
    duplicate = db.execute(
        text(
            """
            SELECT 1
            FROM areas_administrativas
            WHERE UPPER(TRIM(descripcion)) = UPPER(:descripcion)
              AND nodo_padre <=> :nodo_padre
              AND (:excluir IS NULL OR id_Area_Administrativa <> :excluir)
            LIMIT 1
            """
        ),
        {
            "descripcion": description,
            "nodo_padre": parent_id,
            "excluir": exclude_area_id,
        },
    ).scalar_one_or_none()
    if duplicate is not None:
        raise ValueError(
            "Ya existe un área con esa descripción bajo el mismo padre"
        )


def create_employee_area(
    db: Session,
    payload,
    identity: AdminIdentity,
) -> dict:
    validate_assignment_period(payload.fecha_inicia, payload.fecha_final)
    try:
        participant_exists = db.execute(
            text(
                "SELECT 1 FROM participante "
                "WHERE id_participante=:id LIMIT 1"
            ),
            {"id": payload.id_participante},
        ).scalar_one_or_none()
        if participant_exists is None:
            raise ValueError("El participante no existe")

        area_exists = db.execute(
            text(
                "SELECT 1 FROM areas_administrativas "
                "WHERE id_Area_Administrativa=:id LIMIT 1"
            ),
            {"id": payload.id_area},
        ).scalar_one_or_none()
        if area_exists is None:
            raise ValueError("El área no existe")

        type_exists = db.execute(
            text(
                "SELECT 1 FROM tipos_participante "
                "WHERE codigo=:codigo AND activo=TRUE LIMIT 1"
            ),
            {"codigo": payload.codigo_tipo},
        ).scalar_one_or_none()
        if type_exists is None:
            raise ValueError("El tipo no existe o está inactivo")

        overlap = db.execute(
            text(
                """
                SELECT id_empleado_area
                FROM empleado_area
                WHERE id_participante=:id_participante
                  AND id_area=:id_area
                  AND activo=TRUE
                  AND fecha_inicia <= COALESCE(:fecha_final, '9999-12-31')
                  AND COALESCE(fecha_final, '9999-12-31') >= :fecha_inicia
                LIMIT 1
                FOR UPDATE
                """
            ),
            {
                "id_participante": payload.id_participante,
                "id_area": payload.id_area,
                "fecha_inicia": payload.fecha_inicia,
                "fecha_final": payload.fecha_final,
            },
        ).scalar_one_or_none()
        if overlap is not None:
            raise ValueError("La asignación se solapa con otra vigencia")

        inserted = db.execute(
            text(
                """
                INSERT INTO empleado_area
                    (id_participante, id_area, cargo, descripcion,
                     fecha_inicia, fecha_final, activo)
                VALUES
                    (:id_participante, :id_area, :cargo, :descripcion,
                     :fecha_inicia, :fecha_final, TRUE)
                """
            ),
            {
                "id_participante": payload.id_participante,
                "id_area": payload.id_area,
                "cargo": payload.codigo_tipo,
                "descripcion": payload.descripcion,
                "fecha_inicia": payload.fecha_inicia,
                "fecha_final": payload.fecha_final,
            },
        )
        assignment_id = int(inserted.lastrowid)
        result = {
            **payload.model_dump(),
            "id_empleado_area": assignment_id,
            "creado_en": None,
        }
        _audit(
            db,
            catalog="empleado_area",
            key=str(assignment_id),
            operation="CREAR",
            before=None,
            after=result,
            identity=identity,
        )
        db.commit()
        return result
    except Exception:
        db.rollback()
        raise


def list_employee_area_tree(db: Session) -> list[dict]:
    area_rows = list_area_tree(db)
    participant_table = settings.PARTICIPANTE_TABLE
    assignments = db.execute(
        text(
            f"""
            SELECT ea.id_empleado_area, ea.id_participante, ea.id_area,
                   p.identificacion_participante AS codigo_participante,
                   TRIM(CONCAT_WS(' ', p.nombre, p.apellido)) AS nombre_completo,
                   ea.cargo AS codigo_tipo, tp.descripcion AS cargo,
                   ea.descripcion, ea.fecha_inicia, ea.fecha_final
            FROM empleado_area AS ea
            JOIN {participant_table} AS p
              ON p.id_participante = ea.id_participante
            LEFT JOIN tipos_participante AS tp
              ON tp.codigo = ea.cargo
            WHERE ea.activo = TRUE
              AND ea.fecha_inicia <= CURDATE()
              AND (ea.fecha_final IS NULL OR ea.fecha_final >= CURDATE())
            ORDER BY p.apellido, p.nombre, p.id_participante
            """
        )
    ).mappings().all()
    assignments_by_area: dict[int, list[dict]] = {}
    for row in assignments:
        assignments_by_area.setdefault(int(row["id_area"]), []).append(dict(row))

    nodes: dict[int, dict] = {}
    ordered_nodes: list[dict] = []
    for row in area_rows:
        area_id = int(row["id_area"])
        participants = assignments_by_area.get(area_id, [])
        node = {
            "id_area": area_id,
            "descripcion": row["descripcion"],
            "nombre_corto": row["nombre_corto"],
            "nodo_padre": row["id_padre"],
            "nivel": int(row["nivel"]),
            "ruta": row["ruta"],
            "cantidad_participantes": len(participants),
            "participantes": participants,
            "hijos": [],
        }
        nodes[area_id] = node
        ordered_nodes.append(node)

    roots: list[dict] = []
    for node in ordered_nodes:
        parent_id = node["nodo_padre"]
        if parent_id not in (None, 0) and parent_id in nodes:
            nodes[parent_id]["hijos"].append(node)
        else:
            roots.append(node)
    return roots


def list_participant_options(db: Session, search: str) -> list[dict]:
    participant_table = settings.PARTICIPANTE_TABLE
    normalized = " ".join(search.strip().upper().split())
    pattern = f"%{normalized}%"
    rows = db.execute(
        text(
            f"""
            SELECT id_participante,
                   identificacion_participante AS codigo,
                   nombre AS nombres,
                   apellido AS apellidos,
                   TRIM(CONCAT_WS(' ', nombre, apellido)) AS nombre_completo,
                   documento
            FROM {participant_table}
            WHERE (fecha_salida IS NULL OR fecha_salida < fecha_entrada)
              AND (
                  :search = ''
                  OR UPPER(identificacion_participante) LIKE :pattern
                  OR UPPER(COALESCE(documento, '')) LIKE :pattern
                  OR UPPER(nombre) LIKE :pattern
                  OR UPPER(apellido) LIKE :pattern
                  OR UPPER(CONCAT_WS(' ', nombre, apellido)) LIKE :pattern
              )
            ORDER BY apellido, nombre, id_participante
            LIMIT 100
            """
        ),
        {"search": normalized, "pattern": pattern},
    ).mappings().all()
    return [dict(row) for row in rows]


def update_employee_area(
    db: Session,
    assignment_id: int,
    payload,
    identity: AdminIdentity,
) -> dict:
    validate_assignment_period(payload.fecha_inicia, payload.fecha_final)
    try:
        before = _employee_area_for_update(db, assignment_id)
        if before is None:
            raise AdministrativeAreaNotFound("La asignación no existe")
        if not _area_exists(db, payload.id_area):
            raise ValueError("El área no existe")
        type_exists = db.execute(
            text(
                "SELECT 1 FROM tipos_participante "
                "WHERE codigo=:codigo AND activo=TRUE LIMIT 1"
            ),
            {"codigo": payload.codigo_tipo},
        ).scalar_one_or_none()
        if type_exists is None:
            raise ValueError("El tipo no existe o está inactivo")
        overlap = db.execute(
            text(
                """
                SELECT id_empleado_area
                FROM empleado_area
                WHERE id_participante=:id_participante
                  AND id_area=:id_area
                  AND activo=TRUE
                  AND id_empleado_area<>:id_asignacion
                  AND fecha_inicia <= COALESCE(:fecha_final, '9999-12-31')
                  AND COALESCE(fecha_final, '9999-12-31') >= :fecha_inicia
                LIMIT 1 FOR UPDATE
                """
            ),
            {
                "id_participante": before["id_participante"],
                "id_area": payload.id_area,
                "id_asignacion": assignment_id,
                "fecha_inicia": payload.fecha_inicia,
                "fecha_final": payload.fecha_final,
            },
        ).scalar_one_or_none()
        if overlap is not None:
            raise ValueError(
                "El participante ya tiene una asignación vigente en esta área."
            )
        db.execute(
            text(
                """
                UPDATE empleado_area
                SET id_area=:id_area, cargo=:cargo, descripcion=:descripcion,
                    fecha_inicia=:fecha_inicia, fecha_final=:fecha_final
                WHERE id_empleado_area=:id_asignacion
                """
            ),
            {
                "id_area": payload.id_area,
                "cargo": payload.codigo_tipo,
                "descripcion": payload.descripcion,
                "fecha_inicia": payload.fecha_inicia,
                "fecha_final": payload.fecha_final,
                "id_asignacion": assignment_id,
            },
        )
        after = _employee_area_result(db, assignment_id)
        _audit(
            db, "empleado_area", str(assignment_id), "EDITAR",
            before, after, identity,
        )
        db.commit()
        return after
    except Exception:
        db.rollback()
        raise


def retire_employee_area(
    db: Session,
    assignment_id: int,
    identity: AdminIdentity,
) -> None:
    try:
        before = _employee_area_for_update(db, assignment_id)
        if before is None:
            raise AdministrativeAreaNotFound("La asignación no existe")
        if not before["activo"]:
            raise ValueError("La asignación ya está retirada")
        db.execute(
            text(
                """
                UPDATE empleado_area
                SET activo=FALSE,
                    fecha_final=LEAST(COALESCE(fecha_final, CURDATE()), CURDATE())
                WHERE id_empleado_area=:id_asignacion
                """
            ),
            {"id_asignacion": assignment_id},
        )
        after = {**before, "activo": False}
        _audit(
            db, "empleado_area", str(assignment_id), "DESACTIVAR",
            before, after, identity,
        )
        db.commit()
    except Exception:
        db.rollback()
        raise


def _employee_area_for_update(db: Session, assignment_id: int) -> dict | None:
    row = db.execute(
        text(
            """
            SELECT id_empleado_area, id_participante, id_area,
                   cargo AS codigo_tipo, descripcion, fecha_inicia,
                   fecha_final, activo
            FROM empleado_area
            WHERE id_empleado_area=:id_asignacion
            LIMIT 1 FOR UPDATE
            """
        ),
        {"id_asignacion": assignment_id},
    ).mappings().first()
    return dict(row) if row else None


def _employee_area_result(db: Session, assignment_id: int) -> dict:
    row = db.execute(
        text(
            """
            SELECT ea.id_empleado_area, ea.id_participante,
                   p.identificacion_participante AS codigo_participante,
                   TRIM(CONCAT_WS(' ', p.nombre, p.apellido)) AS nombre_completo,
                   ea.cargo AS codigo_tipo, tp.descripcion AS cargo,
                   ea.descripcion, ea.fecha_inicia, ea.fecha_final
            FROM empleado_area ea
            JOIN participante p ON p.id_participante=ea.id_participante
            LEFT JOIN tipos_participante tp ON tp.codigo=ea.cargo
            WHERE ea.id_empleado_area=:id_asignacion
            """
        ),
        {"id_asignacion": assignment_id},
    ).mappings().one()
    return dict(row)


def _participant_type_for_update(db: Session, type_code: int) -> dict | None:
    row = db.execute(
        text(
            """
            SELECT tp.codigo, tp.descripcion, tp.activo,
                   GROUP_CONCAT(tpc.codigo_capacidad
                       ORDER BY tpc.codigo_capacidad) AS capacidades
            FROM tipos_participante AS tp
            LEFT JOIN tipo_participante_capacidad AS tpc
              ON tpc.codigo_tipo=tp.codigo
            WHERE tp.codigo=:codigo
            GROUP BY tp.codigo, tp.descripcion, tp.activo
            FOR UPDATE
            """
        ),
        {"codigo": type_code},
    ).mappings().first()
    if row is None:
        return None
    return {
        "codigo": int(row["codigo"]),
        "descripcion": row["descripcion"],
        "activo": bool(row["activo"]),
        "capacidades": (
            row["capacidades"].split(",") if row["capacidades"] else []
        ),
    }


def _audit(
    db: Session,
    catalog: str,
    key: str,
    operation: str,
    before: dict | None,
    after: dict | None,
    identity: AdminIdentity,
) -> None:
    db.execute(
        text(
            """
            INSERT INTO administracion_catalogo_auditoria
                (catalogo, clave_registro, operacion, valor_anterior,
                 valor_nuevo, actor, dispositivo)
            VALUES
                (:catalogo, :clave, :operacion, :anterior,
                 :nuevo, :actor, :dispositivo)
            """
        ),
        {
            "catalogo": catalog,
            "clave": key,
            "operacion": operation,
            "anterior": json.dumps(before, default=str) if before else None,
            "nuevo": json.dumps(after, default=str) if after else None,
            "actor": identity.actor,
            "dispositivo": identity.device,
        },
    )
