import io
import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException, UploadFile
from pydantic import ValidationError
from starlette.datastructures import Headers

from app.core.config import settings
from app.schemas.bitacora_area_evidencia import BitacoraAreaEvidenciaCreate
from app.services.evidencia_file_service import resolve_evidencia_path, save_validated_evidence
from app.routers.bitacora_area_evidencia import _insert, eliminar_evidencia, listar_evidencias
from app.routers.bitacora_uc03 import eliminar_bitacora_diaria
from unittest.mock import MagicMock, patch

VALID_JPEG = (
    b"\xff\xd8\xff\xe0"
    b"\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00"
    b"\xff\xd9"
)


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
    def test_elimina_bitacora_evidencias_observaciones_y_archivo(self):
        db = MagicMock()
        table_check = MagicMock()
        table_check.first.return_value = (1,)
        parent = MagicMock()
        parent.mappings.return_value.first.return_value = {"id_bitacora": 68}
        children = MagicMock()
        children.mappings.return_value.all.return_value = [
            {"id_evidencia": 16, "archivo_url": "1/foto.jpg"}
        ]
        delete_evidences = MagicMock()
        delete_observations = MagicMock()
        delete_parent = MagicMock(rowcount=1)
        db.execute.side_effect = [
            table_check,
            parent,
            children,
            delete_evidences,
            delete_observations,
            delete_parent,
        ]
        with tempfile.TemporaryDirectory() as directory:
            evidence_file = Path(directory) / "foto.jpg"
            evidence_file.write_bytes(VALID_JPEG)
            with patch(
                "app.routers.bitacora_uc03.candidate_evidence_paths",
                return_value=[evidence_file],
            ):
                result = eliminar_bitacora_diaria(68, db)

        self.assertEqual(68, result["id_bitacora"])
        self.assertEqual(1, result["evidencias_eliminadas"])
        self.assertEqual(1, result["archivos_eliminados"])
        self.assertFalse(evidence_file.exists())
        db.commit.assert_called_once()
        db.rollback.assert_not_called()

    def test_no_elimina_hijos_si_bitacora_no_existe(self):
        db = MagicMock()
        table_check = MagicMock()
        table_check.first.return_value = None
        parent = MagicMock()
        parent.mappings.return_value.first.return_value = None
        db.execute.side_effect = [table_check, parent]

        with self.assertRaises(HTTPException) as raised:
            eliminar_bitacora_diaria(999, db)

        self.assertEqual(404, raised.exception.status_code)
        self.assertEqual(2, db.execute.call_count)
        db.commit.assert_not_called()

    def test_elimina_evidencia_de_texto_sin_archivo(self):
        db = MagicMock()
        with patch(
            "app.routers.bitacora_area_evidencia._get",
            return_value={"id_evidencia": 10, "archivo_url": None},
        ):
            response = eliminar_evidencia(10, db)

        self.assertEqual(204, response.status_code)
        db.commit.assert_called_once()

    def test_lista_todas_las_evidencias_de_una_bitacora(self):
        db = MagicMock()
        expected = [
            {"id_evidencia": 101, "id_bitacora": 68},
            {"id_evidencia": 102, "id_bitacora": 68},
        ]
        db.execute.return_value.mappings.return_value.all.return_value = expected

        result = listar_evidencias(id_bitacora=68, offset=0, limit=200, db=db)

        self.assertEqual(expected, result)
        params = db.execute.call_args.args[1]
        self.assertEqual(68, params["id_bitacora"])
        self.assertEqual(200, params["limit"])
        self.assertEqual(0, params["offset"])

    def test_rechaza_paginacion_invalida(self):
        with self.assertRaises(HTTPException) as raised:
            listar_evidencias(id_bitacora=68, offset=-1, limit=200, db=MagicMock())
        self.assertEqual(422, raised.exception.status_code)

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
            upload = UploadFile(
                filename="../foto.jpg",
                file=io.BytesIO(VALID_JPEG),
                headers=Headers({"content-type": "image/jpeg"}),
            )
            relative, original, mime, size, digest, path = save_validated_evidence(upload, 1)
            self.assertEqual(original, "foto.jpg")
            self.assertEqual(mime, "image/jpeg")
            self.assertEqual(size, len(VALID_JPEG))
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

    def test_rechaza_mime_jpeg_con_firma_falsificada(self):
        upload = UploadFile(
            filename="falso.jpg",
            file=io.BytesIO(b"contenido que no es jpeg"),
            headers=Headers({"content-type": "image/jpeg"}),
        )
        with self.assertRaises(HTTPException) as raised:
            save_validated_evidence(upload, 1)
        self.assertEqual(raised.exception.status_code, 415)

    def test_rechaza_tipo_fuera_del_contrato_multimedia(self):
        upload = UploadFile(filename="observacion.txt", file=io.BytesIO(b"Novedad del turno"))
        upload.headers = {"content-type": "text/plain"}
        with self.assertRaises(HTTPException) as raised:
            save_validated_evidence(upload, 4)
        self.assertEqual(raised.exception.status_code, 422)
        self.assertIn("id_tipo_evidencia", raised.exception.detail)

    def test_guarda_audio_3gpp_real_del_grabador_android(self):
        previous = settings.EVIDENCIAS_DIR
        valid_3gpp = b"\x00\x00\x00\x18ftyp3gp4\x00\x00\x00\x00isom3gp4"
        with tempfile.TemporaryDirectory() as directory:
            settings.EVIDENCIAS_DIR = directory
            upload = UploadFile(
                filename="grabacion.3gp",
                file=io.BytesIO(valid_3gpp),
                headers=Headers({"content-type": "audio/3gpp"}),
            )
            relative, original, mime, size, _, path = save_validated_evidence(upload, 2)
            self.assertEqual(original, "grabacion.3gp")
            self.assertEqual(mime, "audio/3gpp")
            self.assertEqual(size, len(valid_3gpp))
            self.assertTrue(path.is_file())
            self.assertTrue(relative.startswith("2/"))
        settings.EVIDENCIAS_DIR = previous

    def test_guarda_audio_amr_con_extension_y_mime_coherentes(self):
        previous = settings.EVIDENCIAS_DIR
        valid_amr = b"#!AMR\n" + b"\x3c\x00\x00\x00"
        with tempfile.TemporaryDirectory() as directory:
            settings.EVIDENCIAS_DIR = directory
            upload = UploadFile(
                filename="grabacion.amr",
                file=io.BytesIO(valid_amr),
                headers=Headers({"content-type": "audio/amr"}),
            )
            relative, original, mime, size, _, path = save_validated_evidence(upload, 2)
            self.assertEqual(original, "grabacion.amr")
            self.assertEqual(mime, "audio/amr")
            self.assertEqual(size, len(valid_amr))
            self.assertTrue(path.is_file())
            self.assertTrue(relative.startswith("2/"))
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

    def test_texto_se_guarda_sin_archivo_y_con_trim(self):
        db = MagicMock()
        db.execute.return_value.lastrowid = 103
        payload = BitacoraAreaEvidenciaCreate(
            id_bitacora=4,
            id_area=3,
            ts_in_min=100,
            id_tipo_evidencia=4,
            contenido_texto="  Novedad offline  ",
            uuid_cliente="550e8400-e29b-41d4-a716-446655440004",
        )
        with patch("app.routers.bitacora_area_evidencia._get_by_uuid", return_value=None), \
             patch("app.routers.bitacora_area_evidencia._validate_parent"), \
             patch(
                 "app.routers.bitacora_area_evidencia._get",
                 return_value={"id_evidencia": 103, "contenido_texto": "Novedad offline"},
             ):
            result = _insert(db, payload)
        params = db.execute.call_args.args[1]
        self.assertEqual("Novedad offline", params["contenido_texto"])
        self.assertIsNone(params["archivo_url"])
        self.assertEqual(103, result["id_evidencia"])

    def test_texto_vacio_no_se_guarda(self):
        payload = BitacoraAreaEvidenciaCreate(
            id_bitacora=4,
            id_area=3,
            ts_in_min=100,
            id_tipo_evidencia=4,
            contenido_texto="   ",
            uuid_cliente="550e8400-e29b-41d4-a716-446655440005",
        )
        with patch("app.routers.bitacora_area_evidencia._get_by_uuid", return_value=None), \
             patch("app.routers.bitacora_area_evidencia._validate_parent"):
            with self.assertRaises(HTTPException) as raised:
                _insert(MagicMock(), payload)
        self.assertEqual(422, raised.exception.status_code)
