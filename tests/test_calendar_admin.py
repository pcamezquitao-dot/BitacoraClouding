from datetime import date
import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from pydantic import ValidationError

from app.core.admin_auth import AdminIdentity, require_admin_access
from app.main import app
from app.routers.admin_catalog import router as protected_admin_router
from app.routers.calendar_general import router as calendar_router
from app.schemas.admin_catalog import CalendarHolidayUpdate
from app.services.calendar_admin_service import (
    CalendarPeriodNotEditable,
    CalendarPeriodNotFound,
    list_calendar_tree,
    update_calendar_holiday,
)


def calendar_row(period_id=4, parent_id=3, level="DIA", active=True):
    return {
        "id_periodo": period_id,
        "id_padre": parent_id,
        "nivel": level,
        "codigo": f"P{period_id}",
        "nombre": f"Periodo {period_id}",
        "fecha_inicio": date(2026, 1, 1),
        "fecha_fin": date(2026, 1, 1),
        "numero_dia_semana": 4 if level == "DIA" else None,
        "nombre_dia_semana": "Jueves" if level == "DIA" else None,
        "es_fin_semana": False if level == "DIA" else None,
        "es_festivo": False,
        "nombre_festivo": None,
        "orden_periodo": period_id,
        "activo": active,
    }


class CalendarSchemaTest(unittest.TestCase):
    def test_festivo_exige_nombre_y_lo_normaliza(self):
        with self.assertRaises(ValidationError):
            CalendarHolidayUpdate(es_festivo=True, nombre_festivo="   ")
        payload = CalendarHolidayUpdate(
            es_festivo=True,
            nombre_festivo="  Día de prueba  ",
        )
        self.assertEqual("Día de prueba", payload.nombre_festivo)

    def test_dia_no_festivo_limpia_nombre(self):
        payload = CalendarHolidayUpdate(
            es_festivo=False,
            nombre_festivo="No debe conservarse",
        )
        self.assertIsNone(payload.nombre_festivo)


class CalendarTreeTest(unittest.TestCase):
    def test_construye_jerarquia_ordenada(self):
        rows = [
            calendar_row(1, None, "QUINQUENIO"),
            calendar_row(2, 1, "ANIO"),
            calendar_row(3, 2, "MES"),
            calendar_row(4, 3, "DIA"),
        ]
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = rows
        tree = list_calendar_tree(db)
        self.assertEqual([1], [node["id_periodo"] for node in tree])
        self.assertEqual(2, tree[0]["hijos"][0]["id_periodo"])
        self.assertEqual(3, tree[0]["hijos"][0]["hijos"][0]["id_periodo"])
        self.assertEqual(
            4,
            tree[0]["hijos"][0]["hijos"][0]["hijos"][0]["id_periodo"],
        )
        query = str(db.execute.call_args.args[0]).upper()
        self.assertIn("ORDER BY ORDEN_PERIODO", query)

    def test_dato_con_padre_invalido_no_crea_recursion(self):
        rows = [calendar_row(1, 1, "QUINQUENIO")]
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = rows
        tree = list_calendar_tree(db)
        self.assertEqual(1, len(tree))
        self.assertEqual([], tree[0]["hijos"])


class CalendarUpdateTest(unittest.TestCase):
    identity = AdminIdentity("administrador-prueba", "dispositivo-prueba")

    def db_with_row(self, row):
        db = MagicMock()
        selected = MagicMock()
        selected.mappings.return_value.one_or_none.return_value = row
        db.execute.side_effect = [selected, MagicMock(), MagicMock()]
        return db

    def test_actualiza_un_dia_y_audita(self):
        db = self.db_with_row(calendar_row())
        result = update_calendar_holiday(
            db, 4, True, "  Fiesta de prueba  ", self.identity
        )
        self.assertTrue(result["es_festivo"])
        self.assertEqual("Fiesta de prueba", result["nombre_festivo"])
        self.assertEqual(3, db.execute.call_count)
        audit_query = str(db.execute.call_args_list[2].args[0])
        self.assertIn("'EDITAR'", audit_query)
        db.commit.assert_called_once()
        db.rollback.assert_not_called()

    def test_desmarcar_guarda_nombre_nulo(self):
        row = calendar_row()
        row["es_festivo"] = True
        row["nombre_festivo"] = "Anterior"
        db = self.db_with_row(row)
        result = update_calendar_holiday(
            db, 4, False, "Ignorar", self.identity
        )
        self.assertFalse(result["es_festivo"])
        self.assertIsNone(result["nombre_festivo"])
        parameters = db.execute.call_args_list[1].args[1]
        self.assertIsNone(parameters["nombre_festivo"])

    def test_rechaza_periodo_inexistente_y_hace_rollback(self):
        db = self.db_with_row(None)
        with self.assertRaises(CalendarPeriodNotFound):
            update_calendar_holiday(db, 99, True, "Festivo", self.identity)
        db.rollback.assert_called_once()
        db.commit.assert_not_called()

    def test_rechaza_niveles_que_no_son_dia(self):
        for level in ("QUINQUENIO", "ANIO", "MES"):
            with self.subTest(level=level):
                db = self.db_with_row(calendar_row(level=level))
                with self.assertRaises(CalendarPeriodNotEditable):
                    update_calendar_holiday(db, 4, True, "Festivo", self.identity)
                db.rollback.assert_called_once()

    def test_rechaza_periodo_inactivo(self):
        db = self.db_with_row(calendar_row(active=False))
        with self.assertRaises(CalendarPeriodNotEditable):
            update_calendar_holiday(db, 4, True, "Festivo", self.identity)
        db.rollback.assert_called_once()

    def test_error_de_base_hace_rollback(self):
        db = MagicMock()
        selected = MagicMock()
        selected.mappings.return_value.one_or_none.return_value = calendar_row()
        db.execute.side_effect = [selected, RuntimeError("fallo")]
        with self.assertRaises(RuntimeError):
            update_calendar_holiday(db, 4, True, "Festivo", self.identity)
        db.rollback.assert_called_once()


class CalendarAuthorizationTest(unittest.TestCase):
    def test_endpoints_estan_en_router_administrativo(self):
        operations = {
            (method, route.path)
            for route in app.routes
            for method in getattr(route, "methods", set())
        }
        self.assertIn(("GET", "/admin/calendario/arbol"), operations)
        self.assertIn(
            ("PATCH", "/admin/calendario/dias/{id_periodo}/festivo"),
            operations,
        )

    def test_calendario_no_depende_del_token_administrativo(self):
        self.assertEqual([], calendar_router.dependencies)

    def test_los_demas_maestros_conservan_su_proteccion(self):
        self.assertTrue(protected_admin_router.dependencies)

    def test_sin_autorizacion_retorna_401(self):
        with patch("app.core.admin_auth.settings.ADMIN_API_TOKEN", "correcto"):
            with self.assertRaises(HTTPException) as raised:
                require_admin_access(None, "ciudadano", None)
        self.assertEqual(401, raised.exception.status_code)

    def test_token_incorrecto_retorna_401(self):
        credentials = HTTPAuthorizationCredentials(
            scheme="Bearer", credentials="incorrecto"
        )
        with patch("app.core.admin_auth.settings.ADMIN_API_TOKEN", "correcto"):
            with self.assertRaises(HTTPException) as raised:
                require_admin_access(credentials, "ciudadano", None)
        self.assertEqual(401, raised.exception.status_code)


if __name__ == "__main__":
    unittest.main()
