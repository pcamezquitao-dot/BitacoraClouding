import unittest

from app.core.config import settings
from app.main import app
from app.services.evidencia_file_service import TIPOS


class SyncApiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.openapi = app.openapi()

    def test_sync_routes_are_exposed_by_local_backend(self):
        paths = self.openapi["paths"]
        expected = {
            "/bitacora_diaria",
            "/bitacora-area-evidencias/upload",
            "/face-templates/enroll",
            "/face-templates/authorized/active",
            "/face-templates/sync/status",
        }
        self.assertTrue(expected.issubset(paths))

    def test_central_tables_and_database_defaults_are_definitive(self):
        self.assertEqual("bitacora", settings.DB_NAME)
        self.assertEqual("bitacora_diaria", settings.BITACORA_DIARIA_TABLE)
        self.assertEqual("bitacora_area_evidencia", settings.BAE_TABLE)
        self.assertEqual("participante_face_template", settings.FACE_TEMPLATE_TABLE)

    def test_evidence_limits_match_nginx_documented_capacity(self):
        self.assertEqual(15 * 1024 * 1024, settings.EVIDENCIA_FOTO_MAX_BYTES)
        self.assertEqual(50 * 1024 * 1024, settings.EVIDENCIA_AUDIO_MAX_BYTES)
        self.assertEqual(200 * 1024 * 1024, settings.EVIDENCIA_VIDEO_MAX_BYTES)
        self.assertIn("audio/3gpp", TIPOS[2]["mimes"])


if __name__ == "__main__":
    unittest.main()
