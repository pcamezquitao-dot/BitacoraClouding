import os, hashlib, uuid
from typing import Tuple
from fastapi import UploadFile
from app.core.config import settings

def ensure_upload_dir():
    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)

def save_upload(file: UploadFile, subdir: str) -> Tuple[str, str, int]:
    ensure_upload_dir()
    os.makedirs(os.path.join(settings.UPLOAD_DIR, subdir), exist_ok=True)

    filename = file.filename or "file.bin"
    ext = os.path.splitext(filename)[1]
    safe_name = f"{uuid.uuid4().hex}{ext}"
    rel_path = os.path.join(subdir, safe_name).replace("\\", "/")
    abs_path = os.path.join(settings.UPLOAD_DIR, rel_path)

    h = hashlib.sha256()
    size = 0
    with open(abs_path, "wb") as out:
        while True:
            chunk = file.file.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)
            h.update(chunk)
            size += len(chunk)

    return rel_path, h.hexdigest(), size
