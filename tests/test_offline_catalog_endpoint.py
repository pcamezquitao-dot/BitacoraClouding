import unittest
from unittest.mock import MagicMock, patch

from app.main import app
from app.routers.catalogos import catalogos_offline


class OfflineCatalogEndpointTest(unittest.TestCase):
    def test_openapi_expone_un_solo_endpoint_de_catalogos(self):
        schema = app.openapi()
        self.assertIn("/catalogos/offline", schema["paths"])
        self.assertIn("get", schema["paths"]["/catalogos/offline"])

    def test_catalogo_retorna_tres_colecciones_sin_escribir(self):
        db = MagicMock()
        db.bind = None
        participant_result = MagicMock()
        participant_result.mappings.return_value.all.return_value = [
            {
                "id_participante": 2,
                "identificacion_participante": "P0002",
                "nombre": "Ana",
                "apellido": "Prueba",
                "documento": None,
                "activo": True,
                "updated_at": None,
            }
        ]
        area_result = MagicMock()
        area_result.mappings.return_value.all.return_value = [
            {
                "id_area": 3,
                "descripcion": "Administración",
                "activo": True,
                "updated_at": None,
            }
        ]
        assignment_result = MagicMock()
        assignment_result.mappings.return_value.all.return_value = [
            {
                "id_participante": 2,
                "id_area": 3,
                "cargo": 1,
                "fecha_final": None,
                "activo": True,
                "updated_at": None,
            }
        ]
        db.execute.side_effect = [participant_result, area_result, assignment_result]

        with patch(
            "app.routers.catalogos.participant_document_expression",
            return_value="NULL",
        ):
            result = catalogos_offline(db)

        self.assertEqual("P0002", result["participantes"][0]["identificacion_participante"])
        self.assertEqual(3, result["areas"][0]["id_area"])
        self.assertEqual(2, result["empleado_areas"][0]["id_participante"])
        self.assertEqual(3, db.execute.call_count)
        db.commit.assert_not_called()

        assignment_sql = str(db.execute.call_args_list[2].args[0]).upper()
        self.assertIn("FECHA_FINAL IS NULL", assignment_sql)
        self.assertNotIn("GROUP BY", assignment_sql)
