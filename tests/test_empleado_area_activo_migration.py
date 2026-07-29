from pathlib import Path
import unittest


class EmpleadoAreaActivoMigrationTest(unittest.TestCase):
    def test_migracion_es_aditiva_y_conserva_registros_existentes(self):
        sql = Path(
            "migrations/20260729_empleado_area_activo.sql"
        ).read_text(encoding="utf-8").upper()

        self.assertIn("ALTER TABLE EMPLEADO_AREA", sql)
        self.assertIn("ADD COLUMN ACTIVO BOOLEAN NOT NULL DEFAULT TRUE", sql)
        self.assertNotIn("DROP TABLE", sql)
        self.assertNotIn("DELETE FROM EMPLEADO_AREA", sql)
        self.assertNotIn("TRUNCATE", sql)


if __name__ == "__main__":
    unittest.main()
