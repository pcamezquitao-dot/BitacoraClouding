CREATE TABLE IF NOT EXISTS objeto_monitoreo_satelital (
    id_objeto_monitoreo INT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    tipo_objeto VARCHAR(50) NOT NULL,
    pais_codigo CHAR(2) NOT NULL,
    departamento_provincia VARCHAR(100) NULL,
    municipio_localidad VARCHAR(100) NULL,
    descripcion VARCHAR(500) NULL,
    latitud_centro DECIMAL(10,7) NULL,
    longitud_centro DECIMAL(10,7) NULL,
    geometria_geojson JSON NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_objeto_monitoreo),
    UNIQUE KEY uq_objeto_satelital_nombre (nombre),
    KEY idx_objeto_satelital_activo_nombre (activo, nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO objeto_monitoreo_satelital (
    nombre, tipo_objeto, pais_codigo, departamento_provincia,
    municipio_localidad, descripcion
)
SELECT
    'Embalse La Copa', 'EMBALSE', 'CO', 'Boyacá', 'Toca',
    'Objeto piloto para seguimiento satelital mediante Copernicus Sentinel-2.'
WHERE NOT EXISTS (
    SELECT 1 FROM objeto_monitoreo_satelital
    WHERE nombre = 'Embalse La Copa'
);

INSERT INTO tipo_novedad (tipo_novedad, descripcion_novedad)
SELECT 9, 'SATELITAL'
WHERE NOT EXISTS (
    SELECT 1 FROM tipo_novedad
    WHERE tipo_novedad = 9 OR UPPER(descripcion_novedad) = 'SATELITAL'
);

ALTER TABLE bitacora_diaria
    ADD COLUMN id_objeto_monitoreo INT UNSIGNED NULL AFTER tipo_anotacion,
    ADD COLUMN origen_bitacora VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        AFTER id_objeto_monitoreo,
    ADD COLUMN tipo_seguimiento_satelital VARCHAR(40) NULL
        AFTER origen_bitacora,
    ADD KEY idx_bitacora_objeto_monitoreo (id_objeto_monitoreo),
    ADD CONSTRAINT fk_bitacora_objeto_monitoreo
        FOREIGN KEY (id_objeto_monitoreo)
        REFERENCES objeto_monitoreo_satelital (id_objeto_monitoreo)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
