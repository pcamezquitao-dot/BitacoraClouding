from pathlib import Path
import unittest


PROPOSAL = (
    Path(__file__).resolve().parents[1]
    / "migrations"
    / "20260729_tipos_participante_dinamicos.sql"
)


class TiposParticipanteMigrationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = PROPOSAL.read_text(encoding="utf-8").upper()

    def test_propuesta_es_aditiva_y_no_destruye_datos(self):
        self.assertNotIn("DROP TABLE", self.sql)
        self.assertNotIn("DELETE FROM", self.sql)
        self.assertNotIn("TRUNCATE", self.sql)
        self.assertNotIn("UPDATE EMPLEADO_AREA", self.sql)

    def test_agrega_estado_y_capacidades_dinamicas(self):
        self.assertIn("ALTER TABLE TIPOS_PARTICIPANTE", self.sql)
        self.assertIn("ADD COLUMN ACTIVO", self.sql)
        self.assertIn("CREATE TABLE CAPACIDADES_PARTICIPANTE", self.sql)
        self.assertIn("CREATE TABLE TIPO_PARTICIPANTE_CAPACIDAD", self.sql)

    def test_protege_tipos_y_capacidades_referenciados(self):
        self.assertGreaterEqual(self.sql.count("ON DELETE RESTRICT"), 2)
        self.assertIn("REFERENCES TIPOS_PARTICIPANTE (CODIGO)", self.sql)
        self.assertIn("REFERENCES CAPACIDADES_PARTICIPANTE (CODIGO)", self.sql)

    def test_incluye_auditoria_administrativa(self):
        self.assertIn("CREATE TABLE ADMINISTRACION_CATALOGO_AUDITORIA", self.sql)
        self.assertIn("VALOR_ANTERIOR JSON", self.sql)
        self.assertIn("VALOR_NUEVO JSON", self.sql)
        self.assertIn("ACTOR VARCHAR", self.sql)

    def test_conserva_mapeo_transitorio_aprobado(self):
        self.assertIn("INSERT INTO TIPO_PARTICIPANTE_CAPACIDAD", self.sql)
        self.assertIn("(1, 'EMPLEADO')", self.sql)
        self.assertIn("(2, 'EMPLEADO')", self.sql)
        self.assertIn("(3, 'SUPERVISOR')", self.sql)

    def test_hace_autoincremental_la_clave_de_empleado_area(self):
        self.assertIn("ALTER TABLE EMPLEADO_AREA", self.sql)
        self.assertIn(
            "ID_EMPLEADO_AREA INT UNSIGNED NOT NULL AUTO_INCREMENT",
            self.sql,
        )


if __name__ == "__main__":
    unittest.main()
