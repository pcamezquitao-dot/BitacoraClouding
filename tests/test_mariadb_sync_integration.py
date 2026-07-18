import base64
import io
import os
import tempfile
import unittest
from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, UploadFile
from starlette.datastructures import Headers
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import sessionmaker

from app.core.config import settings
from app.routers.bitacora_area_evidencia import crear_con_archivo
from app.schemas.face_template import FaceTemplateEnrollIn
from app.services.face_template_service import enroll_or_replace


TEST_DATABASE_URL = os.getenv("TEST_DATABASE_URL", "")
VALID_JPEG = (
    b"\xff\xd8\xff\xe0"
    b"\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00"
    b"\xff\xd9"
)


def jpeg_upload(filename: str):
    return UploadFile(
        filename=filename,
        file=io.BytesIO(VALID_JPEG),
        headers=Headers({"content-type": "image/jpeg"}),
    )


def create_test_evidence(
    *,
    db,
    filename: str,
    uuid_cliente,
    id_bitacora: int = 31,
    orden: int | None = None,
    latitud: float | None = None,
    longitud: float | None = None,
    precision_gps: float | None = None,
):
    """Call the router directly using normal Python values for every optional field."""
    return crear_con_archivo(
        file=jpeg_upload(filename),
        id_bitacora=id_bitacora,
        id_area=7,
        ts_in_min=1,
        id_tipo_evidencia=1,
        uuid_cliente=uuid_cliente,
        archivo_nombre=None,
        archivo_hash=None,
        mime_type="image/jpeg",
        duracion_seg=None,
        tamanio_bytes=None,
        orden=orden,
        latitud=latitud,
        longitud=longitud,
        precision_gps=precision_gps,
        db=db,
    )


