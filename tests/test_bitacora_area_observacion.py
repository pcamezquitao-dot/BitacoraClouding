import unittest
from unittest.mock import MagicMock, patch

from app.routers.bitacora_uc03 import _crear_observacion_area_si_aplica


def _mapping_result(value):
    result = MagicMock()
    result.mappings.return_value.first.return_value = value
    return result


class BitacoraAreaObservacionTest(unittest.TestCase):
    def test_inserta_observacion_con_id_area_del_qr_e_id_bitacora(self):
        db = MagicMock()
        area_result = _mapping_result({"id_area": 2, "descripcion": "Finca 1"})
        bitacora_result = _mapping_result({"id_bitacora": 91})
        no_existing_observation = MagicMock()
        no_existing_observation.first.return_value = None
        insert_result = MagicMock()
        db.execute.side_effect = [
            area_result,
            bitacora_result,
            no_existing_observation,
            insert_result,
        ]

        with patch(
            "app.routers.bitacora_uc03.require_asignacion_activa",
            return_value={"id_area": 2},
        ):
            result = _crear_observacion_area_si_aplica(
                db=db,
                id_bitacora=91,
                id_empleado=10,
                id_supervisor=20,
                ts_in_min=123456,
                qr_area="AREA_ADMINISTRATIVA|2|Finca 1",
                observaciones="Prueba",
            )

        self.assertEqual(result["id_area"], 2)
        insert_sql = str(db.execute.call_args_list[3].args[0])
        insert_params = db.execute.call_args_list[3].args[1]
        self.assertIn("INSERT INTO bitacora_area_observacion", insert_sql)
        self.assertIn("id_bitacora", insert_sql)
        self.assertEqual(insert_params, {
            "e": 10,
            "s": 20,
            "t": 123456,
            "id_area": 2,
            "obs": "Prueba",
            "id_bitacora": 91,
        })
