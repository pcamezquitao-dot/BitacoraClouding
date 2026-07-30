import logging
import subprocess
import tempfile
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.db import SessionLocal
from app.services.evidencia_file_service import candidate_evidence_paths


logger = logging.getLogger("bitacora.transcription")
PENDING = "PENDIENTE"
PROCESSING = "PROCESANDO"
COMPLETED = "COMPLETADA"
ERROR = "ERROR"


def ensure_pending_transcription(
    db: Session,
    id_evidencia: int,
    id_bitacora: int,
) -> None:
    db.execute(
        text(
            """
            INSERT INTO evidencia_transcripcion
                (id_evidencia, id_bitacora, estado, proveedor, modelo, idioma)
            VALUES
                (:id_evidencia, :id_bitacora, 'PENDIENTE',
                 :proveedor, :modelo, :idioma)
            ON DUPLICATE KEY UPDATE id_evidencia=VALUES(id_evidencia)
            """
        ),
        {
            "id_evidencia": id_evidencia,
            "id_bitacora": id_bitacora,
            "proveedor": "local",
            "modelo": settings.TRANSCRIPTION_ENGINE,
            "idioma": settings.TRANSCRIPTION_LANGUAGE,
        },
    )


def transcribe_file(source: Path) -> str:
    ffmpeg = Path(settings.FFMPEG_PATH)
    whisper = Path(settings.WHISPER_CLI_PATH)
    model = Path(settings.WHISPER_MODEL_PATH)
    for required in (ffmpeg, whisper, model):
        if not required.is_file():
            raise RuntimeError(f"Componente de transcripción no disponible: {required}")

    with tempfile.TemporaryDirectory(prefix="bitacora_transcription_") as directory:
        workdir = Path(directory)
        wav = workdir / "audio.wav"
        output = workdir / "transcripcion"
        subprocess.run(
            [
                str(ffmpeg), "-nostdin", "-hide_banner", "-loglevel", "error",
                "-y", "-i", str(source), "-ar", "16000", "-ac", "1",
                "-c:a", "pcm_s16le", str(wav),
            ],
            check=True,
            capture_output=True,
            text=True,
            timeout=600,
        )
        subprocess.run(
            [
                str(whisper), "-m", str(model), "-f", str(wav), "-l",
                settings.TRANSCRIPTION_LANGUAGE, "-otxt", "-of", str(output), "-np",
            ],
            check=True,
            capture_output=True,
            text=True,
            timeout=1800,
        )
        transcript = output.with_suffix(".txt").read_text(encoding="utf-8").strip()
        if not transcript:
            raise RuntimeError("El motor no produjo texto")
        return transcript


def _mark_error(id_evidencia: int, error: Exception) -> None:
    diagnostic = str(error).strip() or error.__class__.__name__
    with SessionLocal() as db:
        db.execute(
            text(
                """
                UPDATE evidencia_transcripcion
                SET estado='ERROR',
                    ultimo_error=:error,
                    numero_reintentos=numero_reintentos+1,
                    completado_en=NULL
                WHERE id_evidencia=:id_evidencia
                """
            ),
            {"error": diagnostic[:65535], "id_evidencia": id_evidencia},
        )
        db.commit()
    logger.exception("audio transcription failed evidence_id=%s", id_evidencia)


def transcribe_audio_evidence(id_evidencia: int) -> None:
    try:
        with SessionLocal() as db:
            audio = db.execute(
                text(
                    f"""
                    SELECT e.id_evidencia, e.id_bitacora, e.archivo_url
                    FROM {settings.BAE_TABLE} AS e
                    JOIN evidencia_transcripcion AS t
                      ON t.id_evidencia=e.id_evidencia
                    WHERE e.id_evidencia=:id_evidencia
                      AND e.id_tipo_evidencia=2
                    LIMIT 1
                    """
                ),
                {"id_evidencia": id_evidencia},
            ).mappings().first()
            if not audio:
                return
            claimed = db.execute(
                text(
                    """
                    UPDATE evidencia_transcripcion
                    SET estado='PROCESANDO', ultimo_error=NULL
                    WHERE id_evidencia=:id_evidencia
                      AND estado IN ('PENDIENTE', 'ERROR')
                    """
                ),
                {"id_evidencia": id_evidencia},
            )
            db.commit()
            if claimed.rowcount != 1:
                return

        source = next(
            (
                candidate
                for candidate in candidate_evidence_paths(audio["archivo_url"])
                if candidate.is_file()
            ),
            None,
        )
        if source is None:
            raise RuntimeError("Archivo de audio no encontrado")
        transcript = transcribe_file(source)

        with SessionLocal() as db:
            db.execute(
                text(
                    """
                    UPDATE evidencia_transcripcion
                    SET estado='COMPLETADA',
                        texto_transcrito=:texto,
                        ultimo_error=NULL,
                        completado_en=CURRENT_TIMESTAMP(6)
                    WHERE id_evidencia=:id_evidencia
                    """
                ),
                {"texto": transcript, "id_evidencia": id_evidencia},
            )
            db.commit()
        logger.info("audio transcription completed evidence_id=%s", id_evidencia)
    except Exception as error:
        _mark_error(id_evidencia, error)


def recover_pending_transcriptions(limit: int = 20) -> None:
    with SessionLocal() as db:
        pending = db.execute(
            text(
                """
                SELECT id_evidencia
                FROM evidencia_transcripcion
                WHERE estado='PENDIENTE'
                ORDER BY creado_en ASC
                LIMIT :limit
                """
            ),
            {"limit": limit},
        ).scalars().all()
    for id_evidencia in pending:
        transcribe_audio_evidence(int(id_evidencia))
