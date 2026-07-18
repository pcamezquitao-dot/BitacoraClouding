from datetime import datetime
from uuid import UUID, uuid4
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from app.core.db import get_db
from app.core.config import settings
from app.services.qr_service import parse_area_qr
from app.services.jerarquia_service import get_supervisor_for_empleado
from app.services.storage_service import save_upload
from app.services.empleado_area_service import require_asignacion_activa
from app.schemas.bitacora import (
    BitacoraAreaObsCreate,
    BitacoraAreaObsOut,
    BitacoraDiariaCreate,
    BitacoraDiariaOut,
)
from app.schemas.evidencia import EvidenciaOut, BitacoraCompletaOut

router = APIRouter(tags=["bitacora_uc03"])


def _get_bitacora_by_client_uuid(db: Session, client_uuid: str | None):
    if not client_uuid:
        return None
    table = settings.BITACORA_DIARIA_TABLE
    return db.execute(
        text(
            f"""
            SELECT id_bitacora, id_empleado, id_supervisor, ts_in_min,
                   ts_out_min, tipo_anotacion, observaciones
            FROM {table}
            WHERE client_uuid = :client_uuid
            LIMIT 1
            """
        ),
        {"client_uuid": client_uuid},
    ).mappings().first()


def _now_parts(ts_in_min: int | None = None):
    now = datetime.fromtimestamp(ts_in_min * 60) if ts_in_min else datetime.now()
    return int(now.timestamp() // 60), now.date(), now.time().replace(microsecond=0)


def _next_id_evidencia(db: Session) -> int:
    bae = settings.BAE_TABLE
    row = db.execute(text(f"SELECT COALESCE(MAX(id_evidencia), 0) + 1 AS next_id FROM {bae}")).mappings().first()
    return int(row["next_id"])


def _crear_bitacora_diaria(
    db: Session,
    id_empleado: int,
    id_supervisor: int | None,
    ts_in_min: int | None,
    ts_out_min: int | None,
    tipo_anotacion: int | None,
    observaciones: str | None,
    client_uuid: str | None = None,
    qr_area: str | None = None,
) -> BitacoraDiariaOut:
    existing = _get_bitacora_by_client_uuid(db, client_uuid)
    if existing:
        return BitacoraDiariaOut(**existing)

    try:
        require_asignacion_activa(db, id_empleado, "empleado")
    except LookupError as e:
        raise HTTPException(status_code=422, detail=str(e))

    if qr_area:
        try:
            area_seleccionada = parse_area_qr(qr_area)
        except ValueError as e:
            raise HTTPException(status_code=400, detail=str(e))
        asignacion = require_asignacion_activa(db, id_empleado, "empleado")
        if int(asignacion["id_area"]) != area_seleccionada.id_area:
            raise HTTPException(
                status_code=422,
                detail="El área seleccionada no coincide con la asignación activa del empleado",
            )

    if id_supervisor is None:
        try:
            id_supervisor = get_supervisor_for_empleado(db, id_empleado)
        except LookupError as e:
            raise HTTPException(status_code=404, detail=str(e))

    try:
        require_asignacion_activa(db, id_supervisor, "supervisor")
    except LookupError as e:
        raise HTTPException(status_code=422, detail=str(e))

    ts_in_min_calc, fecha_in, hora_in = _now_parts(ts_in_min)
    fecha_out = None
    hora_out = None
    if ts_out_min is not None:
        dt_out = datetime.fromtimestamp(ts_out_min * 60)
        fecha_out = dt_out.date()
        hora_out = dt_out.time().replace(microsecond=0)

    bd = settings.BITACORA_DIARIA_TABLE
    sql_insert_bd = text(f"""
        INSERT INTO {bd}
            (id_empleado, id_supervisor, ts_in_min, ts_out_min, tipo_anotacion,
             observaciones, fecha_in, hora_in, fecha_out, hora_out, client_uuid)
        VALUES
            (:id_empleado, :id_supervisor, :ts_in_min, :ts_out_min, :tipo_anotacion,
             :observaciones, :fecha_in, :hora_in, :fecha_out, :hora_out, :client_uuid)
    """)
    try:
        res = db.execute(sql_insert_bd, {
            "id_empleado": id_empleado,
            "id_supervisor": id_supervisor,
            "ts_in_min": ts_in_min_calc,
            "ts_out_min": ts_out_min,
            "tipo_anotacion": tipo_anotacion,
            "observaciones": observaciones,
            "fecha_in": fecha_in,
            "hora_in": hora_in,
            "fecha_out": fecha_out,
            "hora_out": hora_out,
            "client_uuid": client_uuid,
        })
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"No se pudo insertar en bitacora_diaria: {e}")

    return BitacoraDiariaOut(
        id_bitacora=int(res.lastrowid),
        id_empleado=id_empleado,
        id_supervisor=id_supervisor,
        ts_in_min=ts_in_min_calc,
        ts_out_min=ts_out_min,
        tipo_anotacion=tipo_anotacion,
        observaciones=observaciones,
    )


