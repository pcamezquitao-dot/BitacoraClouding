-- Reversión exclusiva de la Iteración 1.
DELETE FROM objeto_monitoreo_satelital
WHERE nombre IN (
    'Embalse de Tominé',
    'Embalse del Sisga',
    'Embalse de San Rafael',
    'Embalse de Chuza',
    'Embalse del Neusa'
);

UPDATE objeto_monitoreo_satelital
SET latitud_centro=NULL, longitud_centro=NULL, geometria_geojson=NULL
WHERE nombre='Embalse La Copa';

ALTER TABLE imagen_satelital_embalse
    DROP COLUMN IF EXISTS estado,
    DROP COLUMN IF EXISTS porcentaje_pixeles_validos;

ALTER TABLE objeto_monitoreo_satelital
    DROP COLUMN IF EXISTS estado_seguimiento,
    DROP COLUMN IF EXISTS observacion_precision,
    DROP COLUMN IF EXISTS sistema_coordenadas,
    DROP COLUMN IF EXISTS fecha_obtencion_geometria,
    DROP COLUMN IF EXISTS fuente_geometria;
