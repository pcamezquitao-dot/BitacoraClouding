import unittest
from unittest.mock import MagicMock

from app.routers.participante import (
    get_participante_by_qr,
    normalize_participant_code,
    search_participantes,
)


class ParticipanteSearchTest(unittest.TestCase):
    def test_busqueda_vacia_no_consulta_base_de_datos(self):
        db = MagicMock()

        self.assertEqual([], search_participantes("   ", db))
        db.execute.assert_not_called()

    def test_busca_por_codigo_o_nombre_y_retorna_resultados(self):
        expected = [
            {
                "id_participante": 12,
                "nombre": "Ana",
                "apellido": "Prueba",
                "identificacion_participante": "EMP-12",
            }
        ]
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = expected

        result = search_participantes("Ana", db)

        self.assertEqual(expected, result)
        parameters = db.execute.call_args.args[1]
        self.assertEqual("%ANA%", parameters["pattern"])

    def test_normaliza_codigos_alfanumericos_sin_perder_ceros(self):
        for value in ("P0002", "p0002", " P0002 "):
            with self.subTest(value=value):
                self.assertEqual("P0002", normalize_participant_code(value))

    def test_busca_p0002_como_codigo_exacto(self):
        expected = [{
            "id_participante": 2,
            "nombre": "Nombre2",
            "apellido": "Apellido2",
            "identificacion_participante": "P0002",
        }]
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = expected

        self.assertEqual(expected, search_participantes(" p0002 ", db))
        parameters = db.execute.call_args.args[1]
        self.assertEqual("P0002", parameters["exact_code"])
        self.assertEqual("%P0002%", parameters["pattern"])

    def test_codigo_inexistente_retorna_lista_vacia(self):
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = []

        self.assertEqual([], search_participantes("NO-EXISTE", db))

    def test_qr_normaliza_antes_de_consultar(self):
        db = MagicMock()
        db.execute.return_value.mappings.return_value.first.return_value = {
            "id_participante": 2,
            "nombre": "Nombre2",
            "apellido": "Apellido2",
            "identificacion_participante": "P0002",
        }

        get_participante_by_qr(" p0002 ", db)

        self.assertEqual(
            "P0002",
            db.execute.call_args.args[1]["qr"],
        )


if __name__ == "__main__":
    unittest.main()
