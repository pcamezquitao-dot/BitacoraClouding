import json

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.satelital import (
    EmbalseSatelitalOut,
    ImagenSatelitalEmbalseOut,
    ObjetoMonitoreoSatelitalOut,
)


router = APIRouter(prefix="/satelital", tags=["satelital"])


def _embalse(row) -> EmbalseSatelitalOut:
    data = dict(row)
    return EmbalseSatelitalOut(
        id_embalse=data["id_objeto_monitoreo"],
        nombre=data["nombre"],
        pais="Colombia" if data["pais_codigo"] == "CO" else data["pais_codigo"],
        departamento=data["departamento_provincia"],
        municipio=data["municipio_localidad"],
        descripcion=data["descripcion"],
        fuente_geografica=data["fuente_geometria"],
        ultima_fecha_procesada=data["ultima_fecha_procesada"],
        estado_seguimiento=data["estado_seguimiento"],
    )


def _imagen(row) -> ImagenSatelitalEmbalseOut:
    data = dict(row)
    return ImagenSatelitalEmbalseOut(
        id_imagen_satelital=data["id_imagen_satelital"],
        id_embalse=data["id_objeto_monitoreo"],
        fecha_captura=data["fecha_captura"],
        porcentaje_nubes=data["porcentaje_nubes"],
        porcentaje_pixeles_validos=data["porcentaje_pixeles_validos"],
        fuente=data["fuente"],
        imagen_url=f"/satelital-files/{data['archivo_ruta']}",
        mime_type=data["mime_type"],
        estado=data["estado"],
    )


@router.get("/objetos", response_model=list[ObjetoMonitoreoSatelitalOut])
def listar_objetos_monitoreo_activos(db: Session = Depends(get_db)):
    rows = db.execute(
        text(
            """
            SELECT id_objeto_monitoreo, nombre, tipo_objeto, pais_codigo,
                   departamento_provincia, municipio_localidad, descripcion,
                   latitud_centro, longitud_centro, geometria_geojson
            FROM objeto_monitoreo_satelital
            WHERE activo = TRUE
            ORDER BY nombre, id_objeto_monitoreo
            """
        )
    ).mappings().all()
    result = []
    for row in rows:
        item = dict(row)
        if isinstance(item.get("geometria_geojson"), str):
            item["geometria_geojson"] = json.loads(item["geometria_geojson"])
        result.append(ObjetoMonitoreoSatelitalOut(**item))
    return result


@router.get("/embalses", response_model=list[EmbalseSatelitalOut])
def listar_embalses_activos(db: Session = Depends(get_db)):
    rows = db.execute(
        text(
            """
            SELECT o.id_objeto_monitoreo, o.nombre, o.pais_codigo,
                   o.departamento_provincia, o.municipio_localidad,
                   o.descripcion, o.fuente_geometria, o.estado_seguimiento,
                   MAX(i.fecha_captura) AS ultima_fecha_procesada
            FROM objeto_monitoreo_satelital o
            LEFT JOIN imagen_satelital_embalse i
              ON i.id_objeto_monitoreo=o.id_objeto_monitoreo AND i.activo=TRUE
            WHERE o.activo=TRUE AND o.tipo_objeto='EMBALSE'
            GROUP BY o.id_objeto_monitoreo, o.nombre, o.pais_codigo,
                     o.departamento_provincia, o.municipio_localidad,
                     o.descripcion, o.fuente_geometria, o.estado_seguimiento
            ORDER BY nombre, id_objeto_monitoreo
            """
        )
    ).mappings().all()
    return [_embalse(row) for row in rows]


@router.get("/embalses/{id_embalse}", response_model=EmbalseSatelitalOut)
def consultar_embalse(id_embalse: int, db: Session = Depends(get_db)):
    row = db.execute(
        text(
            """
            SELECT o.id_objeto_monitoreo, o.nombre, o.pais_codigo,
                   o.departamento_provincia, o.municipio_localidad,
                   o.descripcion, o.fuente_geometria, o.estado_seguimiento,
                   MAX(i.fecha_captura) AS ultima_fecha_procesada
            FROM objeto_monitoreo_satelital o
            LEFT JOIN imagen_satelital_embalse i
              ON i.id_objeto_monitoreo=o.id_objeto_monitoreo AND i.activo=TRUE
            WHERE o.id_objeto_monitoreo=:id AND o.activo=TRUE
              AND o.tipo_objeto='EMBALSE'
            GROUP BY o.id_objeto_monitoreo, o.nombre, o.pais_codigo,
                     o.departamento_provincia, o.municipio_localidad,
                     o.descripcion, o.fuente_geometria, o.estado_seguimiento
            LIMIT 1
            """
        ),
        {"id": id_embalse},
    ).mappings().first()
    if row is None:
        raise HTTPException(status_code=404, detail="Embalse no encontrado")
    return _embalse(row)


@router.get(
    "/embalses/{id_embalse}/imagenes",
    response_model=list[ImagenSatelitalEmbalseOut],
)
def listar_imagenes_embalse(id_embalse: int, db: Session = Depends(get_db)):
    rows = db.execute(
        text(
            """
            SELECT id_imagen_satelital, id_objeto_monitoreo, fecha_captura,
                   porcentaje_nubes, porcentaje_pixeles_validos, fuente,
                   archivo_ruta, mime_type, estado
            FROM imagen_satelital_embalse
            WHERE id_objeto_monitoreo=:id AND activo=TRUE
            ORDER BY fecha_captura DESC, id_imagen_satelital DESC
            """
        ),
        {"id": id_embalse},
    ).mappings().all()
    return [_imagen(row) for row in rows]


@router.get(
    "/imagenes/{id_imagen}",
    response_model=ImagenSatelitalEmbalseOut,
)
def consultar_imagen_satelital(id_imagen: int, db: Session = Depends(get_db)):
    row = db.execute(
        text(
            """
            SELECT id_imagen_satelital, id_objeto_monitoreo, fecha_captura,
                   porcentaje_nubes, porcentaje_pixeles_validos, fuente,
                   archivo_ruta, mime_type, estado
            FROM imagen_satelital_embalse
            WHERE id_imagen_satelital=:id AND activo=TRUE
            LIMIT 1
            """
        ),
        {"id": id_imagen},
    ).mappings().first()
    if row is None:
        raise HTTPException(status_code=404, detail="Imagen satelital no encontrada")
    return _imagen(row)
