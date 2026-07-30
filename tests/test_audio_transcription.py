import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from app.core.config import settings
from app.main import app
from app.routers.bitacora_area_evidencia import (
    actualizar_transcripcion,
    consultar_transcripcion,
    crear_transcripcion_pendiente,
)
from app.schemas.evidencia_transcripcion import (
    EstadoTranscripcion,
    TranscripcionCreate,
    TranscripcionUpdate,
)
from app.services.audio_transcription_service import transcribe_file


class AudioTranscriptionTest(unittest.TestCase):
    def test_openapi_publica_crud_de_transcripcion(self):
        paths = app.openapi()["paths"]
        endpoint = "/bitacora-area-evidencias/{id_evidencia}/transcripcion"
        self.assertIn(endpoint, paths)
        self.assertIn("get", paths[endpoint])
        self.assertIn("post", paths[endpoint])
        self.assertIn("patch", paths[endpoint])

    def test_motor_convierte_y_transcribe_en_espanol(self):
        previous = (
            settings.FFMPEG_PATH,
            settings.WHISPER_CLI_PATH,
            settings.WHISPER_MODEL_PATH,
            settings.TRANSCRIPTION_LANGUAGE,
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "audio.amr"
            ffmpeg = root / "ffmpeg"
            whisper = root / "whisper-cli"
            model = root / "ggml-base.bin"
            for path in (source, ffmpeg, whisper, model):
                path.write_bytes(b"test")
            settings.FFMPEG_PATH = str(ffmpeg)
            settings.WHISPER_CLI_PATH = str(whisper)
            settings.WHISPER_MODEL_PATH = str(model)
            settings.TRANSCRIPTION_LANGUAGE = "es"

            commands = []

            def fake_run(command, **kwargs):
                commands.append(command)
                if command[0] == str(ffmpeg):
                    Path(command[-1]).write_bytes(b"wav")
                else:
                    output = Path(command[command.index("-of") + 1]).with_suffix(".txt")
                    output.write_text("Prueba de transcripción.", encoding="utf-8")

            with patch(
                "app.services.audio_transcription_service.subprocess.run",
                side_effect=fake_run,
            ):
                transcript = transcribe_file(source)

        (
            settings.FFMPEG_PATH,
            settings.WHISPER_CLI_PATH,
            settings.WHISPER_MODEL_PATH,
            settings.TRANSCRIPTION_LANGUAGE,
        ) = previous
        self.assertEqual("Prueba de transcripción.", transcript)
        self.assertEqual(2, len(commands))
        self.assertIn("-l", commands[1])
        self.assertEqual("es", commands[1][commands[1].index("-l") + 1])

    def test_migracion_define_relacion_unica_y_estado(self):
        sql = Path("migrations/20260724_audio_transcription.sql").read_text(
            encoding="utf-8"
        )
        self.assertIn("CREATE TABLE IF NOT EXISTS evidencia_transcripcion", sql)
        self.assertGreaterEqual(sql.count("BIGINT UNSIGNED"), 3)
        self.assertIn("ENGINE=InnoDB", sql)
        self.assertIn("uq_evidencia_transcripcion_evidencia", sql)
        self.assertIn("fk_evidencia_transcripcion_evidencia", sql)
        self.assertIn("fk_evidencia_transcripcion_bitacora", sql)
        self.assertIn("information_schema.KEY_COLUMN_USAGE", sql)
        self.assertIn("ROLLBACK", sql)
        self.assertNotIn("DROP TABLE", sql.upper())
        self.assertNotIn("TRUNCATE", sql.upper())

    def test_crea_pendiente_solo_para_audio(self):
        db = MagicMock()
        expected = {
            "id_transcripcion": 7,
            "id_evidencia": 11,
            "id_bitacora": 4,
            "estado": "PENDIENTE",
        }
        with patch(
            "app.routers.bitacora_area_evidencia._get",
            return_value={"id_evidencia": 11, "id_bitacora": 4, "id_tipo_evidencia": 2},
        ), patch(
            "app.routers.bitacora_area_evidencia._get_transcripcion",
            side_effect=[None, expected],
        ):
            result = crear_transcripcion_pendiente(
                11,
                TranscripcionCreate(proveedor="local", modelo="whisper", idioma="es"),
                db,
            )
        self.assertEqual(expected, result)
        params = db.execute.call_args.args[1]
        self.assertEqual(11, params["id_evidencia"])
        self.assertEqual(4, params["id_bitacora"])
        db.commit.assert_called_once()

    def test_consulta_transcripcion(self):
        expected = {"id_evidencia": 11, "estado": "PROCESANDO"}
        with patch(
            "app.routers.bitacora_area_evidencia._get_transcripcion",
            return_value=expected,
        ):
            self.assertEqual(expected, consultar_transcripcion(11, MagicMock()))

    def test_completa_y_guarda_texto(self):
        db = MagicMock()
        expected = {
            "id_evidencia": 11,
            "estado": "COMPLETADA",
            "texto_transcrito": "Turno normal.",
        }
        with patch(
            "app.routers.bitacora_area_evidencia._get_transcripcion",
            side_effect=[{"id_evidencia": 11}, expected],
        ):
            result = actualizar_transcripcion(
                11,
                TranscripcionUpdate(
                    estado=EstadoTranscripcion.COMPLETADA,
                    texto_transcrito="  Turno normal.  ",
                    confianza=0.91,
                ),
                db,
            )
        self.assertEqual(expected, result)
        params = db.execute.call_args.args[1]
        self.assertEqual("Turno normal.", params["texto_transcrito"])
        self.assertEqual(0, params["incrementar_reintento"])

    def test_error_incrementa_numero_reintentos(self):
        db = MagicMock()
        with patch(
            "app.routers.bitacora_area_evidencia._get_transcripcion",
            side_effect=[
                {"id_evidencia": 11},
                {"id_evidencia": 11, "estado": "ERROR", "numero_reintentos": 3},
            ],
        ):
            actualizar_transcripcion(
                11,
                TranscripcionUpdate(
                    estado=EstadoTranscripcion.ERROR,
                    error="No se pudo decodificar",
                ),
                db,
            )
        params = db.execute.call_args.args[1]
        self.assertEqual(1, params["incrementar_reintento"])
        self.assertEqual("No se pudo decodificar", params["ultimo_error"])


if __name__ == "__main__":
    unittest.main()
