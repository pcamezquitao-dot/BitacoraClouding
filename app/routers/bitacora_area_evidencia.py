from datetime import datetime
import logging
from pathlib import Path
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, Response, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.db import get_db
from app.schemas.bitacora_area_evidencia import (
    BitacoraAreaEvidenciaCreate,
    BitacoraAreaEvidenciaResponse,
    BitacoraAreaEvidenciaUpdate,
)
from app.services.evidencia_file_service import (
    candidate_evidence_paths,
    resolve_evidencia_path,
    save_validated_evidence,
)

router = APIRouter(prefix="/bitacora-area-evidencias", tags=["Bitácora área evidencias"])

COLUMNAS = """
    id_evidencia, id_bitacora, id_area, ts_in_min, id_tipo_evidencia,
    archivo_url, contenido_texto, archivo_nombre, archivo_hash, mime_type, duracion_seg,
    tamanio_bytes, orden, latitud, longitud, precision_gps,
    uuid_cliente, created_at
"""
logger = logging.getLogger("bitacora.sync")


def _get(db: Session, id_evidencia: int):
    return db.execute(
        text(f"SELECT {COLUMNAS} FROM {settings.BAE_TABLE} WHERE id_evidencia=:id LIMIT 1"),
        {"id": id_evidencia},
    ).mappings().first()


def _get_by_uuid(db: Session, uuid_cliente: UUID | str):
    return db.execute(
        text(f"SELECT {COLUMNAS} FROM {settings.BAE_TABLE} WHERE uuid_cliente=:uuid LIMIT 1"),
        {"uuid": str(uuid_cliente)},
    ).mappings().first()


def _validate_parent(db: Session, id_bitacora: int):
    exists = db.execute(
        text(f"SELECT 1 FROM {settings.BITACORA_DIARIA_TABLE} WHERE id_bitacora=:id LIMIT 1"),
        {"id": id_bitacora},
    ).first()
    if not exists:
        raise HTTPException(status_code=404, detail="Bitácora diaria relacionada no encontrada")


def _insert(db: Session, payload: BitacoraAreaEvidenciaCreate):
    existing = _get_by_uuid(db, payload.uuid_cliente)
    if existing:
        return existing
    _validate_parent(db, payload.id_bitacora)
    if payload.id_tipo_evidencia == 4:
        if not payload.contenido_texto or not payload.contenido_texto.strip():
            raise HTTPException(status_code=422, detail="contenido_texto es obligatorio para TEXTO")
        payload = payload.model_copy(update={
            "contenido_texto": payload.contenido_texto.strip(),
            "archivo_url": None,
            "archivo_nombre": None,
            "archivo_hash": None,
            "mime_type": None,
            "duracion_seg": None,
            "tamanio_bytes": None,
        })
    elif not payload.archivo_url:
        raise HTTPException(status_code=422, detail="archivo_url es obligatorio para evidencia multimedia")
    values = payload.model_dump(mode="json")
    result = db.execute(text(f"""
        INSERT INTO {settings.BAE_TABLE}
            (id_bitacora, id_area, ts_in_min, id_tipo_evidencia, archivo_url, contenido_texto,
             archivo_nombre, archivo_hash, mime_type, duracion_seg, tamanio_bytes,
             orden, latitud, longitud, precision_gps, uuid_cliente)
        VALUES
            (:id_bitacora, :id_area, :ts_in_min, :id_tipo_evidencia, :archivo_url, :contenido_texto,
             :archivo_nombre, :archivo_hash, :mime_type, :duracion_seg, :tamanio_bytes,
             :orden, :latitud, :longitud, :precision_gps, :uuid_cliente)
    """), values)
    return _get(db, int(result.lastrowid))


@router.get("", response_model=list[BitacoraAreaEvidenciaResponse])
def listar_evidencias(
    id_bitacora: int | None = None,
    offset: int = 0,
    limit: int = 50,
    db: Session = Depends(get_db),
):
    if offset < 0 or limit < 1 or limit > 200:
        raise HTTPException(status_code=422, detail="Paginación inválida")
    where = "WHERE id_bitacora=:id_bitacora" if id_bitacora is not None else ""
    return db.execute(
        text(f"""
            SELECT {COLUMNAS} FROM {settings.BAE_TABLE} {where}
            ORDER BY ts_in_min ASC, created_at ASC, id_evidencia ASC
            LIMIT :limit OFFSET :offset
        """),
        {"id_bitacora": id_bitacora, "limit": limit, "offset": offset},
    ).mappings().all()


@router.get("/{id_evidencia}", response_model=BitacoraAreaEvidenciaResponse)
def consultar_evidencia(id_evidencia: int, db: Session = Depends(get_db)):
    row = _get(db, id_evidencia)
    if not row:
        raise HTTPException(status_code=404, detail="Evidencia no encontrada")
    return row


@router.post("", response_model=BitacoraAreaEvidenciaResponse, status_code=201)
def crear_metadatos(payload: BitacoraAreaEvidenciaCreate, db: Session = Depends(get_db)):
    try:
        row = _insert(db, payload)
        db.commit()
        return row
    except HTTPException:
        db.rollback()
        raise
    except IntegrityError:
        db.rollback()
        existing = _get_by_uuid(db, payload.uuid_cliente)
        if existing:
            return existing
        raise HTTPException(status_code=409, detail="Conflicto al crear la evidencia")
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo crear la evidencia: {exc}")


