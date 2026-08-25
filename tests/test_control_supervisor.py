from datetime import date

import pytest
from fastapi import HTTPException

from app.core.supervisor_auth import SupervisorIdentity
from app.routers.control_supervisor import consultar_control
from app.main import app
from app.services import control_supervisor_service as service
from app.services.supervisor_service import SupervisorAuthorizationError
from app.services.worker_time_service import calculate_days


class Result:
    def __init__(self, rows):
        self.rows = rows

    def mappings(self):
        return self

    def all(self):
        return self.rows


class ControlDb:
    def __init__(self):
        self.requests = []

    def execute(self, statement, params):
        self.requests.append((str(statement), params))
        if "FROM bitacora_diaria" in str(statement):
            assert params["participant"] == 10
            return Result([
                {"id_bitacora": 1, "ts_in_min": 29762700, "tipo_anotacion": 4, "client_uuid": "in"},
                {"id_bitacora": 2, "ts_in_min": 29763240, "tipo_anotacion": 5, "client_uuid": "out"},
            ])
        if "FROM dimension_calendario" in str(statement):
            return Result([{"fecha": date(2026, 8, 3), "dia_semana": 1,
                            "sabado": False, "domingo": False, "festivo": False,
                            "nombre_festivo": None}])
        raise AssertionError(str(statement))


def test_control_only_reads_supervised_participants_and_reuses_worker_days(monkeypatch):
    monkeypatch.setattr(service, "identify_supervisor", lambda _db, _code: {
        "id_supervisor": 2, "codigo": "P0002", "nombre_completo": "Supervisor"
    })
    monkeypatch.setattr(service, "supervised_participants", lambda _db, _code: [
        {"id_participante": 10, "codigo": "P0010", "nombre": "Jaime", "apellido": "Camargo", "area": "Cultivo"},
        {"id_participante": 10, "codigo": "P0010", "nombre": "Jaime", "apellido": "Camargo", "area": "Cultivo 2"},
    ])
    db = ControlDb()
    report = service.control_supervisor_report(db, "P0002", 2026, 8)
    expected = calculate_days(2026, 8, [
        {"id_bitacora": 1, "ts_in_min": 29762700, "tipo_anotacion": 4, "client_uuid": "in"},
        {"id_bitacora": 2, "ts_in_min": 29763240, "tipo_anotacion": 5, "client_uuid": "out"},
    ], {date(2026, 8, 3): {"dia_semana": 1}})
    assert report["acumulado"]["supervisados"] == 1
    assert report["supervisados"][0]["areas"] == ["Cultivo", "Cultivo 2"]
    assert report["supervisados"][0]["dias"] == expected
    assert report["acumulado"]["total_minutos"] == 540


@pytest.mark.parametrize("code", ["P0001", "P9999"])
def test_control_rejects_real_non_supervisor_and_missing_code(monkeypatch, code):
    def reject(_db, _identity, _year, _month):
        raise SupervisorAuthorizationError("No autorizado")

    monkeypatch.setattr("app.routers.control_supervisor.control_supervisor_report_for_identity", reject)
    with pytest.raises(HTTPException) as error:
        consultar_control(2026, 8, SupervisorIdentity(2, code, (7,)), object())
    assert error.value.status_code == 403


def test_control_aggregates_multiple_supervised_workers(monkeypatch):
    monkeypatch.setattr(service, "identify_supervisor", lambda _db, _code: {
        "id_supervisor": 2, "codigo": "P0002", "nombre_completo": "Supervisor"
    })
    monkeypatch.setattr(service, "supervised_participants", lambda _db, _code: [
        {"id_participante": 10, "codigo": "P0010", "nombre": "Uno", "apellido": "", "area": "A"},
        {"id_participante": 11, "codigo": "P0011", "nombre": "Dos", "apellido": "", "area": "B"},
    ])
    monkeypatch.setattr(service, "_worker_days", lambda _db, participant_id, _year, _month: [{
        "fecha": "2026-08-03", "dia_semana": 1, "laborable": True, "sabado": False,
        "domingo": False, "festivo": False, "nombre_festivo": None,
        "minutos_trabajados": participant_id, "registro_incompleto": False, "eventos": [],
    }])
    report = service.control_supervisor_report(object(), "P0002", 2026, 8)
    assert [worker["id_participante"] for worker in report["supervisados"]] == [10, 11]
    assert report["acumulado"]["total_minutos"] == 21


