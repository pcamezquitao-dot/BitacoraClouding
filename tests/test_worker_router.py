from fastapi import HTTPException

from app.routers.worker import _identify, worker_time


class Result:
    def __init__(self, rows):
        self.rows = rows

    def mappings(self):
        return self

    def first(self):
        return self.rows[0] if self.rows else None

    def all(self):
        return self.rows


class WorkerDb:
    def __init__(self, worker=True):
        self.worker = worker
        self.sql = []

    def execute(self, statement, params):
        sql = str(statement)
        self.sql.append((sql, params))
        if "FROM participante p" in sql:
            rows = [{"id_participante": 2, "codigo": "P0002", "nombre_completo": "Trabajador"}]
            return Result(rows if self.worker else [])
        if "FROM bitacora_diaria" in sql:
            return Result([])
        if "FROM dimension_calendario" in sql:
            return Result([{"fecha": __import__("datetime").date(2026, 8, 3), "dia_semana": 1,
                            "sabado": 0, "domingo": 0, "festivo": 0, "nombre_festivo": None}])
        raise AssertionError(sql)


def test_worker_time_authorizes_code_and_uses_dimension_calendar():
    db = WorkerDb()
    result = worker_time(" p0002 ", 2026, 8, db)
    assert result["id_participante"] == 2
    assert len(result["dias"]) == 31
    assert any("FROM dimension_calendario" in sql for sql, _ in db.sql)
    event_query = next((sql, params) for sql, params in db.sql if "FROM bitacora_diaria" in sql)
    assert event_query[1]["participant"] == 2


def test_unknown_or_inactive_worker_is_rejected_without_reading_events():
    db = WorkerDb(worker=False)
    try:
        _identify(db, "P9999")
        raise AssertionError("Expected HTTPException")
    except HTTPException as error:
        assert error.status_code == 403
    assert len(db.sql) == 1
