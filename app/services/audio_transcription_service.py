import logging
import subprocess
import tempfile
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

from sqlalchemy import text

from app.core.config import settings
from app.core.db import SessionLocal
from app.services.evidencia_file_service import candidate_evidence_paths


logger = logging.getLogger("bitacora.transcription")
PENDING = "PENDIENTE"
PROCESSING = "PROCESANDO"
COMPLETED = "COMPLETADA"
ERROR = "ERROR"


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
                str(ffmpeg),
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                str(source),
                "-ar",
                "16000",
                "-ac",
                "1",
                "-c:a",
                "pcm_s16le",
                str(wav),
            ],
            check=True,
            capture_output=True,
            text=True,
            timeout=600,
        )
        subprocess.run(
            [
                str(whisper),
                "-m",
                str(model),
                "-f",
                str(wav),
                "-l",
                settings.TRANSCRIPTION_LANGUAGE,
                "-otxt",
                "-of",
                str(output),
                "-np",
            ],
            check=True,
            capture_output=True,
            text=True,
            timeout=1800,
        )
        transcript_path = output.with_suffix(".txt")
        transcript = transcript_path.read_text(encoding="utf-8").strip()
        if not transcript:
            raise RuntimeError("El motor no produjo texto")
        return transcript


def _mark_error(id_evidencia: int, error: Exception) -> None:
    diagnostic = str(error).strip() or error.__class__.__name__
    with SessionLocal() as db:
        db.execute(
            text(
                f"""
                UPDATE {settings.BAE_TABLE}
                SET transcripcion_estado=:estado,
                    transcripcion_fecha=NOW(),
                    transcripcion_error=:error
                WHERE id_evidencia=:id
                """
            ),
            {"estado": ERROR, "error": diagnostic[:2000], "id": id_evidencia},
        )
        db.commit()
    logger.exception("audio transcription failed evidence_id=%s", id_evidencia)


def transcribe_audio_evidence(id_evidencia: int) -> None:
    try:
        with SessionLocal() as db:
            audio = db.execute(
                text(
                    f"""
                    SELECT id_evidencia, id_bitacora, id_area, ts_in_min, archivo_url,
                           archivo_hash, orden, transcripcion_estado
                    FROM {settings.BAE_TABLE}
                    WHERE id_evidencia=:id AND id_tipo_evidencia=2
                    LIMIT 1
                    """
                ),
                {"id": id_evidencia},
            ).mappings().first()
            if not audio:
                return
            existing = db.execute(
                text(
                    f"""
                    SELECT id_evidencia
                    FROM {settings.BAE_TABLE}
                    WHERE id_evidencia_origen=:id
                    LIMIT 1
                    """
                ),
                {"id": id_evidencia},
            ).first()
            if existing:
                db.execute(
                    text(
                        f"""
                        UPDATE {settings.BAE_TABLE}
                        SET transcripcion_estado=:estado,
                            transcripcion_fecha=NOW(),
                            transcripcion_error=NULL
                        WHERE id_evidencia=:id
                        """
                    ),
                    {"estado": COMPLETED, "id": id_evidencia},
                )
                db.commit()
                return
            claimed = db.execute(
                text(
                    f"""
                    UPDATE {settings.BAE_TABLE}
                    SET transcripcion_estado=:procesando,
                        transcripcion_motor=:motor,
                        transcripcion_idioma=:idioma,
                        transcripcion_fecha=NOW(),
                        transcripcion_error=NULL,
                        transcripcion_intentos=transcripcion_intentos+1
                    WHERE id_evidencia=:id
                      AND transcripcion_estado IN (:pendiente, :error)
                    """
                ),
                {
                    "procesando": PROCESSING,
                    "motor": settings.TRANSCRIPTION_ENGINE,
                    "idioma": settings.TRANSCRIPTION_LANGUAGE,
                    "id": id_evidencia,
                    "pendiente": PENDING,
                    "error": ERROR,
                },
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
        transcript_uuid = str(
            uuid5(
                NAMESPACE_URL,
                f"bitacora:transcripcion:{id_evidencia}:{audio['archivo_hash'] or ''}",
            )
        )

        with SessionLocal() as db:
            db.execute(
                text(
                    f"""
                    INSERT INTO {settings.BAE_TABLE}
                        (id_evidencia_origen, id_bitacora, id_area, ts_in_min,
                         id_tipo_evidencia, contenido_texto, orden, uuid_cliente)
                    VALUES
                        (:origen, :bitacora, :area, :ts, 4, :texto, :orden, :uuid)
                    ON DUPLICATE KEY UPDATE
                        contenido_texto=VALUES(contenido_texto)
                    """
                ),
                {
                    "origen": id_evidencia,
                    "bitacora": audio["id_bitacora"],
                    "area": audio["id_area"],
                    "ts": audio["ts_in_min"],
                    "texto": transcript,
                    "orden": (audio["orden"] or 0) + 1,
                    "uuid": transcript_uuid,
                },
            )
            db.execute(
                text(
                    f"""
                    UPDATE {settings.BAE_TABLE}
                    SET transcripcion_estado=:estado,
                        transcripcion_fecha=NOW(),
                        transcripcion_error=NULL
                    WHERE id_evidencia=:id
                    """
                ),
                {"estado": COMPLETED, "id": id_evidencia},
            )
            db.commit()
        logger.info("audio transcription completed evidence_id=%s", id_evidencia)
    except Exception as error:
        _mark_error(id_evidencia, error)


def recover_pending_transcriptions(limit: int = 20) -> None:
    with SessionLocal() as db:
        pending = db.execute(
            text(
                f"""
                SELECT id_evidencia
                FROM {settings.BAE_TABLE}
                WHERE id_tipo_evidencia=2
                  AND transcripcion_estado=:estado
                ORDER BY created_at ASC
                LIMIT :limit
                """
            ),
            {"estado": PENDING, "limit": limit},
        ).scalars().all()
    for id_evidencia in pending:
        transcribe_audio_evidence(int(id_evidencia))