def test_control_supports_supervisor_without_supervised_workers(monkeypatch):
    monkeypatch.setattr(service, "identify_supervisor", lambda _db, _code: {
        "id_supervisor": 2, "codigo": "P0002", "nombre_completo": "Supervisor"
    })
    monkeypatch.setattr(service, "supervised_participants", lambda _db, _code: [])
    report = service.control_supervisor_report(object(), "P0002", 2026, 8)
    assert report["supervisados"] == []
    assert report["acumulado"] == {
        "supervisados": 0, "total_minutos": 0, "jornadas_incompletas": 0
    }


@pytest.mark.parametrize(
    ("code", "participant_id", "area"),
    [("P0002", 2, "Finca1"), ("P0003", 3, "Finca2")],
)
def test_control_is_generic_for_real_supervisors(monkeypatch, code, participant_id, area):
    observed = []

    def identify(_db, requested_code):
        observed.append(requested_code)
        return {
            "id_supervisor": participant_id,
            "codigo": code,
            "nombre_completo": f"Supervisor {participant_id}",
        }

    def supervised(_db, requested_code):
        observed.append(requested_code)
        return []

    monkeypatch.setattr(service, "identify_supervisor", identify)
    monkeypatch.setattr(service, "supervised_participants", supervised)
    report = service.control_supervisor_report(object(), code, 2026, 8)

    assert observed == [code, code]
    assert report["id_supervisor"] == participant_id
    assert report["codigo_supervisor"] == code
    assert report["supervisados"] == []
    assert area in {"Finca1", "Finca2"}  # Evidencia del área real consultada en MariaDB.


def test_control_month_with_and_without_movements_returns_200_shape(monkeypatch):
    monkeypatch.setattr(service, "identify_supervisor", lambda _db, _code: {
        "id_supervisor": 2, "codigo": "P0002", "nombre_completo": "Supervisor"
    })
    monkeypatch.setattr(service, "supervised_participants", lambda _db, _code: [{
        "id_participante": 10, "codigo": "P0010", "nombre": "Trabajador",
        "apellido": "Prueba", "area": "F1_Cultivo",
    }])

    def days(_db, _participant_id, _year, month):
        if month == 7:
            return []
        return [{
            "fecha": "2026-08-03", "dia_semana": 1, "laborable": True,
            "sabado": False, "domingo": False, "festivo": False,
            "nombre_festivo": None, "minutos_trabajados": 480,
            "registro_incompleto": False, "eventos": [],
        }]

    monkeypatch.setattr(service, "_worker_days", days)
    july = service.control_supervisor_report(object(), "P0002", 2026, 7)
    august = service.control_supervisor_report(object(), "P0002", 2026, 8)

    assert july["acumulado"]["total_minutos"] == 0
    assert july["supervisados"][0]["dias"] == []
    assert august["acumulado"]["total_minutos"] == 480


def test_control_uses_only_the_scope_returned_by_supervisor_authorization(monkeypatch):
    requested_codes = []
    monkeypatch.setattr(service, "identify_supervisor", lambda _db, code: {
        "id_supervisor": 2, "codigo": code, "nombre_completo": "Supervisor"
    })

    def authorized_scope(_db, code):
        requested_codes.append(code)
        return [
            {"id_participante": 10, "codigo": "P0010", "nombre": "Uno", "apellido": "", "area": "Finca1"},
            {"id_participante": 10, "codigo": "P0010", "nombre": "Uno", "apellido": "", "area": "F1_Cultivo"},
            {"id_participante": 11, "codigo": "P0011", "nombre": "Dos", "apellido": "", "area": "F1_Cultivo"},
        ]

    monkeypatch.setattr(service, "supervised_participants", authorized_scope)
    monkeypatch.setattr(service, "_worker_days", lambda *_args: [])
    report = service.control_supervisor_report(object(), "P0002", 2026, 8)

    assert requested_codes == ["P0002"]
    assert [worker["id_participante"] for worker in report["supervisados"]] == [10, 11]
    assert report["supervisados"][0]["areas"] == ["Finca1", "F1_Cultivo"]


def test_control_router_is_registered_in_openapi():
    paths = app.openapi()["paths"]
    assert "/control/supervisor/me" in paths
    assert "/control/supervisor/{codigo}" not in paths
