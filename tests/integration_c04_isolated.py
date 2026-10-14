"""Prueba destructiva controlada, solo para bitacora_c04_test."""
from datetime import date

from sqlalchemy import text

from app.core.admin_auth import AdminIdentity
from app.core.db import SessionLocal
from app.schemas.admin_catalog import EmployeeAreaCreate
from app.services.admin_catalog_service import (
    create_employee_area,
    list_participant_options,
    retire_employee_area,
)


db = SessionLocal()
try:
    database = db.execute(text("SELECT DATABASE()" )).scalar_one()
    assert database == "bitacora_c04_test", database
    expected = db.execute(text("""
        SELECT COUNT(*) FROM participante p
        WHERE (p.fecha_salida IS NULL OR p.fecha_salida < p.fecha_entrada)
          AND NOT EXISTS (
            SELECT 1 FROM empleado_area ea
            WHERE ea.id_participante=p.id_participante
              AND ea.activo=TRUE AND ea.fecha_inicia<=CURDATE()
              AND (ea.fecha_final IS NULL OR ea.fecha_final>=CURDATE()))
    """)).scalar_one()
    options = list_participant_options(db, "")
    assert len(options) == expected, (len(options), expected)
    codes = [row["codigo"] for row in options]
    assert "P0013" in codes
    search = list_participant_options(db, "P0013")
    assert [row["codigo"] for row in search] == ["P0013"]
    historical = db.execute(text("""
        SELECT p.id_participante, p.identificacion_participante
        FROM participante p
        JOIN empleado_area h ON h.id_participante=p.id_participante
        WHERE (h.activo=FALSE OR h.fecha_inicia>CURDATE() OR h.fecha_final<CURDATE())
          AND NOT EXISTS (
            SELECT 1 FROM empleado_area a
            WHERE a.id_participante=p.id_participante
              AND a.activo=TRUE AND a.fecha_inicia<=CURDATE()
              AND (a.fecha_final IS NULL OR a.fecha_final>=CURDATE()))
        LIMIT 1
    """)).mappings().first()
    assert historical and historical["identificacion_participante"] in codes
    candidate = options[0]
    area_ids = [row[0] for row in db.execute(text(
        "SELECT id_Area_Administrativa FROM areas_administrativas "
        "ORDER BY id_Area_Administrativa LIMIT 2"
    )).all()]
    type_code = db.execute(text(
        "SELECT codigo FROM tipos_participante WHERE activo=TRUE "
        "ORDER BY codigo LIMIT 1"
    )).scalar_one()
    schedule = db.execute(text(
        "SELECT id_jornada FROM jornadas_de_trabajo WHERE activo=TRUE "
        "AND vigencia_desde<=CURDATE() "
        "AND (vigencia_hasta IS NULL OR vigencia_hasta>=CURDATE()) "
        "ORDER BY id_jornada LIMIT 1"
    )).scalar_one()
    payload = EmployeeAreaCreate(
        id_participante=candidate["id_participante"], id_area=area_ids[0],
        codigo_tipo=type_code, descripcion="CP-C04 aislado",
        fecha_inicia=date.today(), fecha_final=None, id_jornada=schedule,
    )
    created = create_employee_area(db, payload, AdminIdentity("cp-c04", "aislado"))
    assert candidate["codigo"] not in {
        row["codigo"] for row in list_participant_options(db, "")
    }
    conflicting = payload.model_copy(update={
        "id_area": area_ids[1], "descripcion": "conflicto"
    })
    try:
        create_employee_area(db, conflicting, AdminIdentity("cp-c04", "aislado"))
    except ValueError as error:
        assert "activa incompatible" in str(error)
    else:
        raise AssertionError("La revalidación permitió una asignación incompatible")
    retire_employee_area(
        db, created["id_empleado_area"], AdminIdentity("cp-c04", "aislado")
    )
    assert candidate["codigo"] in {
        row["codigo"] for row in list_participant_options(db, "")
    }
    print(f"DATABASE={database}")
    print(f"EXPECTED={expected};OBTAINED={len(options)}")
    print(f"POST_P0012={sum(code > 'P0012' for code in codes)}")
    print(f"HISTORICAL_INCLUDED={historical['identificacion_participante']}")
    print(f"SEARCH_P0013={len(search)}")
    print("CREATE_REFRESH=OK;CONFLICT_REVALIDATION=OK;RETIRE_REFRESH=OK")
finally:
    db.close()
