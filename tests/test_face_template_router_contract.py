import unittest

from app.main import app
from app.routers.face_templates import create_templates_router, router


class FaceTemplateRouterContractTest(unittest.TestCase):
    def test_factory_compatibility_returns_existing_router(self):
        self.assertIs(router, create_templates_router())

    def test_existing_face_endpoints_remain_published(self):
        paths = app.openapi()["paths"]
        expected = {
            "/face-templates": "post",
            "/face-templates/enroll": "post",
            "/face-templates/by-participante/{id_participante}": "get",
            "/face-templates/sync": "get",
            "/face-templates/participant/{id_participante}/active": "get",
            "/face-templates/authorized/active": "get",
            "/face-templates/{id_face_template}/deactivate": "patch",
        }
        for path, method in expected.items():
            with self.subTest(path=path, method=method):
                self.assertIn(path, paths)
                self.assertIn(method, paths[path])

    def test_bitacora_and_evidence_routes_remain_published(self):
        paths = app.openapi()["paths"]
        self.assertIn("/bitacora_diaria", paths)
        self.assertIn("post", paths["/bitacora_diaria"])
        self.assertIn("/bitacora-area-evidencias/upload", paths)
        self.assertIn("post", paths["/bitacora-area-evidencias/upload"])


if __name__ == "__main__":
    unittest.main()