@router.post("/upload", response_model=BitacoraAreaEvidenciaResponse, status_code=201)
def crear_con_archivo(
    file: UploadFile = File(...),
    id_bitacora: int = Form(...),
    id_area: int = Form(...),
    ts_in_min: int = Form(...),
    id_tipo_evidencia: int = Form(...),
    uuid_cliente: UUID = Form(...),
    archivo_nombre: str | None = Form(None),
    archivo_hash: str | None = Form(None),
    mime_type: str | None = Form(None),
    duracion_seg: int | None = Form(None),
    tamanio_bytes: int | None = Form(None),
    orden: int | None = Form(None),
    latitud: float | None = Form(None),
    longitud: float | None = Form(None),
    precision_gps: float | None = Form(None),
    db: Session = Depends(get_db),
):
    logger.info(
        "evidence upload local_uuid=%s bitacora_id=%s filename=%s content_type=%s "
        "id_tipo_evidencia=%s size=%s",
        uuid_cliente,
        id_bitacora,
        Path(file.filename or "").name,
        file.content_type,
        id_tipo_evidencia,
        getattr(file, "size", None) or tamanio_bytes,
    )
    existing = _get_by_uuid(db, uuid_cliente)
    if existing:
        logger.info(
            "evidence idempotent local_uuid=%s server_id=%s",
            uuid_cliente,
            existing["id_evidencia"],
        )
        return existing
    saved_path: Path | None = None
    try:
        relative, original, detected_mime, detected_size, digest, saved_path = save_validated_evidence(
            file, id_tipo_evidencia
        )
        payload = BitacoraAreaEvidenciaCreate(
            id_bitacora=id_bitacora,
            id_area=id_area,
            ts_in_min=ts_in_min,
            id_tipo_evidencia=id_tipo_evidencia,
            archivo_url=relative,
            archivo_nombre=archivo_nombre or original,
            archivo_hash=archivo_hash or digest,
            mime_type=mime_type or detected_mime,
            duracion_seg=duracion_seg,
            tamanio_bytes=tamanio_bytes if tamanio_bytes is not None else detected_size,
            orden=orden,
            latitud=latitud,
            longitud=longitud,
            precision_gps=precision_gps,
            uuid_cliente=uuid_cliente,
        )
        row = _insert(db, payload)
        db.commit()
        logger.info(
            "evidence committed local_uuid=%s bitacora_id=%s generated_name=%s "
            "size=%s server_id=%s",
            uuid_cliente,
            id_bitacora,
            Path(relative).name,
            detected_size,
            row["id_evidencia"],
        )
        return row
    except HTTPException:
        db.rollback()
        if saved_path:
            saved_path.unlink(missing_ok=True)
        raise
    except IntegrityError:
        db.rollback()
        if saved_path:
            saved_path.unlink(missing_ok=True)
        existing = _get_by_uuid(db, uuid_cliente)
        if existing:
            return existing
        raise HTTPException(status_code=409, detail="Conflicto al cargar la evidencia")
    except Exception as exc:
        db.rollback()
        if saved_path:
            saved_path.unlink(missing_ok=True)
        logger.exception(
            "evidence failed local_uuid=%s bitacora_id=%s error=%s",
            uuid_cliente,
            id_bitacora,
            type(exc).__name__,
        )
        raise HTTPException(status_code=400, detail=f"No se pudo cargar la evidencia: {exc}")


@router.patch("/{id_evidencia}", response_model=BitacoraAreaEvidenciaResponse)
def modificar_evidencia(
    id_evidencia: int,
    payload: BitacoraAreaEvidenciaUpdate,
    db: Session = Depends(get_db),
):
    if not _get(db, id_evidencia):
        raise HTTPException(status_code=404, detail="Evidencia no encontrada")
    changes = payload.model_dump(exclude_unset=True)
    if not changes:
        return _get(db, id_evidencia)
    assignments = ", ".join(f"{column}=:{column}" for column in changes)
    changes["id_evidencia"] = id_evidencia
    try:
        db.execute(
            text(f"UPDATE {settings.BAE_TABLE} SET {assignments} WHERE id_evidencia=:id_evidencia"),
            changes,
        )
        db.commit()
        return _get(db, id_evidencia)
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo modificar la evidencia: {exc}")


@router.delete("/{id_evidencia}", status_code=204)
def eliminar_evidencia(id_evidencia: int, db: Session = Depends(get_db)):
    row = _get(db, id_evidencia)
    if not row:
        raise HTTPException(status_code=404, detail="Evidencia no encontrada")
    try:
        db.execute(
            text(f"DELETE FROM {settings.BAE_TABLE} WHERE id_evidencia=:id"),
            {"id": id_evidencia},
        )
        db.commit()
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo eliminar la evidencia: {exc}")
    for path in candidate_evidence_paths(row["archivo_url"]):
        path.unlink(missing_ok=True)
    return Response(status_code=204)


@router.get("/{id_evidencia}/archivo")
def descargar_archivo(id_evidencia: int, db: Session = Depends(get_db)):
    row = _get(db, id_evidencia)
    if not row:
        raise HTTPException(status_code=404, detail="Evidencia no encontrada")
    path = resolve_evidencia_path(row["archivo_url"])
    if not path.is_file():
        raise HTTPException(status_code=404, detail="Archivo físico no encontrado")
    return FileResponse(path, media_type=row["mime_type"], filename=row["archivo_nombre"] or path.name)
