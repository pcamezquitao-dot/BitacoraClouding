from pathlib import Path
import unittest

from app.main import app
from app.schemas.bitacora import BitacoraDiariaCreate


class SatelitalContractTest(unittest.TestCase):
    def test_publica_catalogo_de_objetos_activos(self):
        paths = app.openapi()["paths"]
        self.assertIn("/satelital/objetos", paths)
        self.assertIn("get", paths["/satelital/objetos"])
        self.assertIn("/satelital/embalses", paths)
        self.assertIn("/satelital/embalses/{id_embalse}", paths)
        self.assertIn("/satelital/embalses/{id_embalse}/imagenes", paths)
        self.assertIn("/satelital/imagenes/{id_imagen}", paths)

    def test_payload_manual_conserva_valores_anteriores(self):
        payload = BitacoraDiariaCreate(id_empleado=1)
        self.assertEqual("MANUAL", payload.origen_bitacora)
        self.assertIsNone(payload.id_objeto_monitoreo)
        self.assertIsNone(payload.tipo_seguimiento_satelital)

    def test_migracion_es_aditiva_y_registra_catalogos(self):
        sql = Path("migrations/20260729_bitacora_satelital.sql").read_text(
            encoding="utf-8"
        ).upper()
        self.assertIn("CREATE TABLE IF NOT EXISTS OBJETO_MONITOREO_SATELITAL", sql)
        self.assertIn("'EMBALSE LA COPA'", sql)
        self.assertIn("'SATELITAL'", sql)
        self.assertIn("ID_OBJETO_MONITOREO INT UNSIGNED NULL", sql)
        self.assertIn("DEFAULT 'MANUAL'", sql)
        self.assertNotIn("DROP TABLE", sql)
        self.assertNotIn("TRUNCATE", sql)

    def test_imagenes_guardan_ruta_y_no_archivo_en_mariadb(self):
        sql = Path("migrations/20260729_imagenes_embalses.sql").read_text(
            encoding="utf-8"
        ).upper()
        self.assertIn("CREATE TABLE IF NOT EXISTS IMAGEN_SATELITAL_EMBALSE", sql)
        self.assertIn("ARCHIVO_RUTA VARCHAR", sql)
        self.assertNotIn(" BLOB", sql)
        self.assertNotIn(" LONGBLOB", sql)

    def test_iteracion_uno_registra_seis_embalses_sin_inventar_poligonos(self):
        sql = Path("migrations/20260730_embalses_iteracion1.sql").read_text(
            encoding="utf-8"
        ).upper()
        for reservoir in (
            "EMBALSE LA COPA",
            "EMBALSE DE TOMINÉ",
            "EMBALSE DEL SISGA",
            "EMBALSE DE SAN RAFAEL",
            "EMBALSE DE CHUZA",
            "EMBALSE DEL NEUSA",
        ):
            self.assertIn(reservoir, sql)
        self.assertIn("OPENSTREETMAP", sql)
        self.assertIn("EPSG:4326", sql)
        self.assertIn("POLÍGONO ÍNTEGRO PENDIENTE", sql)
        self.assertNotIn("DROP TABLE", sql)

    def test_iteracion_uno_tiene_reversion_explicita(self):
        rollback = Path(
            "migrations/20260730_embalses_iteracion1_rollback.sql"
        ).read_text(encoding="utf-8").upper()
        self.assertIn("DELETE FROM OBJETO_MONITOREO_SATELITAL", rollback)
        self.assertIn("DROP COLUMN IF EXISTS", rollback)


if __name__ == "__main__":
    unittest.main()
