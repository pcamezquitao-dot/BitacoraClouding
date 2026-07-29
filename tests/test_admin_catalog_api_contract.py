import unittest
from unittest.mock import patch

from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.core.admin_auth import require_admin_access
from app.main import app


class AdminCatalogApiContractTest(unittest.TestCase):
    def test_publica_endpoints_administrativos_previstos(self):
        paths = app.openapi()["paths"]
        self.assertIn("/admin/tipos-participante", paths)
        self.assertIn("get", paths["/admin/tipos-participante"])
        self.assertIn("post", paths["/admin/tipos-participante"])
        self.assertIn("/admin/tipos-participante/{codigo}", paths)
        self.assertIn("put", paths["/admin/tipos-participante/{codigo}"])
        self.assertIn("/admin/tipos-participante/{codigo}/estado", paths)
        self.assertIn(
            "put",
            paths["/admin/tipos-participante/{codigo}/estado"],
        )
        self.assertIn("/admin/areas/arbol", paths)
        self.assertIn("get", paths["/admin/areas/arbol"])
        self.assertIn("/admin/areas", paths)
        self.assertIn("post", paths["/admin/areas"])
        self.assertIn("/admin/areas/{id_area}", paths)
        self.assertIn("put", paths["/admin/areas/{id_area}"])
        self.assertIn("delete", paths["/admin/areas/{id_area}"])
        self.assertIn("/admin/empleado-area", paths)
        self.assertIn("post", paths["/admin/empleado-area"])
        self.assertIn("/admin/empleado-area/tree", paths)
        self.assertIn("get", paths["/admin/empleado-area/tree"])
        self.assertIn("/admin/empleado-area/{id_asignacion}", paths)
        self.assertIn("put", paths["/admin/empleado-area/{id_asignacion}"])
        self.assertIn("delete", paths["/admin/empleado-area/{id_asignacion}"])
        self.assertIn("/admin/participantes/options", paths)
        self.assertIn("get", paths["/admin/participantes/options"])

    def test_rechaza_escrituras_sin_token_administrativo(self):
        with patch("app.core.admin_auth.settings.ADMIN_API_TOKEN", "secreto"):
            with self.assertRaises(HTTPException) as raised:
                require_admin_access(None, "administrador")
        self.assertEqual(401, raised.exception.status_code)

    def test_identifica_actor_con_token_valido(self):
        credentials = HTTPAuthorizationCredentials(
            scheme="Bearer",
            credentials="secreto",
        )
        with patch("app.core.admin_auth.settings.ADMIN_API_TOKEN", "secreto"):
            identity = require_admin_access(
                credentials,
                "  responsable.catalogos  ",
                "V2035-pruebas",
            )
        self.assertEqual("responsable.catalogos", identity.actor)
        self.assertEqual("V2035-pruebas", identity.device)


if __name__ == "__main__":
    unittest.main()
