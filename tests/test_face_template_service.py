import base64
from datetime import datetime
import unittest

from app.core.config import settings
from app.schemas.face_template import FaceTemplateEnrollIn, FaceTemplateMetadataOut
from app.services.face_template_service import (
    enroll_or_replace,
    get_active_for_participant,
    list_authorized_active,
)


class FakeResult:
    def __init__(self, rows=None, lastrowid=None, rowcount=0):
        self.rows = rows or []
        self.lastrowid = lastrowid
        self.rowcount = rowcount

    def first(self):
        return self.rows[0] if self.rows else None

    def mappings(self):
        return self

    def all(self):
        return self.rows


class FakeFaceDb:
    def __init__(self):
        self.templates = []
        self.participants = {2}

    def execute(self, statement, params=None):
        sql = " ".join(str(statement).split())
        params = params or {}
        if sql.startswith("SELECT id_participante FROM"):
            participant_id = params["id_participante"]
            return FakeResult([(participant_id,)] if participant_id in self.participants else [])
        if sql.startswith("UPDATE") and "WHERE id_participante" in sql:
            changed = 0
            for row in self.templates:
                if row["id_participante"] == params["id_participante"] and row["active"]:
                    row["active"] = False
                    changed += 1
            return FakeResult(rowcount=changed)
        if sql.startswith("INSERT INTO"):
            row = {
                "id_face_template": len(self.templates) + 1,
                "id_participante": params["id_participante"],
                "participant_code": params["participant_code"],
                "display_name": params["display_name"],
                "encrypted_embedding": params["encrypted_embedding"],
                "embedding_sha256": params["embedding_sha256"],
                "model_version": params["model_version"],
                "encryption_version": params["encryption_version"],
                "enrolled_at": datetime(2026, 1, 1),
                "active": True,
                "device_id": params["device_id"],
            }
            self.templates.append(row)
            return FakeResult(lastrowid=row["id_face_template"], rowcount=1)
        if "WHERE id_face_template = :id_face_template" in sql:
            rows = [
                self._metadata(row)
                for row in self.templates
                if row["id_face_template"] == params["id_face_template"]
            ]
            return FakeResult(rows)
        if "WHERE id_participante = :id_participante AND active = 1" in sql:
            rows = [
                self._metadata(row)
                for row in reversed(self.templates)
                if row["id_participante"] == params["id_participante"] and row["active"]
            ]
            return FakeResult(rows[:1])
        if "WHERE active = 1" in sql:
            return FakeResult([row.copy() for row in self.templates if row["active"]])
        raise AssertionError(f"SQL no contemplado por la prueba: {sql}")

    @staticmethod
    def _metadata(row):
        return {key: value for key, value in row.items() if key != "encrypted_embedding"}


class FaceTemplateServiceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.previous_key = settings.FACE_TEMPLATE_MASTER_KEY
        cls.previous_table = settings.PARTICIPANTE_TABLE
        settings.FACE_TEMPLATE_MASTER_KEY = base64.b64encode(bytes(range(32))).decode()
        settings.PARTICIPANTE_TABLE = "participante"

    @classmethod
    def tearDownClass(cls):
        settings.FACE_TEMPLATE_MASTER_KEY = cls.previous_key
        settings.PARTICIPANTE_TABLE = cls.previous_table

    def payload(self, value=0.25):
        embedding = (float(value).hex() * 0).encode()
        # Cuatro floats IEEE-754; el servicio valida bytes y hash, no dimensiones del modelo.
        import struct
        embedding = struct.pack(">ffff", value, value, value, value)
        return FaceTemplateEnrollIn(
            id_participante=2,
            participant_code="p0002",
            display_name="Participante Dos",
            embedding_base64=base64.b64encode(embedding).decode(),
            model_version="FaceNet-160/128",
            device_id="device-test",
        )

    def test_insercion_inicial_devuelve_id_y_no_expone_embedding(self):
        db = FakeFaceDb()
        result = enroll_or_replace(db, self.payload())

        self.assertEqual(1, result["id_face_template"])
        self.assertTrue(result["active"])
        self.assertEqual("P0002", result["participant_code"])
        self.assertNotIn("encrypted_embedding", result)
        self.assertNotIn("encrypted_embedding", FaceTemplateMetadataOut.model_fields)

    def test_reemplazo_conserva_historial_inactivo(self):
        db = FakeFaceDb()
        enroll_or_replace(db, self.payload(0.25))
        replacement = enroll_or_replace(db, self.payload(0.5))

        self.assertEqual(2, replacement["id_face_template"])
        self.assertFalse(db.templates[0]["active"])
        self.assertTrue(db.templates[1]["active"])

    def test_recuperacion_activa_por_participante(self):
        db = FakeFaceDb()
        enroll_or_replace(db, self.payload())

        result = get_active_for_participant(db, 2)

        self.assertEqual(2, result["id_participante"])
        self.assertEqual("P0002", result["participant_code"])

    def test_sincronizacion_central_descifra_y_valida_hash(self):
        db = FakeFaceDb()
        created = enroll_or_replace(db, self.payload())

        downloaded = list_authorized_active(db)

        self.assertEqual(1, len(downloaded))
        self.assertEqual(created["embedding_sha256"], downloaded[0]["embedding_sha256"])
        self.assertEqual(self.payload().embedding_base64, downloaded[0]["embedding_base64"])

    def test_rechaza_participante_inexistente(self):
        db = FakeFaceDb()
        payload = self.payload().model_copy(update={"id_participante": 999})

        with self.assertRaisesRegex(LookupError, "participante no existe"):
            enroll_or_replace(db, payload)


if __name__ == "__main__":
    unittest.main()
