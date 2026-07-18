import base64
import hashlib
import hmac
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.schemas.face_template import FaceTemplateEnrollIn


def _table() -> str:
    return settings.FACE_TEMPLATE_TABLE


def _master_key() -> bytes:
    try:
        key = base64.b64decode(settings.FACE_TEMPLATE_MASTER_KEY, validate=True)
    except Exception as error:
        raise RuntimeError("FACE_TEMPLATE_MASTER_KEY no es Base64 válido") from error
    if len(key) not in (16, 24, 32):
        raise RuntimeError("FACE_TEMPLATE_MASTER_KEY debe representar 16, 24 o 32 bytes")
    return key


def decode_embedding(value: str) -> bytes:
    try:
        embedding = base64.b64decode(value, validate=True)
    except Exception as error:
        raise ValueError("embedding_base64 no es Base64 válido") from error
    if not embedding or len(embedding) % 4 != 0:
        raise ValueError("El embedding debe contener floats de 32 bits")
    return embedding


def embedding_sha256(embedding: bytes) -> str:
    return hashlib.sha256(embedding).hexdigest()


def encrypt_embedding(embedding: bytes) -> bytes:
    nonce = os.urandom(12)
    return nonce + AESGCM(_master_key()).encrypt(nonce, embedding, None)


def decrypt_embedding(encrypted: bytes) -> bytes:
    if len(encrypted) <= 12:
        raise ValueError("Plantilla cifrada inválida")
    return AESGCM(_master_key()).decrypt(encrypted[:12], encrypted[12:], None)


def enroll_or_replace(db: Session, payload: FaceTemplateEnrollIn):
    table = _table()
    participant_table = settings.PARTICIPANTE_TABLE
    embedding = decode_embedding(payload.embedding_base64)
    digest = embedding_sha256(embedding)
    if payload.embedding_sha256 and not hmac.compare_digest(
        payload.embedding_sha256.lower(), digest
    ):
        raise ValueError("embedding_sha256 no coincide con la plantilla recibida")

    participant = db.execute(
        text(
            f"SELECT id_participante FROM {participant_table} "
            "WHERE id_participante = :id_participante LIMIT 1 FOR UPDATE"
        ),
        {"id_participante": payload.id_participante},
    ).first()
    if not participant:
        raise LookupError("El participante no existe")

    db.execute(
        text(
            f"UPDATE {table} SET active = 0, revoked_at = NOW(), "
            "revoked_by = :revoked_by, "
            "revocation_reason = :reason, updated_at = NOW() "
            "WHERE id_participante = :id_participante AND active = 1"
        ),
        {
            "id_participante": payload.id_participante,
            "revoked_by": payload.created_by,
            "reason": "Reemplazo de enrolamiento",
        },
    )
    result = db.execute(
        text(
            f"""
            INSERT INTO {table} (
                id_participante, participant_code, display_name,
                encrypted_embedding, embedding_sha256, model_version,
                encryption_version, enrolled_at, active, created_by,
                created_at, updated_at, device_id
            ) VALUES (
                :id_participante, :participant_code, :display_name,
                :encrypted_embedding, :embedding_sha256, :model_version,
                :encryption_version, NOW(), 1, :created_by,
                NOW(), NOW(), :device_id
            )
            """
        ),
        {
            "id_participante": payload.id_participante,
            "participant_code": payload.participant_code.strip().upper(),
            "display_name": payload.display_name.strip(),
            "encrypted_embedding": encrypt_embedding(embedding),
            "embedding_sha256": digest,
            "model_version": payload.model_version,
            "encryption_version": payload.encryption_version,
            "created_by": payload.created_by,
            "device_id": payload.device_id,
        },
    )
    return get_metadata_by_id(db, result.lastrowid)


def get_metadata_by_id(db: Session, id_face_template: int):
    return db.execute(
        text(
            f"""
            SELECT id_face_template, id_participante, participant_code,
                   display_name, embedding_sha256, model_version,
                   encryption_version, enrolled_at, active, device_id
            FROM {_table()}
            WHERE id_face_template = :id_face_template
            """
        ),
        {"id_face_template": id_face_template},
    ).mappings().first()


def get_active_for_participant(db: Session, id_participante: int):
    return db.execute(
        text(
            f"""
            SELECT id_face_template, id_participante, participant_code,
                   display_name, embedding_sha256, model_version,
                   encryption_version, enrolled_at, active, device_id
            FROM {_table()}
            WHERE id_participante = :id_participante AND active = 1
            ORDER BY id_face_template DESC LIMIT 1
            """
        ),
        {"id_participante": id_participante},
    ).mappings().first()


def list_authorized_active(db: Session):
    rows = db.execute(
        text(
            f"""
            SELECT id_face_template, id_participante, participant_code,
                   display_name, encrypted_embedding, embedding_sha256,
                   model_version, encryption_version, enrolled_at,
                   active, device_id
            FROM {_table()}
            WHERE active = 1
            ORDER BY id_participante
            """
        )
    ).mappings().all()
    return [
        {
            **{key: value for key, value in row.items() if key != "encrypted_embedding"},
            "embedding_base64": base64.b64encode(
                decrypt_embedding(row["encrypted_embedding"])
            ).decode("ascii"),
        }
        for row in rows
    ]


def deactivate(
    db: Session,
    id_face_template: int,
    revoked_by: str | None,
    reason: str | None,
) -> bool:
    result = db.execute(
        text(
            f"""
            UPDATE {_table()}
            SET active = 0, revoked_at = NOW(), revoked_by = :revoked_by,
                revocation_reason = :reason, updated_at = NOW()
            WHERE id_face_template = :id_face_template AND active = 1
            """
        ),
        {
            "id_face_template": id_face_template,
            "revoked_by": revoked_by,
            "reason": reason,
        },
    )
    return result.rowcount > 0


def sync_status(db: Session, device_id: str | None):
    return db.execute(
        text(
            f"""
            SELECT
                SUM(CASE WHEN active = 1 THEN 1 ELSE 0 END) AS active_templates,
                SUM(CASE WHEN active = 1 AND device_id = :device_id THEN 1 ELSE 0 END)
                    AS templates_for_device,
                MAX(updated_at) AS latest_update
            FROM {_table()}
            """
        ),
        {"device_id": device_id},
    ).mappings().first()
