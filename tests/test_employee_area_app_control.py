from fastapi import HTTPException

from app.main import app
from app.routers import admin_catalog, employee_area


def test_employee_area_routes_are_registered_without_legacy_token_dependency():
    paths = app.openapi()["paths"]

    assert "/admin/empleado-area/tree" in paths
    assert "/admin/empleado-area/tipos-participante" in paths
    assert "/admin/participantes/options" in paths
    assert "/admin/empleado-area" in paths
    assert "/admin/empleado-area/{id_asignacion}" in paths
    assert employee_area.router.dependencies == []


def test_other_admin_catalog_routes_keep_existing_server_protection():
    assert admin_catalog.router.dependencies


def test_employee_area_write_identity_requires_identified_administrator():
    try:
        employee_area.employee_admin_identity(actor="  ", device=None)
    except HTTPException as error:
        assert error.status_code == 422
        assert error.detail == "Identifique al administrador"
    else:
        raise AssertionError("La escritura acepto un administrador vacio")


def test_employee_area_write_identity_normalizes_audit_headers():
    identity = employee_area.employee_admin_identity(
        actor="  Patricia  ",
        device="  telefono-prueba  ",
    )

    assert identity.actor == "Patricia"
    assert identity.device == "telefono-prueba"
