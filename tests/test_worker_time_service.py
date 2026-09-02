from datetime import date

from app.services.worker_time_service import calculate_days


def test_pairs_entry_exit_and_keeps_every_day_of_month():
    rows = [
        {"ts_in_min": 29762700, "tipo_anotacion": 4, "client_uuid": "in"},
        {"ts_in_min": 29763240, "tipo_anotacion": 5, "client_uuid": "out"},
    ]
    days = calculate_days(2026, 8, rows, {date(2026, 8, 3): {"dia_semana": 1}})
    assert len(days) == 31
    assert days[2]["minutos_trabajados"] == 540
    assert not days[2]["registro_incompleto"]


def test_incomplete_and_absent_workday_are_distinct():
    rows = [{"ts_in_min": 29762700, "tipo_anotacion": 4, "client_uuid": "in"}]
    days = calculate_days(2026, 8, rows, {})
    assert days[2]["registro_incompleto"]
    assert not days[3]["registro_incompleto"]


def test_duplicate_entry_does_not_create_extra_work():
    rows = [
        {"ts_in_min": 29762700, "tipo_anotacion": 4, "client_uuid": "a"},
        {"ts_in_min": 29762701, "tipo_anotacion": 4, "client_uuid": "duplicate"},
        {"ts_in_min": 29763240, "tipo_anotacion": 5, "client_uuid": "b"},
    ]
    assert calculate_days(2026, 8, rows, {})[2]["minutos_trabajados"] == 540


def test_shift_crossing_midnight_belongs_to_entry_day():
    rows = [
        {"ts_in_min": 29765040, "tipo_anotacion": 4, "client_uuid": "in"},
        {"ts_in_min": 29765160, "tipo_anotacion": 5, "client_uuid": "out"},
    ]
    days = calculate_days(2026, 8, rows, {})
    assert days[3]["minutos_trabajados"] == 120
    assert days[4]["minutos_trabajados"] == 0


def test_dimension_calendar_holiday_name_and_precedence():
    day = calculate_days(2026, 8, [], {
        date(2026, 8, 3): {"dia_semana": 1, "festivo": True, "nombre_festivo": "Prueba"}
    })[2]
    assert day["festivo"] and not day["laborable"]
    assert day["nombre_festivo"] == "Prueba"
