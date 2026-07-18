import unittest

from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.core.config import settings
from app.core.face_auth import require_face_sync_access


class FaceAuthTest(unittest.TestCase):
    def setUp(self):
        self.previous = settings.FACE_TEMPLATE_API_TOKEN
        settings.FACE_TEMPLATE_API_TOKEN = "test-token"

    def tearDown(self):
        settings.FACE_TEMPLATE_API_TOKEN = self.previous

    def test_autoriza_bearer_configurado(self):
        credentials = HTTPAuthorizationCredentials(
            scheme="Bearer",
            credentials="test-token",
        )
        self.assertEqual("test-token", require_face_sync_access(credentials))

    def test_rechaza_descarga_sin_credenciales(self):
        with self.assertRaises(HTTPException) as raised:
            require_face_sync_access(None)
        self.assertEqual(401, raised.exception.status_code)


if __name__ == "__main__":
    unittest.main()
