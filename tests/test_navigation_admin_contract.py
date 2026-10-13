from pathlib import Path
from unittest.mock import patch

import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.core.admin_auth import require_admin_access
from app.schemas.navigation_admin import NavigationWrite


def test_navigation_payload_normalizes_nulls_and_limits():
    value = NavigationWrite(nombre="  Acción  ", descripcion="   ", request_id="request-001")
    assert value.nombre == "Acción"
    assert value.descripcion is None
    assert value.tipo_accion == 1
    assert value.id_accion_padre is None
    assert value.id_proceso is None


def test_admin_access_requires_technical_token_and_shared_key():
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials="technical")
    with patch("app.core.admin_auth.settings.ADMIN_API_TOKEN", "technical"), patch(
        "app.core.admin_auth.settings.ADMIN_ACCESS_KEY", "clave-configurada"
    ):
        identity = require_admin_access(credentials, "auditor", "V2035", "clave-configurada")
        assert identity.actor == "auditor"
        for missing_or_wrong in (None, "0000"):
            with pytest.raises(HTTPException) as error:
                require_admin_access(credentials, "auditor", "V2035", missing_or_wrong)
            assert error.value.status_code == 401


def test_router_and_service_preserve_authorization_audit_and_integrity():
    router = Path("app/routers/navigation_admin.py").read_text(encoding="utf-8")
    service = Path("app/services/navigation_admin_service.py").read_text(encoding="utf-8")
    assert "dependencies=[Depends(require_admin_access)]" in router
    assert router.count("Depends(require_admin_access)") >= 4
    assert '_audit(db, "dim_accion"' in service
    assert "WITH RECURSIVE descendants" in service
    assert "administracion_catalogo_auditoria" in service
    assert "DELETE FROM dim_accion WHERE id_accion=:id" in service
    assert "FOR UPDATE" in service
    assert "DELETE FROM dim_proceso" not in service


def test_main_registers_navigation_and_access_routers():
    source = Path("app/main.py").read_text(encoding="utf-8")
    assert "navigation_admin_router" in source
    assert "admin_access_router" in source


def test_android_reports_process_failure_without_discarding_cached_catalog():
    source = Path(
        "android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/admin/NavigationAdminPanel.kt"
    ).read_text(encoding="utf-8")
    assert "var processError" in source
    assert "onSuccess { processes = it; processError = null }" in source
    assert "se conserva la asociación vigente" in source
    assert ".getOrElse { processes }" not in source
