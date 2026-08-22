from unittest.mock import MagicMock, patch

from app.routers.bitacora_uc03 import _crear_bitacora_diaria


def test_endpoint_oficial_acepta_origen_prueba_sin_ruta_paralela():
    db = MagicMock()
    inserted = MagicMock()
    inserted.lastrowid = 901
    db.execute.return_value = inserted

    with patch(
        "app.routers.bitacora_uc03._get_bitacora_by_client_uuid",
        return_value=None,
    ), patch(
        "app.routers.bitacora_uc03.require_asignacion_activa"
    ):
        result = _crear_bitacora_diaria(
            db=db,
            id_empleado=15,
            id_supervisor=2,
            ts_in_min=29_782_500,
            ts_out_min=None,
            tipo_anotacion=4,
            observaciones="DATOS_PRUEBA_P0015",
            client_uuid="c22-test-uuid",
            origen_bitacora="PRUEBA",
        )

    assert result.id_bitacora == 901
    assert result.origen_bitacora == "PRUEBA"
    inserted_parameters = db.execute.call_args.args[1]
    assert inserted_parameters["origen_bitacora"] == "PRUEBA"
    assert inserted_parameters["observaciones"] == "DATOS_PRUEBA_P0015"
