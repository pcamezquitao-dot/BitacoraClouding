import unittest
from unittest.mock import MagicMock, patch

from app.routers.bitacora_uc03 import _crear_bitacora_diaria


class BitacoraIdempotencyTest(unittest.TestCase):
    def test_reintento_con_client_uuid_devuelve_registro_existente(self):
        db = MagicMock()
        existing = {
            "id_bitacora": 77,
            "id_empleado": 2,
            "id_supervisor": 3,
            "ts_in_min": 100,
            "ts_out_min": None,
            "tipo_anotacion": None,
            "observaciones": "offline",
        }
        with patch(
            "app.routers.bitacora_uc03._get_bitacora_by_client_uuid",
            return_value=existing,
        ), patch(
            "app.routers.bitacora_uc03.require_asignacion_activa"
        ) as validate:
            result = _crear_bitacora_diaria(
                db,
                id_empleado=2,
                id_supervisor=3,
                ts_in_min=100,
                ts_out_min=None,
                tipo_anotacion=None,
                observaciones="offline",
                client_uuid="stable-client-uuid",
            )

        self.assertEqual(77, result.id_bitacora)
        validate.assert_not_called()
        db.execute.assert_not_called()


if __name__ == "__main__":
    unittest.main()
