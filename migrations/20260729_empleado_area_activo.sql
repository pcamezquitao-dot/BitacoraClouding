-- Retiro lógico aditivo de asignaciones empleado-área.
-- Los registros existentes permanecen activos.
ALTER TABLE empleado_area
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE AFTER fecha_final,
    ADD INDEX idx_empleado_area_activo_vigencia
        (activo, fecha_inicia, fecha_final);
