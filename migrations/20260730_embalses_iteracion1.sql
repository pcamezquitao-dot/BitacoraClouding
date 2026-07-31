-- Iteración 1: catálogo verificable para seguimiento satelital de embalses.
-- Coordenadas centrales consultadas en OpenStreetMap/Nominatim el 2026-07-30.
-- No se inventan polígonos: geometria_geojson permanece NULL hasta importar
-- íntegramente cada geometría OSM y validarla para procesamiento satelital.

ALTER TABLE objeto_monitoreo_satelital
    ADD COLUMN IF NOT EXISTS fuente_geometria VARCHAR(255) NULL
        AFTER geometria_geojson,
    ADD COLUMN IF NOT EXISTS fecha_obtencion_geometria DATE NULL
        AFTER fuente_geometria,
    ADD COLUMN IF NOT EXISTS sistema_coordenadas VARCHAR(30) NOT NULL
        DEFAULT 'EPSG:4326' AFTER fecha_obtencion_geometria,
    ADD COLUMN IF NOT EXISTS observacion_precision VARCHAR(500) NULL
        AFTER sistema_coordenadas,
    ADD COLUMN IF NOT EXISTS estado_seguimiento VARCHAR(30) NOT NULL
        DEFAULT 'CATALOGADO' AFTER observacion_precision;

ALTER TABLE imagen_satelital_embalse
    ADD COLUMN IF NOT EXISTS porcentaje_pixeles_validos DECIMAL(5,2) NULL
        AFTER porcentaje_nubes,
    ADD COLUMN IF NOT EXISTS estado VARCHAR(30) NOT NULL
        DEFAULT 'DISPONIBLE' AFTER mime_type;

UPDATE objeto_monitoreo_satelital
SET latitud_centro=5.6035680,
    longitud_centro=-73.1997066,
    fuente_geometria='OpenStreetMap way 28158202 / Nominatim',
    fecha_obtencion_geometria='2026-07-30',
    sistema_coordenadas='EPSG:4326',
    observacion_precision='Centro OSM verificado; polígono íntegro pendiente de importación y validación.',
    estado_seguimiento='DEMOSTRACION'
WHERE nombre='Embalse La Copa';

INSERT INTO objeto_monitoreo_satelital (
    nombre, tipo_objeto, pais_codigo, departamento_provincia,
    municipio_localidad, descripcion, latitud_centro, longitud_centro,
    fuente_geometria, fecha_obtencion_geometria, sistema_coordenadas,
    observacion_precision, estado_seguimiento
)
VALUES
('Embalse de Tominé','EMBALSE','CO','Cundinamarca','Sesquilé, Guatavita',
 'Embalse incluido en el catálogo inicial de seguimiento satelital.',
 4.9777177,-73.8265378,'OpenStreetMap relation 7272885 / Nominatim',
 '2026-07-30','EPSG:4326',
 'Centro OSM verificado; polígono íntegro pendiente de importación y validación.',
 'CATALOGADO'),
('Embalse del Sisga','EMBALSE','CO','Cundinamarca','Chocontá',
 'Embalse incluido en el catálogo inicial de seguimiento satelital.',
 5.0648609,-73.7137629,'OpenStreetMap way 32058486 / Nominatim',
 '2026-07-30','EPSG:4326',
 'Centro OSM verificado; polígono íntegro pendiente de importación y validación.',
 'CATALOGADO'),
('Embalse de San Rafael','EMBALSE','CO','Cundinamarca','La Calera',
 'Embalse del sistema de abastecimiento Chingaza.',
 4.7021189,-73.9925497,'OpenStreetMap relation 4000410 / Nominatim',
 '2026-07-30','EPSG:4326',
 'Centro OSM verificado; polígono íntegro pendiente de importación y validación.',
 'CATALOGADO'),
('Embalse de Chuza','EMBALSE','CO','Cundinamarca','Fómeque',
 'Embalse del sistema de abastecimiento Chingaza.',
 4.5987657,-73.7052027,'OpenStreetMap way 32056928 / Nominatim',
 '2026-07-30','EPSG:4326',
 'Centro OSM verificado; polígono íntegro pendiente de importación y validación.',
 'CATALOGADO'),
('Embalse del Neusa','EMBALSE','CO','Cundinamarca','Cogua',
 'Embalse incluido en el catálogo inicial de seguimiento satelital.',
 5.1354179,-73.9667554,'OpenStreetMap way 756631663 / Nominatim',
 '2026-07-30','EPSG:4326',
 'Centro OSM verificado; polígono íntegro pendiente de importación y validación.',
 'CATALOGADO')
ON DUPLICATE KEY UPDATE
    tipo_objeto=VALUES(tipo_objeto),
    pais_codigo=VALUES(pais_codigo),
    departamento_provincia=VALUES(departamento_provincia),
    municipio_localidad=VALUES(municipio_localidad),
    descripcion=VALUES(descripcion),
    latitud_centro=VALUES(latitud_centro),
    longitud_centro=VALUES(longitud_centro),
    fuente_geometria=VALUES(fuente_geometria),
    fecha_obtencion_geometria=VALUES(fecha_obtencion_geometria),
    sistema_coordenadas=VALUES(sistema_coordenadas),
    observacion_precision=VALUES(observacion_precision),
    estado_seguimiento=VALUES(estado_seguimiento);

UPDATE imagen_satelital_embalse
SET porcentaje_pixeles_validos=NULL, estado='DISPONIBLE'
WHERE archivo_ruta='embalse_la_copa/demo_2026-07-29.png';