def _crear_observacion_area_si_aplica(
    db: Session,
    id_bitacora: int,
    id_empleado: int,
    id_supervisor: int,
    ts_in_min: int,
    qr_area: str | None,
    observaciones: str | None,
):
    if not qr_area:
        return None
    try:
        parsed_area = parse_area_qr(qr_area)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    areas_t = settings.AREAS_TABLE
    area_row = db.execute(
        text(f"SELECT id_Area_Administrativa AS id_area, descripcion FROM {areas_t} WHERE id_Area_Administrativa=:id LIMIT 1"),
        {"id": parsed_area.id_area},
    ).mappings().first()
    if not area_row:
        raise HTTPException(status_code=404, detail="Área administrativa no encontrada.")

    try:
        asignacion_empleado = require_asignacion_activa(db, id_empleado, "empleado")
    except LookupError as e:
        raise HTTPException(status_code=422, detail=str(e))
    if int(asignacion_empleado["id_area"]) != int(area_row["id_area"]):
        raise HTTPException(
            status_code=422,
            detail="El área seleccionada no coincide con la asignación activa del empleado",
        )

    bd = settings.BITACORA_DIARIA_TABLE
    bitacora_row = db.execute(
        text(f"""
            SELECT id_bitacora
            FROM {bd}
            WHERE id_empleado=:e
              AND id_supervisor=:s
              AND ts_in_min=:t
              AND id_bitacora=:id_bitacora
            LIMIT 1
        """),
        {"e": id_empleado, "s": id_supervisor, "t": ts_in_min, "id_bitacora": id_bitacora},
    ).mappings().first()
    if not bitacora_row:
        raise HTTPException(
            status_code=404,
            detail="No existe la bitácora diaria correspondiente para empleado, supervisor y ts_in_min",
        )
    id_bitacora_db = int(bitacora_row["id_bitacora"])

    bao = settings.BAO_TABLE
    existe = db.execute(
        text(f"SELECT 1 FROM {bao} WHERE id_empleado=:e AND id_supervisor=:s AND ts_in_min=:t LIMIT 1"),
        {"e": id_empleado, "s": id_supervisor, "t": ts_in_min},
    ).first()
    if not existe:
        db.execute(text(f"""
            INSERT INTO {bao}
                (id_empleado, id_supervisor, ts_in_min, id_area, observaciones, created_at, id_bitacora)
            VALUES
                (:e, :s, :t, :id_area, :obs, NOW(), :id_bitacora)
        """), {
            "e": id_empleado,
            "s": id_supervisor,
            "t": ts_in_min,
            "id_area": int(area_row["id_area"]),
            "obs": observaciones,
            "id_bitacora": id_bitacora_db,
        })
    return area_row


