from datetime import date
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.schemas.work_schedules_admin import (
    WorkScheduleDetailWrite,
    WorkScheduleWrite,
    programmed_week_minutes,
)
from app.services import work_schedules_admin_service as service
def detail(day, start=None, end=None, *, number=1, next_day=False, rest=0, paid=False, working=True):
    return WorkScheduleDetailWrite(
        dia_semana_num=day, numero_tramo=number, es_laborable=working,
        hora_entrada_min=start, hora_salida_min=end,
        salida_dia_siguiente=next_day, descanso_min=rest,
        descanso_remunerado=paid,
    )


def complete_details():
    return [
        detail(1, 360, 840, rest=60), detail(2, 420, 960, rest=60),
        detail(3, 360, 840, rest=60), detail(4, 360, 600),
        detail(4, 840, 1080, number=2), detail(5, 1260, 300, next_day=True),
        detail(6, 360, 600), detail(7, working=False),
    ]


def payload(**changes):
    values = dict(
        codigo_jornada=" JOR-TEST-01 ", nombre_jornada=" Jornada  de prueba ",
        minutos_objetivo_semana=2520, vigencia_desde=date(2026, 8, 1),
        activo=True, aplica_control_horario=True, detalles=complete_details(),
    )
    values.update(changes)
    return WorkScheduleWrite(**values)


def test_active_schedule_normalizes_and_totals_2520():
    value = payload()
    assert value.codigo_jornada == "JOR-TEST-01"
    assert value.nombre_jornada == "Jornada de prueba"
    assert programmed_week_minutes(value.detalles) == value.minutos_objetivo_semana == 2520


def test_inactive_schedule_accepts_42_hours_30_minutes_as_2550_minutes():
    value = payload(activo=False, minutos_objetivo_semana=2550)
    assert value.minutos_objetivo_semana == 2550


@pytest.mark.parametrize("invalid", [
    dict(dia_semana_num=0, hora_entrada_min=420, hora_salida_min=480),
    dict(dia_semana_num=8, hora_entrada_min=420, hora_salida_min=480),
    dict(dia_semana_num=1, hora_entrada_min=430, hora_salida_min=480),
    dict(dia_semana_num=1, hora_entrada_min=420, hora_salida_min=490),
])
def test_invalid_day_or_quarter_is_rejected(invalid):
    with pytest.raises(ValidationError):
        WorkScheduleDetailWrite(**invalid)


def test_overlap_and_active_mismatch_are_rejected():
    base = payload(activo=False).model_dump()
    with pytest.raises(ValidationError):
        WorkScheduleWrite(**(base | {"activo": True, "minutos_objetivo_semana": 2505}))
    with pytest.raises(ValidationError):
        WorkScheduleWrite(**(base | {"detalles": complete_details() + [detail(4, 570, 660, number=3)]}))


def test_no_control_requires_zero_and_no_details():
    value = WorkScheduleWrite(
        codigo_jornada="SIN_HORARIO", nombre_jornada="Sin horario fijo",
        minutos_objetivo_semana=0, vigencia_desde=date(2026, 8, 1),
        activo=True, aplica_control_horario=False,
    )
    assert value.detalles == []
    with pytest.raises(ValidationError):
        WorkScheduleWrite(**(value.model_dump() | {"minutos_objetivo_semana": 15}))


def test_referenced_schedule_cannot_be_inactivated(monkeypatch):
    current = payload().model_dump() | {
        "id_jornada": 9, "total_programado_semana": 2520,
        "referenciada_activa": True,
        "fecha_creacion": None, "fecha_actualizacion": None,
    }
    monkeypatch.setattr(service, "get_schedule", lambda _db, _id: current)
    with pytest.raises(service.WorkScheduleConflict, match="No se puede inactivar"):
        service.change_status(object(), 9, False)


def test_unreferenced_schedule_changes_status_without_duplicate_activo(monkeypatch):
    current = payload().model_dump() | {
        "id_jornada": 9, "total_programado_semana": 2520,
        "referenciada_activa": False,
        "fecha_creacion": None, "fecha_actualizacion": None,
    }
    received = {}
    monkeypatch.setattr(service, "get_schedule", lambda _db, _id: current)
    monkeypatch.setattr(
        service,
        "update_schedule",
        lambda _db, _id, update: received.setdefault("payload", update),
    )

    service.change_status(object(), 9, False)

    assert received["payload"].activo is False


def test_create_rolls_back_when_detail_write_fails(monkeypatch):
    class FailingSession:
        committed = False
        rolled_back = False
        calls = 0

        def execute(self, *_args, **_kwargs):
            self.calls += 1
            if self.calls == 1:
                return SimpleNamespace(lastrowid=11)
            raise RuntimeError("fallo al escribir detalle")

        def commit(self):
            self.committed = True

        def rollback(self):
            self.rolled_back = True

    db = FailingSession()
    with pytest.raises(RuntimeError, match="fallo al escribir detalle"):
        service.create_schedule(db, payload())
    assert db.rolled_back is True
    assert db.committed is False


def test_referenced_schedule_only_allows_name_and_notes(monkeypatch):
    current = payload().model_dump() | {
        "id_jornada": 9,
        "total_programado_semana": 2520,
        "referenciada_activa": True,
        "fecha_creacion": None,
        "fecha_actualizacion": None,
    }
    monkeypatch.setattr(service, "_referenced_active", lambda _db, _id: True)
    monkeypatch.setattr(service, "get_schedule", lambda _db, _id: current)
    service._assert_edit_allowed(object(), 9, payload(nombre_jornada="Otro nombre", observaciones="Nueva nota"))
    with pytest.raises(service.WorkScheduleConflict, match="solo puede editar"):
        service._assert_edit_allowed(object(), 9, payload(codigo_jornada="OTRA"))


def test_openapi_exposes_only_the_authorized_schedule_operations():
    from app.main import app

    paths = app.openapi()["paths"]
    assert {"get", "post"} <= set(paths["/admin/jornadas"])
    assert {"get", "put"} <= set(paths["/admin/jornadas/{schedule_id}"])
    assert set(paths["/admin/jornadas/{schedule_id}/estado"]) == {"patch"}
    assert all("delete" not in methods for path, methods in paths.items() if path.startswith("/admin/jornadas"))
