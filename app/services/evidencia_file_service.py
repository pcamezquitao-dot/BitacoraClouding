import hashlib
from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, UploadFile

from app.core.config import settings


TIPOS = {
    1: {
        "mimes": {"image/jpeg", "image/png", "image/webp"},
        "extensions": {".jpg", ".jpeg", ".png", ".webp"},
        "max": lambda: settings.EVIDENCIA_FOTO_MAX_BYTES,
    },
    2: {
        "mimes": {"audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav"},
        "extensions": {".mp3", ".m4a", ".mp4", ".wav"},
        "max": lambda: settings.EVIDENCIA_AUDIO_MAX_BYTES,
    },
    3: {
        "mimes": {"video/mp4"},
        "extensions": {".mp4"},
        "max": lambda: settings.EVIDENCIA_VIDEO_MAX_BYTES,
    },
    4: {
        "mimes": {"text/plain"},
        "extensions": {".txt"},
        "max": lambda: settings.EVIDENCIA_TEXTO_MAX_BYTES,
    },
}


def _matches_signature(header: bytes, mime: str, extension: str) -> bool:
    if mime == "image/jpeg" and extension in {".jpg", ".jpeg"}:
        return header.startswith(b"\xff\xd8\xff")
    if mime == "image/png" and extension == ".png":
        return header.startswith(b"\x89PNG\r\n\x1a\n")
    if mime == "image/webp" and extension == ".webp":
        return header.startswith(b"RIFF") and header[8:12] == b"WEBP"
    if mime in {"audio/wav", "audio/x-wav"} and extension == ".wav":
        return header.startswith(b"RIFF") and header[8:12] == b"WAVE"
    if mime == "audio/mpeg" and extension == ".mp3":
        return header.startswith(b"ID3") or (
            len(header) >= 2 and header[0] == 0xFF and header[1] & 0xE0 == 0xE0
        )
    if mime in {"audio/mp4", "video/mp4"} and extension in {".m4a", ".mp4"}:
        return len(header) >= 12 and header[4:8] == b"ftyp"
    if mime == "text/plain" and extension == ".txt":
        return b"\x00" not in header
    return False


def evidencia_root() -> Path:
    root = Path(settings.EVIDENCIAS_DIR).resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def resolve_evidencia_path(relative_path: str) -> Path:
    root = evidencia_root()
    candidate = (root / relative_path).resolve()
    if candidate != root and root not in candidate.parents:
        raise HTTPException(status_code=400, detail="Ruta de evidencia inválida")
    return candidate


def save_validated_evidence(file: UploadFile, id_tipo_evidencia: int):
    policy = TIPOS.get(id_tipo_evidencia)
    if not policy:
        raise HTTPException(status_code=422, detail="id_tipo_evidencia inválido")

    original = Path(file.filename or "").name
    extension = Path(original).suffix.lower()
    mime = (file.content_type or "").lower()
    if mime not in policy["mimes"] or extension not in policy["extensions"]:
        raise HTTPException(status_code=415, detail="Tipo de archivo no permitido")
    header = file.file.read(32)
    file.file.seek(0)
    if not _matches_signature(header, mime, extension):
        raise HTTPException(
            status_code=415,
            detail="La firma del archivo no coincide con el tipo declarado",
        )

    safe_name = f"{uuid4().hex}{extension}"
    relative = f"{id_tipo_evidencia}/{safe_name}"
    destination = resolve_evidencia_path(relative)
    destination.parent.mkdir(parents=True, exist_ok=True)

    digest = hashlib.sha256()
    size = 0
    limit = policy["max"]()
    try:
        with destination.open("wb") as output:
            while chunk := file.file.read(1024 * 1024):
                size += len(chunk)
                if size > limit:
                    raise HTTPException(status_code=413, detail="El archivo supera el tamaño permitido")
                digest.update(chunk)
                output.write(chunk)
    except Exception:
        destination.unlink(missing_ok=True)
        raise

    return relative, original or safe_name, mime, size, digest.hexdigest(), destination
