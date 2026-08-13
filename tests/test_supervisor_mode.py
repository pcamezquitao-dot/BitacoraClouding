from unittest.mock import MagicMock

import pytest

from app.main import app
from app.services.supervisor_service import (
    SupervisorAuthorizationError,
    identify_supervisor,
    resolve_supervisor_type_code,
    supervised_participants,
)


def scalar_result(values):
    result = MagicMock()
    result.scalars.return_value.all.return_value = values
    return result


def mapping_result(values):
    result = MagicMock()
    result.mappings.return_value.all.return_value = values
    return result


def test_supervisor_routes_are_registered():
    paths = app.openapi()["paths"]
    assert "/supervisor/identificar" in paths
    assert "/supervisor/{codigo}/participantes" in paths
    assert "/supervisor/movimientos" in paths
    assert "/supervisor/{codigo}/movimientos-hoy" in paths


def test_supervisor_type_is_resolved_centrally_by_catalog_name():
    db = MagicMock()
    db.execute.return_value = scalar_result([2])

    assert resolve_supervisor_type_code(db) == 2
    query = str(db.execute.call_args.args[0]).upper()
    assert "FROM TIPOS_PARTICIPANTE" in query
    assert "DESCRIPCION" in query
    assert "TIPO_PARTICIPANTE_CAPACIDAD" not in query


def test_ambiguous_supervisor_type_is_rejected():
    db = MagicMock()
    db.execute.return_value = scalar_result([2, 7])
    with pytest.raises(SupervisorAuthorizationError):
        resolve_supervisor_type_code(db)


def test_p0002_identification_uses_resolved_type_code_two():
    db = MagicMock()
    db.execute.side_effect = [
        scalar_result([2]),
        mapping_result([{
            "id_participante": 2,
            "identificacion_participante": "P0002",
            "nombre_completo": "AURELIO UBAQUE PINEDA JOSE AURELIO",
            "id_area": 2,
            "area": "Finca1",
        }]),
    ]
    result = identify_supervisor(db, " p0002 ")
    assert result["id_supervisor"] == 2
    assert result["areas"] == [{"id_area": 2, "area": "Finca1"}]
    params = db.execute.call_args_list[1].args[1]
    assert params["codigo"] == "P0002"
    assert params["supervisor_type"] == 2


def test_hierarchical_query_filters_active_and_uses_parameterized_type():
    db = MagicMock()
    db.execute.side_effect = [
        scalar_result([2]),
        mapping_result([{
            "id_participante": 2, "identificacion_participante": "P0002",
            "nombre_completo": "AURELIO", "id_area": 2, "area": "Finca1",
        }]),
        scalar_result([2]),
        mapping_result([{
            "id_participante": 51, "codigo": "P0051", "nombre": "ANA",
            "apellido": "PRUEBA", "id_area": 10, "area": "Descendiente",
        }]),
    ]
    result = supervised_participants(db, "P0002", "P0051")
    assert result[0]["id_participante"] == 51
    query = str(db.execute.call_args_list[3].args[0]).upper()
    assert "WITH RECURSIVE AREAS_SUPERVISADAS" in query
    assert "EA.ACTIVO=TRUE" in query
    assert "P.FECHA_SALIDA IS NULL" in query
    assert "CARGO=:SUPERVISOR_TYPE" in query
    assert "CARGO=2" not in query