@unittest.skipUnless(TEST_DATABASE_URL, "TEST_DATABASE_URL no configurada")
class MariaDbSyncIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        url = make_url(TEST_DATABASE_URL)
        if not (url.database or "").lower().endswith("_test"):
            raise unittest.SkipTest("TEST_DATABASE_URL debe apuntar a una base terminada en _test")
        cls.engine = create_engine(TEST_DATABASE_URL, pool_pre_ping=True)
        cls.Session = sessionmaker(bind=cls.engine)
        cls.suffix = uuid4().hex[:10]
        cls.participant_table = f"it_participante_{cls.suffix}"
        cls.face_table = f"it_face_{cls.suffix}"
        cls.bitacora_table = f"it_bitacora_{cls.suffix}"
        cls.evidence_table = f"it_evidence_{cls.suffix}"
        cls.previous = (
            settings.PARTICIPANTE_TABLE,
            settings.FACE_TEMPLATE_TABLE,
            settings.BITACORA_DIARIA_TABLE,
            settings.BAE_TABLE,
            settings.EVIDENCIAS_DIR,
            settings.FACE_TEMPLATE_MASTER_KEY,
        )
        cls.temp_dir = tempfile.TemporaryDirectory()
        settings.PARTICIPANTE_TABLE = cls.participant_table
        settings.FACE_TEMPLATE_TABLE = cls.face_table
        settings.BITACORA_DIARIA_TABLE = cls.bitacora_table
        settings.BAE_TABLE = cls.evidence_table
        settings.EVIDENCIAS_DIR = cls.temp_dir.name
        settings.FACE_TEMPLATE_MASTER_KEY = base64.b64encode(bytes(range(32))).decode()
        with cls.engine.begin() as db:
            db.execute(text(f"""
                CREATE TABLE {cls.participant_table} (
                    id_participante INT PRIMARY KEY,
                    identificacion_participante VARCHAR(100) NOT NULL UNIQUE
                ) ENGINE=InnoDB
            """))
            db.execute(text(f"""
                CREATE TABLE {cls.bitacora_table} (
                    id_bitacora INT PRIMARY KEY
                ) ENGINE=InnoDB
            """))
            db.execute(text(f"""
                CREATE TABLE {cls.face_table} (
                    id_face_template INT AUTO_INCREMENT PRIMARY KEY,
                    id_participante INT NOT NULL,
                    participant_code VARCHAR(100) NOT NULL,
                    display_name VARCHAR(255) NOT NULL,
                    encrypted_embedding LONGBLOB NOT NULL,
                    embedding_sha256 CHAR(64) NOT NULL,
                    model_version VARCHAR(100) NOT NULL,
                    encryption_version VARCHAR(50) NOT NULL,
                    enrolled_at DATETIME NOT NULL,
                    active TINYINT(1) NOT NULL,
                    created_by VARCHAR(100),
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    device_id VARCHAR(255),
                    revoked_at DATETIME,
                    revoked_by VARCHAR(100),
                    revocation_reason VARCHAR(255),
                    FOREIGN KEY (id_participante)
                        REFERENCES {cls.participant_table}(id_participante)
                ) ENGINE=InnoDB
            """))
            db.execute(text(f"""
                CREATE TABLE {cls.evidence_table} (
                    id_evidencia INT AUTO_INCREMENT PRIMARY KEY,
                    id_bitacora INT NOT NULL,
                    id_area INT NOT NULL,
                    ts_in_min BIGINT NOT NULL,
                    id_tipo_evidencia TINYINT NOT NULL,
                    archivo_url VARCHAR(500) NOT NULL,
                    archivo_nombre VARCHAR(255),
                    archivo_hash CHAR(64),
                    mime_type VARCHAR(100),
                    duracion_seg INT,
                    tamanio_bytes BIGINT,
                    orden INT,
                    latitud DOUBLE,
                    longitud DOUBLE,
                    precision_gps DOUBLE,
                    uuid_cliente CHAR(36) NOT NULL UNIQUE,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (id_bitacora)
                        REFERENCES {cls.bitacora_table}(id_bitacora)
                ) ENGINE=InnoDB
            """))
            db.execute(text(
                f"INSERT INTO {cls.participant_table} "
                "(id_participante, identificacion_participante) VALUES (2, 'P0002')"
            ))
            db.execute(text(
                f"INSERT INTO {cls.bitacora_table} (id_bitacora) VALUES (31)"
            ))

    @classmethod
    def tearDownClass(cls):
        if hasattr(cls, "engine"):
            with cls.engine.begin() as db:
                db.execute(text(f"DROP TABLE IF EXISTS {cls.evidence_table}"))
                db.execute(text(f"DROP TABLE IF EXISTS {cls.face_table}"))
                db.execute(text(f"DROP TABLE IF EXISTS {cls.bitacora_table}"))
                db.execute(text(f"DROP TABLE IF EXISTS {cls.participant_table}"))
            cls.engine.dispose()
            (
                settings.PARTICIPANTE_TABLE,
                settings.FACE_TEMPLATE_TABLE,
                settings.BITACORA_DIARIA_TABLE,
                settings.BAE_TABLE,
                settings.EVIDENCIAS_DIR,
                settings.FACE_TEMPLATE_MASTER_KEY,
            ) = cls.previous
            cls.temp_dir.cleanup()

    def face_payload(self, value: float = 0.25):
        raw = value.hex().encode() * 4
        return FaceTemplateEnrollIn(
            client_uuid=str(uuid4()),
            id_participante=2,
            participant_code="P0002",
            display_name="Participante prueba",
            embedding_base64=base64.b64encode(raw).decode(),
            model_version="integration-test",
        )

    def test_face_insert_replace_history_and_idempotency(self):
        db = self.Session()
        try:
            first = enroll_or_replace(db, self.face_payload())
            db.commit()
            repeated = enroll_or_replace(db, self.face_payload())
            db.commit()
            self.assertEqual(first["id_face_template"], repeated["id_face_template"])
            replacement = enroll_or_replace(db, self.face_payload(0.5))
            db.commit()
            self.assertNotEqual(first["id_face_template"], replacement["id_face_template"])
            counts = db.execute(text(
                f"SELECT COUNT(*) total, SUM(active = 1) active "
                f"FROM {self.face_table}"
            )).mappings().one()
            self.assertEqual(2, counts["total"])
            self.assertEqual(1, int(counts["active"]))
        finally:
            db.close()

    def test_evidence_file_metadata_rollback_and_no_duplicates(self):
        db = self.Session()
        client_uuid = uuid4()
        try:
            first = create_test_evidence(
                db=db,
                filename="foto.jpg",
                uuid_cliente=client_uuid,
            )
            self.assertTrue((Path(settings.EVIDENCIAS_DIR) / first["archivo_url"]).is_file())
            without_gps = db.execute(text(
                f"SELECT orden, latitud, longitud, precision_gps "
                f"FROM {self.evidence_table} WHERE uuid_cliente = :uuid_cliente"
            ), {"uuid_cliente": str(client_uuid)}).mappings().one()
            with self.subTest("campos GPS ausentes"):
                self.assertIsNone(without_gps["orden"])
                self.assertIsNone(without_gps["latitud"])
                self.assertIsNone(without_gps["longitud"])
                self.assertIsNone(without_gps["precision_gps"])

            repeated = create_test_evidence(
                db=db,
                filename="otra.jpg",
                uuid_cliente=client_uuid,
            )
            self.assertEqual(first["id_evidencia"], repeated["id_evidencia"])

            gps_uuid = uuid4()
            with_gps = create_test_evidence(
                db=db,
                filename="foto-gps.jpg",
                uuid_cliente=gps_uuid,
                orden=2,
                latitud=4.7110,
                longitud=-74.0721,
                precision_gps=6.5,
            )
            self.assertTrue((Path(settings.EVIDENCIAS_DIR) / with_gps["archivo_url"]).is_file())
            gps_values = db.execute(text(
                f"SELECT orden, latitud, longitud, precision_gps "
                f"FROM {self.evidence_table} WHERE uuid_cliente = :uuid_cliente"
            ), {"uuid_cliente": str(gps_uuid)}).mappings().one()
            with self.subTest("campos GPS válidos"):
                self.assertEqual(gps_values["orden"], 2)
                self.assertAlmostEqual(gps_values["latitud"], 4.7110)
                self.assertAlmostEqual(gps_values["longitud"], -74.0721)
                self.assertAlmostEqual(gps_values["precision_gps"], 6.5)

            before_files = set(Path(settings.EVIDENCIAS_DIR).rglob("*.*"))
            with self.assertRaises(HTTPException):
                create_test_evidence(
                    db=db,
                    filename="fallo.jpg",
                    uuid_cliente=uuid4(),
                    id_bitacora=999999,
                )
            self.assertEqual(before_files, set(Path(settings.EVIDENCIAS_DIR).rglob("*.*")))
            total = db.execute(text(f"SELECT COUNT(*) FROM {self.evidence_table}")).scalar_one()
            self.assertEqual(2, total)
        finally:
            db.close()
