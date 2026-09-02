from app.services.management_service import classify_day, sum_totals


def day(minutes, *, saturday=False, sunday=False, holiday=False):
    return {"fecha":"2026-08-03","dia_semana":6 if saturday else 7 if sunday else 1,
            "sabado":saturday,"domingo":sunday,"festivo":holiday,"nombre_festivo":"Festivo" if holiday else None,
            "minutos_trabajados":minutes,"registro_incompleto":False}


def test_weekday_and_saturday_limits():
    assert classify_day(day(360))["ordinarios_minutos"] == 360
    assert classify_day(day(480))["extras_minutos"] == 0
    assert classify_day(day(660))["extras_minutos"] == 180
    assert classify_day(day(180, saturday=True))["ordinarios_minutos"] == 180
    assert classify_day(day(240, saturday=True))["extras_minutos"] == 0
    assert classify_day(day(420, saturday=True))["extras_minutos"] == 180


def test_sunday_and_holiday_never_duplicate_or_become_extra():
    for value in (day(300, sunday=True), day(540, holiday=True), day(420, sunday=True, holiday=True)):
        result = classify_day(value)
        assert result["dominicales_festivos_minutos"] == value["minutos_trabajados"]
        assert result["ordinarios_minutos"] == result["extras_minutos"] == 0


def test_month_and_year_sum_preserves_control_formula():
    items = [classify_day(day(480)), classify_day(day(660)), classify_day(day(300, sunday=True))]
    total = sum_totals(items)
    assert total["total_minutos"] == total["ordinarios_minutos"] + total["extras_minutos"] + total["dominicales_festivos_minutos"]
