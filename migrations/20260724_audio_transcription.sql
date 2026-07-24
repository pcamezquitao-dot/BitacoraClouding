ALTER TABLE bitacora_area_evidencia
    ADD COLUMN id_evidencia_origen BIGINT UNSIGNED NULL AFTER id_evidencia,
    ADD COLUMN transcripcion_estado VARCHAR(20) NULL AFTER contenido_texto,
    ADD COLUMN transcripcion_motor VARCHAR(100) NULL AFTER transcripcion_estado,
    ADD COLUMN transcripcion_idioma VARCHAR(10) NULL AFTER transcripcion_motor,
    ADD COLUMN transcripcion_fecha DATETIME NULL AFTER transcripcion_idioma,
    ADD COLUMN transcripcion_error TEXT NULL AFTER transcripcion_fecha,
    ADD COLUMN transcripcion_intentos INT UNSIGNED NOT NULL DEFAULT 0
        AFTER transcripcion_error,
    ADD UNIQUE KEY uq_bae_transcripcion_origen (id_evidencia_origen),
    ADD KEY idx_bae_transcripcion_estado (transcripcion_estado),
    ADD CONSTRAINT fk_bae_evidencia_origen
        FOREIGN KEY (id_evidencia_origen)
        REFERENCES bitacora_area_evidencia (id_evidencia)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_bae_tipo_evidencia
        FOREIGN KEY (id_tipo_evidencia)
        REFERENCES tipo_evidencia (id_tipo_evidencia)
        ON UPDATE CASCADE;
