from app.services.hours_report_service import _active_assignment, render_hours_html


def test_historical_assignment_uses_assignment_valid_on_day():
    assignments = {2: [
        {"id_empleado_area": 1, "fecha_inicia": __import__('datetime').date(2026, 1, 1),
         "fecha_final": __import__('datetime').date(2026, 6, 30), "id_area": 10},
        {"id_empleado_area": 2, "fecha_inicia": __import__('datetime').date(2026, 7, 1),
         "fecha_final": None, "id_area": 20},
    ]}
    assert _active_assignment(assignments, 2, __import__('datetime').date(2026, 5, 1))["id_area"] == 10
    assert _active_assignment(assignments, 2, __import__('datetime').date(2026, 8, 1))["id_area"] == 20


def test_html_escapes_organization_names_and_has_plain_alternative():
    report = {
        "desde": "2026-08-14", "hasta": "2026-08-14", "generado_en": "2026-08-15T06:00:00-05:00",
        "resumen": {"trabajadores_con_actividad": 1, "total_minutos": 480,
                    "ordinarios_minutos": 480, "extras_minutos": 0,
                    "extras_autorizados_minutos": 0, "jornadas_incompletas": 0, "anomalias": 0},
        "organizacion": [{"nombre": "<script>alert(1)</script>", "trabajadores": 1,
                          "total_minutos": 480, "anomalias": 0}],
    }
    html, plain = render_hours_html(report)
    assert "<script>" not in html and "&lt;script&gt;" in html
    assert "Informe gerencial" in plain and "Total: 8 h 0 min" in plain


def test_html_compares_with_previous_day():
    current = {
        "desde": "2026-08-14", "hasta": "2026-08-14", "generado_en": "now",
        "resumen": {"trabajadores_con_actividad": 1, "total_minutos": 600,
                    "ordinarios_minutos": 480, "extras_minutos": 120,
                    "extras_autorizados_minutos": 0, "jornadas_incompletas": 0, "anomalias": 0},
        "organizacion": [],
    }
    previous = {**current, "resumen": {**current["resumen"], "total_minutos": 480}}
    html, plain = render_hours_html(current, previous)
    assert "Variación frente al día anterior" in html
    assert "+2 h 0 min" in html and "+2 h 0 min" in plain
