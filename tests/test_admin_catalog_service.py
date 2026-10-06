from datetime import date
import unittest
from unittest.mock import MagicMock

from app.core.admin_auth import AdminIdentity
from app.schemas.admin_catalog import AdministrativeAreaWrite, EmployeeAreaCreate
from app.services.admin_catalog_service import create_administrative_area
from app.services.admin_catalog_service import create_employee_area
from app.services.admin_catalog_service import delete_administrative_area
from app.services.admin_catalog_service import list_area_tree
from app.services.admin_catalog_service import list_participant_options
from app.services.admin_catalog_service import update_administrative_area
from app.services.admin_catalog_service import _ensure_area_reference_not_duplicate


def scalar_result(value):
    result = MagicMock()
    result.scalar_one_or_none.return_value = value
    return result


class AdminCatalogServiceTest(unittest.TestCase):
    def setUp(self):
        self.payload = EmployeeAreaCreate(
            id_participante=51,
            id_area=38,
            codigo_tipo=2,
            descripcion="Asignación de prueba",
            fecha_inicia=date(2026, 7, 29),
            fecha_final=None,
        )
        self.identity = AdminIdentity("pruebas", "dispositivo-pruebas")

    def test_selector_permite_cargar_los_51_participantes_actuales(self):
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = []

        self.assertEqual([], list_participant_options(db, ""))

        query = str(db.execute.call_args.args[0]).upper()
        self.assertIn("LIMIT 100", query)

    def test_crea_asignacion_y_auditoria_en_una_transaccion(self):
        db = MagicMock()
        inserted = MagicMock()
        inserted.lastrowid = 52
        audit_result = MagicMock()
        db.execute.side_effect = [
            scalar_result(1),
            scalar_result(1),
            scalar_result(1),
            scalar_result(None),
            inserted,
            audit_result,
        ]

        result = create_employee_area(db, self.payload, self.identity)

        self.assertEqual(52, result["id_empleado_area"])
        db.commit.assert_called_once_with()
        db.rollback.assert_not_called()
        insert_sql = str(db.execute.call_args_list[4].args[0]).upper()
        self.assertIn("INSERT INTO EMPLEADO_AREA", insert_sql)
        self.assertIn("FECHA_INICIA", insert_sql)
        self.assertNotIn("ID_EMPLEADO_AREA,", insert_sql)
        audit_sql = str(db.execute.call_args_list[5].args[0]).upper()
        self.assertIn("ADMINISTRACION_CATALOGO_AUDITORIA", audit_sql)

    def test_solapamiento_activo_devuelve_mensaje_claro(self):
        db = MagicMock()
        db.execute.side_effect = [
            scalar_result(1),
            scalar_result(1),
            scalar_result(1),
            scalar_result(7),
        ]

        with self.assertRaisesRegex(ValueError, "ya tiene una asignación activa"):
            create_employee_area(db, self.payload, self.identity)

        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()
        self.assertEqual(4, db.execute.call_count)

    def test_asignacion_retirada_no_bloquea_una_nueva_creacion(self):
        db = MagicMock()
        inserted = MagicMock()
        inserted.lastrowid = 99
        audit_result = MagicMock()
        db.execute.side_effect = [
            scalar_result(1),
            scalar_result(1),
            scalar_result(1),
            scalar_result(None),
            inserted,
            audit_result,
        ]

        result = create_employee_area(db, self.payload, self.identity)

        self.assertEqual(99, result["id_empleado_area"])
        db.commit.assert_called_once_with()
        db.rollback.assert_not_called()

    def test_tipo_inactivo_revierte_antes_de_buscar_solapamientos(self):
        db = MagicMock()
        db.execute.side_effect = [
            scalar_result(1),
            scalar_result(1),
            scalar_result(None),
        ]

        with self.assertRaisesRegex(ValueError, "inactivo"):
            create_employee_area(db, self.payload, self.identity)

        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()
        self.assertEqual(3, db.execute.call_count)

    def test_arbol_acepta_raiz_cero_e_incluye_areas_huerfanas(self):
        db = MagicMock()
        result = MagicMock()
        result.mappings.return_value.all.return_value = []
        count_result = MagicMock()
        count_result.scalar_one.return_value = 0
        db.execute.side_effect = [result, count_result]

        self.assertEqual([], list_area_tree(db))

        query = str(db.execute.call_args_list[0].args[0]).upper()
        self.assertIn("NODO_PADRE = 0", query)
        self.assertIn("NOT EXISTS", query)
        self.assertIn("ORDER BY ORDEN", query)
        self.assertIn("DESCRIPCION", query)
        self.assertIn("UTF8MB4_UCA1400_AI_CI", query)

    def test_arbol_rechaza_resultado_que_no_cubre_toda_la_tabla(self):
        db = MagicMock()
        tree_result = MagicMock()
        tree_result.mappings.return_value.all.return_value = [
            {"id_area": 1, "descripcion": "Raíz", "nombre_corto": "R",
             "id_padre": None, "nivel": 0, "ruta": "Raíz"}
        ]
        count_result = MagicMock()
        count_result.scalar_one.return_value = 2
        db.execute.side_effect = [tree_result, count_result]

        with self.assertRaisesRegex(ValueError, "no cubre todas"):
            list_area_tree(db)

    def test_crea_rama_hija_y_audita_en_una_transaccion(self):
        db = MagicMock()
        parent_result = MagicMock()
        parent_result.scalar_one_or_none.return_value = 1
        duplicate_result = MagicMock()
        duplicate_result.scalar_one_or_none.return_value = None
        reference_result = MagicMock()
        reference_result.scalar_one_or_none.return_value = None
        inserted = MagicMock()
        inserted.lastrowid = 40
        db.execute.side_effect = [
            parent_result,
            duplicate_result,
            reference_result,
            inserted,
            MagicMock(),
        ]
        payload = AdministrativeAreaWrite(
            descripcion=" Nueva rama ",
            nombre_corto=" NR ",
            nodo_padre=10,
        )

        result = create_administrative_area(db, payload, self.identity)

        self.assertEqual(40, result["id_area_administrativa"])
        self.assertEqual(10, result["nodo_padre"])
        self.assertEqual("Nueva rama", result["descripcion"])
        self.assertEqual("NR", result["nombre_corto"])
        db.commit.assert_called_once_with()
        db.rollback.assert_not_called()

    def test_impide_editar_area_como_hija_de_si_misma(self):
        db = MagicMock()
        current = MagicMock()
        current.mappings.return_value.one_or_none.return_value = {
            "id_area_administrativa": 10,
            "descripcion": "Área",
            "nombre_corto": None,
            "nodo_padre": 1,
        }
        db.execute.return_value = current
        payload = AdministrativeAreaWrite(
            descripcion="Área",
            nombre_corto="AREA",
            nodo_padre=10,
        )

        with self.assertRaisesRegex(ValueError, "hija de sí misma"):
            update_administrative_area(db, 10, payload, self.identity)

        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()

    def test_referencia_es_obligatoria_y_se_valida_despues_de_trim(self):
        with self.assertRaises(ValueError):
            AdministrativeAreaWrite(
                descripcion="Área",
                nombre_corto="   ",
                nodo_padre=None,
            )

        payload = AdministrativeAreaWrite(
            descripcion=" Área ",
            nombre_corto=" REF ",
            nodo_padre=None,
        )
        self.assertEqual("Área", payload.descripcion)
        self.assertEqual("REF", payload.nombre_corto)

    def test_referencia_duplicada_se_rechaza_globalmente(self):
        db = MagicMock()
        duplicate_description = scalar_result(None)
        duplicate_reference = scalar_result(1)
        db.execute.side_effect = [duplicate_description, duplicate_reference]
        payload = AdministrativeAreaWrite(
            descripcion="Área nueva",
            nombre_corto="REF",
            nodo_padre=None,
        )

        with self.assertRaisesRegex(ValueError, "Referencia"):
            create_administrative_area(db, payload, self.identity)

        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()
        reference_sql = str(db.execute.call_args_list[1].args[0]).upper()
        self.assertIn("NOMBRE_CORTO =", reference_sql)

    def test_edicion_excluye_el_codigo_propio_al_validar_referencia(self):
        db = MagicMock()
        db.execute.return_value = scalar_result(None)

        _ensure_area_reference_not_duplicate(db, "PROPIA", exclude_area_id=10)

        params = db.execute.call_args.args[1]
        self.assertEqual(10, params["excluir"])
        self.assertEqual("PROPIA", params["nombre_corto"])

    def test_impide_eliminar_area_con_ramas_hijas(self):
        db = MagicMock()
        current = MagicMock()
        current.mappings.return_value.one_or_none.return_value = {
            "id_area_administrativa": 10,
            "descripcion": "Área",
            "nombre_corto": None,
            "nodo_padre": 1,
        }
        child = MagicMock()
        child.scalar_one_or_none.return_value = 1
        db.execute.side_effect = [current, child]

        with self.assertRaisesRegex(ValueError, "ramas hijas"):
            delete_administrative_area(db, 10, self.identity)

        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()


if __name__ == "__main__":
    unittest.main()
