import io
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile
from pydantic import ValidationError

from app.core.config import settings
from app.schemas.bitacora_area_evidencia import BitacoraAreaEvidenciaCreate
from app.services.evidencia_file_service import resolve_evidencia_path, save_validated_evidence
from app.routers.bitacora_area_evidencia import _insert
from unittest.mock import MagicMock, patch


def metadata(**overrides):
    values = {
        "id_bitacora": 4,
        "id_area": 3,
        "ts_in_min": 100,
        "id_tipo_evidencia": 1,
        "archivo_url": "1/evidencia.jpg",
        "uuid_cliente": "550e8400-e29b-41d4-a716-446655440000",
    }
    values.update(overrides)
    return values


class BitacoraAreaEvidenciaCrudTest(unittest.TestCase):
    def test_rechaza_latitud_invalida(self):
        with self.assertRaises(ValidationError):
            BitacoraAreaEvidenciaCreate(**metadata(latitud=91))

    def test_rechaza_longitud_invalida(self):
        with self.assertRaises(ValidationError):
            BitacoraAreaEvidenciaCreate(**metadata(longitud=-181))

    def test_guarda_archivo_con_nombre_uuid_y_hash(self):
        previous = settings.EVIDENCIAS_DIR
        with tempfile.TemporaryDirectory() as directory:
            settings.EVIDENCIAS_DIR = directory
            upload = UploadFile(filename="../foto.jpg", file=io.BytesIO(b"jpeg-test"))
            upload.headers = {"content-type": "image/jpeg"}
            relative, original, mime, size, digest, path = save_validated_evidence(upload, 1)
            self.assertEqual(original, "foto.jpg")
            self.assertEqual(mime, "image/jpeg")
            self.assertEqual(size, 9)
            self.assertEqual(len(digest), 64)
            self.assertTrue(path.is_file())
            self.assertNotIn("..", relative)
        settings.EVIDENCIAS_DIR = previous

    def test_rechaza_mime_no_permitido(self):
        upload = UploadFile(filename="malware.exe", file=io.BytesIO(b"bad"))
        upload.headers = {"content-type": "application/octet-stream"}
        with self.assertRaises(HTTPException) as raised:
            save_validated_evidence(upload, 1)
        self.assertEqual(raised.exception.status_code, 415)

    def test_guarda_observacion_textual_como_archivo(self):
        previous = settings.EVIDENCIAS_DIR
        with tempfile.TemporaryDirectory() as directory:
            settings.EVIDENCIAS_DIR = directory
            upload = UploadFile(filename="observacion.txt", file=io.BytesIO(b"Novedad del turno"))
            upload.headers = {"content-type": "text/plain"}
            relative, original, mime, size, _, path = save_validated_evidence(upload, 4)
            self.assertEqual(original, "observacion.txt")
            self.assertEqual(mime, "text/plain")
            self.assertEqual(size, 17)
            self.assertTrue(path.is_file())
            self.assertTrue(relative.startswith("4/"))
        settings.EVIDENCIAS_DIR = previous

    def test_rechaza_ruta_fuera_del_directorio(self):
        previous = settings.EVIDENCIAS_DIR
        with tempfile.TemporaryDirectory() as directory:
            settings.EVIDENCIAS_DIR = directory
            with self.assertRaises(HTTPException):
                resolve_evidencia_path("../../escape.jpg")
        settings.EVIDENCIAS_DIR = previous

    def test_uuid_existente_es_idempotente(self):
        db = MagicMock()
        existing = {"id_evidencia": 99, "uuid_cliente": "550e8400-e29b-41d4-a716-446655440000"}
        with patch(
            "app.routers.bitacora_area_evidencia._get_by_uuid",
            return_value=existing,
        ), patch("app.routers.bitacora_area_evidencia._validate_parent") as validate:
            result = _insert(db, BitacoraAreaEvidenciaCreate(**metadata(uuid_cliente=existing["uuid_cliente"])))
        self.assertEqual(result, existing)
        validate.assert_not_called()
        db.execute.assert_not_called()

    def test_permite_varias_evidencias_para_una_misma_bitacora(self):
        db = MagicMock()
        db.execute.side_effect = [
            MagicMock(lastrowid=101),
            MagicMock(lastrowid=102),
        ]
        first = BitacoraAreaEvidenciaCreate(**metadata())
        second = BitacoraAreaEvidenciaCreate(
            **metadata(
                archivo_url="1/evidencia-2.jpg",
                uuid_cliente="550e8400-e29b-41d4-a716-446655440001",
            )
        )
        with patch(
            "app.routers.bitacora_area_evidencia._get_by_uuid",
            return_value=None,
        ), patch(
            "app.routers.bitacora_area_evidencia._validate_parent"
        ), patch(
            "app.routers.bitacora_area_evidencia._get",
            side_effect=[
                {"id_evidencia": 101, "id_bitacora": 4},
                {"id_evidencia": 102, "id_bitacora": 4},
            ],
        ):
            saved = [_insert(db, first), _insert(db, second)]

        self.assertEqual([101, 102], [row["id_evidencia"] for row in saved])
        self.assertTrue(all(row["id_bitacora"] == 4 for row in saved))
        self.assertEqual(2, db.execute.call_count)
