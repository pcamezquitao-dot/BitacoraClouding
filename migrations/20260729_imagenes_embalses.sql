-- El catálogo de embalses reutiliza objeto_monitoreo_satelital (tipo EMBALSE).
-- MariaDB conserva únicamente metadatos y rutas; el archivo permanece fuera.
CREATE TABLE IF NOT EXISTS imagen_satelital_embalse (
    id_imagen_satelital BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_objeto_monitoreo INT UNSIGNED NOT NULL,
    fecha_captura DATE NOT NULL,
    porcentaje_nubes DECIMAL(5,2) NULL,
    fuente VARCHAR(150) NOT NULL,
    archivo_ruta VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_imagen_satelital),
    UNIQUE KEY uq_imagen_embalse_fecha_ruta (
        id_objeto_monitoreo, fecha_captura, archivo_ruta
    ),
    KEY idx_imagen_embalse_activa_fecha (
        id_objeto_monitoreo, activo, fecha_captura
    ),
    CONSTRAINT fk_imagen_embalse_objeto
        FOREIGN KEY (id_objeto_monitoreo)
        REFERENCES objeto_monitoreo_satelital (id_objeto_monitoreo)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO imagen_satelital_embalse (
    id_objeto_monitoreo, fecha_captura, porcentaje_nubes,
    fuente, archivo_ruta, mime_type
)
SELECT
    id_objeto_monitoreo, '2026-07-29', 12.50,
    'MANUAL / DEMOSTRACIÓN (fuente futura: Copernicus Sentinel-2)',
    'embalse_la_copa/demo_2026-07-29.png', 'image/png'
FROM objeto_monitoreo_satelital
WHERE nombre = 'Embalse La Copa'
  AND NOT EXISTS (
      SELECT 1 FROM imagen_satelital_embalse
      WHERE archivo_ruta = 'embalse_la_copa/demo_2026-07-29.png'
  );
