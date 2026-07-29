from datetime import date
import unittest

from app.services.admin_catalog_policy import (
    EMPLOYEE_CAPABILITY,
    SUPERVISOR_CAPABILITY,
    legacy_capabilities,
    normalize_type_description,
    validate_assignment_period,
    validate_capabilities,
)


class AdminCatalogPolicyTest(unittest.TestCase):
    def test_mapeo_transitorio_conserva_comportamiento_jaime05(self):
        self.assertEqual({EMPLOYEE_CAPABILITY}, legacy_capabilities(1))
        self.assertEqual({EMPLOYEE_CAPABILITY}, legacy_capabilities(2))
        self.assertEqual({SUPERVISOR_CAPABILITY}, legacy_capabilities(3))

    def test_tipo_nuevo_requiere_descripcion_normalizada(self):
        self.assertEqual(
            "Coordinador regional",
            normalize_type_description("  Coordinador   regional  "),
        )
        with self.assertRaisesRegex(ValueError, "descripción"):
            normalize_type_description("   ")

    def test_tipo_nuevo_requiere_al_menos_una_capacidad_activa(self):
        self.assertEqual(
            {EMPLOYEE_CAPABILITY, SUPERVISOR_CAPABILITY},
            validate_capabilities(
                [" empleado ", "SUPERVISOR", "EMPLEADO"],
                active_capabilities={EMPLOYEE_CAPABILITY, SUPERVISOR_CAPABILITY},
            ),
        )
        with self.assertRaisesRegex(ValueError, "capacidad"):
            validate_capabilities([], active_capabilities={EMPLOYEE_CAPABILITY})
        with self.assertRaisesRegex(ValueError, "inactiva"):
            validate_capabilities(
                ["GERENTE"],
                active_capabilities={EMPLOYEE_CAPABILITY},
            )

    def test_vigencia_exige_inicio_y_rechaza_final_anterior(self):
        validate_assignment_period(date(2026, 7, 29), None)
        validate_assignment_period(date(2026, 7, 29), date(2026, 7, 29))
        with self.assertRaisesRegex(ValueError, "fecha inicial"):
            validate_assignment_period(None, None)
        with self.assertRaisesRegex(ValueError, "anterior"):
            validate_assignment_period(date(2026, 7, 29), date(2026, 7, 28))


if __name__ == "__main__":
    unittest.main()
