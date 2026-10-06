import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException

from app.routers.bitacora_uc03 import (
    _crear_bitacora_diaria,
    _require_daily_log_responsible,
)


class CreateDailyLogRolePolicyTest(unittest.TestCase):
    @staticmethod
    def _db_with_roles(*roles: str):
        db = MagicMock()
        db.execute.return_value.mappings.return_value.all.return_value = [
            {"rol": role} for role in roles
        ]
        return db

    def test_supervisor_can_be_responsible_for_another_participant(self):
        db = self._db_with_roles("SUPERVISOR")

        _require_daily_log_responsible(db, id_empleado=20, id_supervisor=10)

        db.execute.assert_called_once()

    def test_manager_can_be_responsible_for_another_participant(self):
        db = self._db_with_roles("GERENTE")

        _require_daily_log_responsible(db, id_empleado=20, id_supervisor=10)

        db.execute.assert_called_once()

    def test_manager_can_be_responsible_for_self(self):
        db = self._db_with_roles("GERENTE")

        _require_daily_log_responsible(db, id_empleado=10, id_supervisor=10)

        db.execute.assert_called_once()

    def test_supervisor_cannot_be_responsible_for_self(self):
        db = self._db_with_roles("SUPERVISOR")

        with self.assertRaises(HTTPException) as raised:
            _require_daily_log_responsible(db, id_empleado=10, id_supervisor=10)

        self.assertEqual(422, raised.exception.status_code)
        self.assertEqual(
            "Solo un gerente puede ser supervisor de sí mismo",
            raised.exception.detail,
        )

    def test_other_role_cannot_be_responsible(self):
        db = self._db_with_roles()

        with self.assertRaises(HTTPException) as raised:
            _require_daily_log_responsible(db, id_empleado=20, id_supervisor=10)

        self.assertEqual(422, raised.exception.status_code)
        self.assertEqual(
            "El responsable debe tener cargo activo de supervisor o gerente",
            raised.exception.detail,
        )

    @staticmethod
    def _role_result(role: str):
        result = MagicMock()
        result.mappings.return_value.all.return_value = [{"rol": role}]
        return result

    @staticmethod
    def _insert_result(bitacora_id: int):
        result = MagicMock()
        result.lastrowid = bitacora_id
        return result

    def test_supervisor_participant_with_valid_supervisor_reaches_insert(self):
        db = MagicMock()
        db.execute.side_effect = [
            self._role_result("SUPERVISOR"),
            self._insert_result(501),
        ]
        with patch(
            "app.routers.bitacora_uc03._get_bitacora_by_client_uuid",
            return_value=None,
        ), patch("app.routers.bitacora_uc03.require_asignacion_activa"):
            result = _crear_bitacora_diaria(
                db=db,
                id_empleado=20,
                id_supervisor=10,
                ts_in_min=30_000_001,
                ts_out_min=None,
                tipo_anotacion=4,
                observaciones="supervisor con superior válido",
            )

        self.assertEqual(501, result.id_bitacora)
        self.assertEqual(20, result.id_empleado)
        self.assertEqual(10, result.id_supervisor)
        self.assertEqual(2, db.execute.call_count)

    def test_manager_self_responsibility_reaches_insert_with_equal_ids(self):
        db = MagicMock()
        db.execute.side_effect = [
            self._role_result("GERENTE"),
            self._insert_result(502),
        ]
        with patch(
            "app.routers.bitacora_uc03._get_bitacora_by_client_uuid",
            return_value=None,
        ), patch("app.routers.bitacora_uc03.require_asignacion_activa"):
            result = _crear_bitacora_diaria(
                db=db,
                id_empleado=30,
                id_supervisor=30,
                ts_in_min=30_000_002,
                ts_out_min=None,
                tipo_anotacion=4,
                observaciones="gerente autorresponsable",
            )

        self.assertEqual(502, result.id_bitacora)
        self.assertEqual(30, result.id_empleado)
        self.assertEqual(30, result.id_supervisor)
        self.assertEqual(2, db.execute.call_count)

    def test_supervisor_self_responsibility_is_rejected_before_insert(self):
        db = MagicMock()
        db.execute.side_effect = [self._role_result("SUPERVISOR")]
        with patch(
            "app.routers.bitacora_uc03._get_bitacora_by_client_uuid",
            return_value=None,
        ), patch("app.routers.bitacora_uc03.require_asignacion_activa"):
            with self.assertRaises(HTTPException) as raised:
                _crear_bitacora_diaria(
                    db=db,
                    id_empleado=10,
                    id_supervisor=10,
                    ts_in_min=30_000_003,
                    ts_out_min=None,
                    tipo_anotacion=4,
                    observaciones="autorreferencia inválida",
                )

        self.assertEqual(422, raised.exception.status_code)
        self.assertEqual(1, db.execute.call_count)


if __name__ == "__main__":
    unittest.main()
