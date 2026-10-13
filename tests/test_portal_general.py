from unittest.mock import Mock, patch

import pytest

from app.services.portal_general_service import (
    PortalConfigurationError, PortalForbidden, _item, authorize_item, load_portal,
)


PARTICIPANT = {
    "id_participante": 93401, "codigo": "CP-C34-ACTIVO",
    "nombre_completo": "Participante C34", "tipo_codigo": 9341,
    "tipo_descripcion": "CP_C34_ROL",
}
AVAILABLE = {
    "id_accion": 93411, "nombre": "Proceso disponible", "descripcion": None,
    "id_proceso": 93421, "nombre_proceso": "CP_C34_PROCESO",
    "id_bpm_proceso": 93431, "nombre_bpm": "CP_C34_BPM",
    "bpm_activo": True, "estado_destino": "DISPONIBLE",
}


def test_portal_reports_missing_duplicate_and_empty_root_without_hiding_tasks_entry():
    db = Mock()
    with patch("app.services.portal_general_service._participant", return_value=PARTICIPANT), patch(
        "app.services.portal_general_service._roots", return_value=[]
    ):
        assert load_portal(db, "CP-C34-ACTIVO")["estado_menu"] == "RAIZ_AUSENTE"
    with patch("app.services.portal_general_service._participant", return_value=PARTICIPANT), patch(
        "app.services.portal_general_service._roots", return_value=[{"id_accion": 1}, {"id_accion": 2}]
    ):
        assert load_portal(db, "CP-C34-ACTIVO")["estado_menu"] == "RAIZ_DUPLICADA"
    with patch("app.services.portal_general_service._participant", return_value=PARTICIPANT), patch(
        "app.services.portal_general_service._roots", return_value=[{"id_accion": 1}]
    ), patch("app.services.portal_general_service._children", return_value=[]):
        assert load_portal(db, "CP-C34-ACTIVO")["estado_menu"] == "SIN_HIJOS"


def test_open_option_revalidates_identity_membership_and_destination():
    db = Mock()
    with patch("app.services.portal_general_service.load_portal", return_value={"opciones": [AVAILABLE]}):
        assert authorize_item(db, "CP-C34-ACTIVO", 93411) == AVAILABLE
        with pytest.raises(PortalForbidden):
            authorize_item(db, "CP-C34-ACTIVO", 99999)
    invalid = {**AVAILABLE, "estado_destino": "SIN_FLUJO_BPM", "id_bpm_proceso": None}
    with patch("app.services.portal_general_service.load_portal", return_value={"opciones": [invalid]}):
        with pytest.raises(PortalConfigurationError, match="no tiene flujo BPM"):
            authorize_item(db, "CP-C34-ACTIVO", 93411)


def test_contract_uses_real_relationships_and_read_only_queries():
    source = __import__("pathlib").Path("app/services/portal_general_service.py").read_text(encoding="utf-8")
    assert "FROM dim_accion" in source
    assert "LEFT JOIN bpm_etapa be ON be.id_etapa=p.id_proceso" in source
    assert "bp.id_bpm_proceso=be.id_bpm_proceso" in source
    assert "INSERT " not in source and "UPDATE " not in source and "DELETE " not in source
    assert "La opción no pertenece al portal del participante" in source


def test_router_is_registered_and_has_no_case_creation_side_effect():
    main = __import__("pathlib").Path("app/main.py").read_text(encoding="utf-8")
    router = __import__("pathlib").Path("app/routers/portal_general.py").read_text(encoding="utf-8")
    assert "portal_general_router" in main
    assert '@router.post("/identificar"' in router
    assert '@router.get("/{codigo}/opciones/{id_accion}"' in router
    assert "bpm_caso" not in router


@pytest.mark.parametrize(("changes", "expected"), [
    ({"id_proceso": None}, "SIN_PROCESO"),
    ({"nombre_proceso": None}, "PROCESO_INEXISTENTE"),
    ({"bpm_ids": None, "bpm_names": None}, "SIN_FLUJO_BPM"),
    ({"bpm_ids": "93431,93432", "bpm_names": "Uno||Dos"}, "FLUJO_BPM_AMBIGUO"),
    ({"bpm_activo": 0}, "FLUJO_BPM_INACTIVO"),
    ({}, "DISPONIBLE"),
])
def test_destination_states_are_explicit(changes, expected):
    row = {
        "id_accion": 93411, "nombre": "Destino", "descripcion": None,
        "id_proceso": 93421, "nombre_proceso": "Proceso",
        "bpm_ids": "93431", "bpm_names": "Flujo", "bpm_activo": 1,
    }
    assert _item({**row, **changes})["estado_destino"] == expected


def test_android_rejects_blank_code_and_clears_previous_identity_before_reload():
    source = __import__("pathlib").Path(
        "android/bitacora-android/app/src/main/java/com/cactus/bitacora/PortalGeneralScreen.kt"
    ).read_text(encoding="utf-8")
    assert "requested.isBlank()" in source
    clear = source.index("portal = null")
    request = source.index("repository.identifyPortal")
    assert clear < request

