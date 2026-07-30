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


if __name__ == "__main__":
    unittest.main()