def _crear_evidencia(
    db: Session,
    id_bitacora: int,
    id_area: int,
    ts_in_min: int,
    id_tipo_evidencia: int,
    archivo: UploadFile,
    uuid_cliente: str,
    duracion_seg: int | None = None,
    orden: int | None = None,
) -> EvidenciaOut:
    
    if id_tipo_evidencia not in (1,2,3):
        raise HTTPException(status_code=400, detail="id_tipo_evidencia inválido. Use 1=FOTO,2=AUDIO,3=VIDEO.")

    bd = settings.BITACORA_DIARIA_TABLE
    chk = db.execute(
        text(f"SELECT 1 FROM {bd} WHERE id_bitacora=:id LIMIT 1"),
        {"id": id_bitacora},
    ).first()
    if not chk:
        raise HTTPException(
            status_code=404,
            detail="No existe bitacora_diaria para asociar la evidencia.",
        )

    bae = settings.BAE_TABLE
    existente = db.execute(
        text(f"SELECT id_evidencia FROM {bae} WHERE uuid_cliente=:uuid LIMIT 1"),
        {"uuid": uuid_cliente},
    ).first()
    if existente:
        row = db.execute(text(f"SELECT * FROM {bae} WHERE id_evidencia=:id"), {"id": existente[0]}).mappings().first()
        return EvidenciaOut(**dict(row))

    subdir = f"bitacora_{id_bitacora}/area_{id_area}/ts_{ts_in_min}"
    rel_url, sha256, size_bytes = save_upload(archivo, subdir)

    sql_ins = text(f"""
        INSERT INTO {bae}
            (id_bitacora, id_area, ts_in_min, id_tipo_evidencia, archivo_url,
             archivo_nombre, archivo_hash, mime_type, duracion_seg, tamanio_bytes,
             orden, uuid_cliente)
        VALUES
            (:id_bitacora, :id_area, :t, :tipo, :url,
             :nombre, :hash, :mime_type, :dur, :size, :orden, :uuid_cliente)
    """)
    result = db.execute(sql_ins, {
        "id_bitacora": id_bitacora,
        "id_area": id_area,
        "t": ts_in_min,
        "tipo": id_tipo_evidencia,
        "url": rel_url,
        "nombre": archivo.filename,
        "hash": sha256,
        "mime_type": archivo.content_type,
        "dur": duracion_seg,
        "size": size_bytes,
        "orden": orden,
        "uuid_cliente": uuid_cliente,
    })

    return EvidenciaOut(
        id_evidencia=int(result.lastrowid),
        id_bitacora=id_bitacora,
        id_area=id_area,
        ts_in_min=ts_in_min,
        id_tipo_evidencia=id_tipo_evidencia,
        archivo_url=rel_url,
        archivo_nombre=archivo.filename,
        archivo_hash=sha256,
        mime_type=archivo.content_type,
        tamanio_bytes=size_bytes,
        duracion_seg=duracion_seg,
        orden=orden,
        uuid_cliente=uuid_cliente,
    )


@router.post("/bitacora_diaria", response_model=BitacoraDiariaOut)
def crear_bitacora_diaria(payload: BitacoraDiariaCreate, db: Session = Depends(get_db)):
    try:
        out = _crear_bitacora_diaria(
            db=db,
            id_empleado=payload.id_empleado,
            id_supervisor=payload.id_supervisor,
            ts_in_min=payload.ts_in_min,
            ts_out_min=payload.ts_out_min,
            tipo_anotacion=payload.tipo_anotacion,
            observaciones=payload.observaciones,
            client_uuid=payload.client_uuid,
            qr_area=payload.qr_area,
        )
        db.commit()
        return out
    except IntegrityError:
        db.rollback()
        existing = _get_bitacora_by_client_uuid(db, payload.client_uuid)
        if existing:
            return BitacoraDiariaOut(**existing)
        raise HTTPException(status_code=409, detail="Conflicto de idempotencia de bitácora")
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo crear bitácora diaria: {e}")


@router.post("/bitacora_area_observacion", response_model=BitacoraAreaObsOut)
def crear_bitacora_area_observacion(payload: BitacoraAreaObsCreate, db: Session = Depends(get_db)):
    try:
        bd_out = _crear_bitacora_diaria(
            db=db,
            id_empleado=payload.id_empleado,
            id_supervisor=None,
            ts_in_min=None,
            ts_out_min=None,
            tipo_anotacion=payload.tipo_anotacion,
            observaciones=payload.observaciones,
        )
        area_row = _crear_observacion_area_si_aplica(
            db=db,
            id_bitacora=bd_out.id_bitacora,
            id_empleado=bd_out.id_empleado,
            id_supervisor=int(bd_out.id_supervisor),
            ts_in_min=bd_out.ts_in_min,
            qr_area=payload.qr_area,
            observaciones=payload.observaciones,
        )
        db.commit()
        return BitacoraAreaObsOut(
            id_bitacora=bd_out.id_bitacora,
            id_empleado=bd_out.id_empleado,
            id_supervisor=int(bd_out.id_supervisor),
            ts_in_min=bd_out.ts_in_min,
            id_area=int(area_row["id_area"]),
            area_descripcion=str(area_row["descripcion"]),
        )
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo insertar observación de área: {e}")


