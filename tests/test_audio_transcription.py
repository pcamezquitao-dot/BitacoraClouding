import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.core.config import settings
from app.main import app
from app.services.audio_transcription_service import transcribe_file


class AudioTranscriptionTest(unittest.TestCase):
    def test_openapi_publica_reintento_de_transcripcion(self):
        paths = app.openapi()["paths"]
        endpoint = "/bitacora-area-evidencias/{id_evidencia}/transcripcion/reintentar"
        self.assertIn(endpoint, paths)
        self.assertIn("post", paths[endpoint])

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
        self.assertIn("id_evidencia_origen", sql)
        self.assertIn("uq_bae_transcripcion_origen", sql)
        self.assertIn("transcripcion_estado", sql)
        self.assertIn("fk_bae_tipo_evidencia", sql)


if __name__ == "__main__":
    unittest.main()
