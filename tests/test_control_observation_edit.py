import pytest
from fastapi import HTTPException

from app.core.supervisor_auth import SupervisorIdentity
from app.routers import control_supervisor as router
from app.schemas.control_supervisor import ControlObservationUpdateIn
from app.services import control_supervisor_service as service
from app.services.supervisor_service import SupervisorAuthorizationError


class Result:
    def __init__(self, row=None, scalar=None, lastrowid=None):
        self.row, self.scalar, self.lastrowid = row, scalar, lastrowid
    def mappings(self): return self
    def first(self): return self.row
    def scalar_one_or_none(self): return self.scalar
    def all(self): return self.row


class EditDb:
    def __init__(self, observation="antes", fail_evidence=False):
        self.observation, self.fail_evidence = observation, fail_evidence
        self.requests, self.commits, self.rollbacks = [], 0, 0
    def execute(self, statement, params):
        sql = str(statement)
        self.requests.append((sql, dict(params)))
        if "FOR UPDATE" in sql:
            return Result({"id_bitacora": 1234, "id_empleado": 15, "ts_in_min": 29762700,
                           "tipo_anotacion": 4, "observaciones": self.observation})
        if "SELECT id_area" in sql:
            return Result(scalar=7)
        if sql.lstrip().startswith("UPDATE"):
            self.observation = params["new_value"]
            return Result()
        if sql.lstrip().startswith("INSERT"):
            if self.fail_evidence:
                raise RuntimeError("evidence failed")
            return Result(lastrowid=88)
        raise AssertionError(sql)
    def commit(self): self.commits += 1
    def rollback(self): self.rollbacks += 1


@pytest.fixture
def authorization(monkeypatch):
    monkeypatch.setattr(service, "validated_supervisor_identity", lambda _db, _identity: {
        "id_supervisor": 2, "codigo": "P0002", "nombre_completo": "Supervisor"
    })
    monkeypatch.setattr(service, "_authorized_worker", lambda _db, _identity, participant_id: {
        "id_participante": participant_id, "codigo": "P0015", "id_area": 7
    })
    monkeypatch.setattr(service, "_resolve_bao_table", lambda _db: "bitacora_area_observacion")


def test_updates_only_selected_observation_and_creates_one_linked_text_evidence(authorization):
    db = EditDb()
    result = service.update_control_observation(
        db, SupervisorIdentity(2, "P0002", (7,)), 1234, "antes", "despues"
    )
    updates = [(sql, values) for sql, values in db.requests if sql.lstrip().startswith("UPDATE")]
    inserts = [(sql, values) for sql, values in db.requests if sql.lstrip().startswith("INSERT")]
    assert len(updates) == 1 and set(updates[0][1]) == {"new_value", "id"}
    assert len(inserts) == 1
    evidence = inserts[0][1]
    assert evidence["bitacora"] == 1234 and evidence["supervisor"] == 2
    assert "Observacion anterior: [antes]" in evidence["content"]
    assert "Observacion nueva: [despues]" in evidence["content"]
    assert result["modificada"] is True and result["id_evidencia"] == 88


def test_same_observation_creates_no_update_or_evidence(authorization):
    db = EditDb()
    result = service.update_control_observation(
        db, SupervisorIdentity(2, "P0002", (7,)), 1234, "antes", "antes"
    )
    assert result["modificada"] is False
    assert not any(sql.lstrip().startswith(("UPDATE", "INSERT")) for sql, _ in db.requests)


def test_concurrent_change_returns_409_and_rolls_back(monkeypatch):
    db = EditDb()
    monkeypatch.setattr(router, "update_control_observation", lambda *_args: (_ for _ in ()).throw(
        RuntimeError("CONFLICTO_OBSERVACION")
    ))
    with pytest.raises(HTTPException) as error:
        router.actualizar_observacion(
            1234, ControlObservationUpdateIn(observacion_anterior="a", observacion_nueva="b"),
            SupervisorIdentity(2, "P0002", (7,)), db
        )
    assert error.value.status_code == 409
    assert db.rollbacks == 1 and db.commits == 0


def test_unauthorized_supervisor_returns_403_and_creates_nothing(monkeypatch):
    db = EditDb()
    monkeypatch.setattr(router, "update_control_observation", lambda *_args: (_ for _ in ()).throw(
        SupervisorAuthorizationError("fuera de alcance")
    ))
    with pytest.raises(HTTPException) as error:
        router.actualizar_observacion(
            1234, ControlObservationUpdateIn(observacion_anterior="a", observacion_nueva="b"),
            SupervisorIdentity(3, "P0003", (8,)), db
        )
    assert error.value.status_code == 403 and db.rollbacks == 1


def test_evidence_failure_rolls_back_and_does_not_commit(authorization):
    db = EditDb(fail_evidence=True)
    with pytest.raises(RuntimeError):
        router.actualizar_observacion(
            1234, ControlObservationUpdateIn(observacion_anterior="antes", observacion_nueva="despues"),
            SupervisorIdentity(2, "P0002", (7,)), db
        )
    assert db.rollbacks == 1 and db.commits == 0


def test_non_entry_or_exit_is_rejected(authorization):
    db = EditDb()
    original = db.execute
    def type_six(statement, params):
        result = original(statement, params)
        if "FOR UPDATE" in str(statement): result.row["tipo_anotacion"] = 6
        return result
    db.execute = type_six
    with pytest.raises(ValueError):
        service.update_control_observation(db, SupervisorIdentity(2, "P0002", (7,)), 1234, "antes", "x")


def test_day_query_requests_only_real_type_four_and_five_rows(authorization):
    class DayDb:
        sql = ""
        def execute(self, statement, params):
            self.sql = str(statement)
            return Result(row=[
                {"id_bitacora": 1, "id_participante": 15, "timestamp_min": 29762700,
                 "tipo_anotacion": 4, "observaciones": "entrada"},
                {"id_bitacora": 2, "id_participante": 15, "timestamp_min": 29763180,
                 "tipo_anotacion": 5, "observaciones": "salida"},
            ])
    from datetime import date
    db = DayDb()
    rows = service.control_day_bitacoras(
        db, SupervisorIdentity(2, "P0002", (7,)), 15, date(2026, 8, 3)
    )
    assert "tipo_anotacion IN (4,5)" in db.sql
    assert [row["tipo_anotacion"] for row in rows] == [4, 5]