@router.post("/bitacora_area_evidencia/upload", response_model=EvidenciaOut)
def upload_evidencia(
    id_bitacora: int = Form(...),
    id_area: int = Form(...),
    ts_in_min: int = Form(...),
    id_tipo_evidencia: int = Form(...),
    uuid_cliente: UUID = Form(...),
    duracion_seg: int | None = Form(None),
    orden: int | None = Form(None),
    archivo: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    try:
        evidencia = _crear_evidencia(
            db=db,
            id_bitacora=id_bitacora,
            id_area=id_area,
            ts_in_min=ts_in_min,
            id_tipo_evidencia=id_tipo_evidencia,
            archivo=archivo,
            uuid_cliente=str(uuid_cliente),
            duracion_seg=duracion_seg,
            orden=orden,
        )
        db.commit()
        return evidencia
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo insertar evidencia en BD: {e}")


@router.post("/bitacora_completa/upload", response_model=BitacoraCompletaOut)
def crear_bitacora_completa_upload(
    id_empleado: int = Form(...),
    id_tipo_evidencia: int = Form(...),
    archivo: UploadFile = File(...),
    id_supervisor: int | None = Form(None),
    ts_in_min: int | None = Form(None),
    ts_out_min: int | None = Form(None),
    tipo_anotacion: int | None = Form(None),
    observaciones: str | None = Form(None),
    client_uuid: str | None = Form(None),
    qr_area: str | None = Form(None),
    duracion_seg: int | None = Form(None),
    orden: int | None = Form(None),
    db: Session = Depends(get_db),
):
    try:
        bd_out = _crear_bitacora_diaria(
            db=db,
            id_empleado=id_empleado,
            id_supervisor=id_supervisor,
            ts_in_min=ts_in_min,
            ts_out_min=ts_out_min,
            tipo_anotacion=tipo_anotacion,
            observaciones=observaciones,
            client_uuid=client_uuid,
        )
        area_row = _crear_observacion_area_si_aplica(
            db=db,
            id_bitacora=bd_out.id_bitacora,
            id_empleado=bd_out.id_empleado,
            id_supervisor=int(bd_out.id_supervisor),
            ts_in_min=bd_out.ts_in_min,
            qr_area=qr_area,
            observaciones=observaciones,
        )
        evidencia = _crear_evidencia(
            db=db,
            id_bitacora=bd_out.id_bitacora,
            id_area=int(area_row["id_area"]),
            ts_in_min=bd_out.ts_in_min,
            id_tipo_evidencia=id_tipo_evidencia,
            archivo=archivo,
            uuid_cliente=client_uuid or str(uuid4()),
            duracion_seg=duracion_seg,
            orden=orden,
        )
        db.commit()
        return BitacoraCompletaOut(id_bitacora=bd_out.id_bitacora, evidencia=evidencia)
    except HTTPException:
        db.rollback()
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=400, detail=f"No se pudo crear bitácora completa: {e}")


@router.get("/bitacora_diaria/{id_bitacora}", response_model=BitacoraDiariaOut)
def obtener_bitacora_diaria(id_bitacora: int, db: Session = Depends(get_db)):
    bd = settings.BITACORA_DIARIA_TABLE
    row = db.execute(text(f"""
        SELECT id_bitacora, id_empleado, id_supervisor, ts_in_min, ts_out_min, tipo_anotacion, observaciones
        FROM {bd}
        WHERE id_bitacora=:id
        LIMIT 1
    """), {"id": id_bitacora}).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Bitácora no encontrada")
    return BitacoraDiariaOut(**dict(row))
