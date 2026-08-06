import unittest

from fastapi import HTTPException

from app.main import app
from app.routers.admin_catalog import router as protected_admin_router
from app.routers.administrative_areas import area_admin_identity, router as areas_router


class AdministrativeAreasAppControlTest(unittest.TestCase):
    def test_publica_los_cuatro_endpoints_de_areas(self):
        paths = app.openapi()["paths"]
        self.assertIn("get", paths["/admin/areas/arbol"])
        self.assertIn("post", paths["/admin/areas"])
        self.assertIn("put", paths["/admin/areas/{id_area}"])
        self.assertIn("delete", paths["/admin/areas/{id_area}"])

    def test_router_de_areas_no_depende_del_token(self):
        self.assertEqual([], areas_router.dependencies)

    def test_otros_maestros_conservan_proteccion(self):
        self.assertTrue(protected_admin_router.dependencies)

    def test_operaciones_exigen_identificacion_del_administrador(self):
        with self.assertRaises(HTTPException) as raised:
            area_admin_identity("   ", None)
        self.assertEqual(422, raised.exception.status_code)

    def test_normaliza_identificacion_y_dispositivo(self):
        identity = area_admin_identity("  Patricia  ", "  V2035  ")
        self.assertEqual("Patricia", identity.actor)
        self.assertEqual("V2035", identity.device)


if __name__ == "__main__":
    unittest.main()
