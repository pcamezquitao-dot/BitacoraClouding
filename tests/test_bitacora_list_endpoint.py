import unittest

from fastapi import HTTPException

from app.routers.bitacora_uc03 import listar_bitacoras_diarias, router


class _Mappings:
    def __init__(self, rows):
        self.rows = rows

    def all(self):
        return self.rows


class _Result:
    def __init__(self, rows):
        self.rows = rows

    def mappings(self):
        return _Mappings(self.rows)


class _Db:
    def __init__(self, rows):
        self.rows = rows
        self.sql = ""
        self.params = None

    def execute(self, statement, params):
        self.sql = str(statement)
        self.params = params
        return _Result(self.rows[params["offset"]:params["offset"] + params["limit"]])


def _row(identifier, ts=1000, observations="ok"):
    return {
        "id_bitacora": identifier,
        "id_empleado": 14,
        "id_supervisor": 2,
        "ts_in_min": ts,
        "ts_out_min": None,
        "tipo_anotacion": None,
        "observaciones": observations,
        "client_uuid": None if identifier % 2 else f"uuid-{identifier}",
    }


class BitacoraListEndpointTest(unittest.TestCase):
    def test_returns_zero_one_twenty_and_sixty_four_without_filters(self):
        for count in (0, 1, 20, 64):
            with self.subTest(count=count):
                rows = [_row(identifier) for identifier in range(5, 5 + count)]
                db = _Db(rows)
                result = listar_bitacoras_diarias(offset=0, limit=200, db=db)
                self.assertEqual(count, len(result))
                self.assertEqual({"offset": 0, "limit": 200}, db.params)
                self.assertNotIn("WHERE", db.sql.upper())

    def test_keeps_repeated_timestamp_null_observations_and_latest_id(self):
        rows = [_row(identifier, ts=777, observations=None) for identifier in range(5, 69)]
        result = listar_bitacoras_diarias(offset=0, limit=200, db=_Db(rows))
        self.assertEqual(64, len(result))
        self.assertEqual(64, len({item.id_bitacora for item in result}))
        self.assertIn(68, {item.id_bitacora for item in result})
        self.assertTrue(all(item.observaciones is None for item in result))

    def test_pagination_and_validation(self):
        rows = [_row(identifier) for identifier in range(1, 65)]
        self.assertEqual(20, len(listar_bitacoras_diarias(offset=20, limit=20, db=_Db(rows))))
        for offset, limit in ((-1, 20), (0, 0), (0, 201)):
            with self.assertRaises(HTTPException) as captured:
                listar_bitacoras_diarias(offset=offset, limit=limit, db=_Db(rows))
            self.assertEqual(422, captured.exception.status_code)

    def test_get_collection_route_is_published(self):
        paths = {(route.path, method) for route in router.routes for method in route.methods}
        self.assertIn(("/bitacora_diaria", "GET"), paths)


if __name__ == "__main__":
    unittest.main()
