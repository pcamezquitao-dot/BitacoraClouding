-- Migración aprobada y validada primero en bitacora_admin_test_20260729.
--
-- Objetivo:
-- 1. Permitir crear, renombrar y desactivar tipos de participante.
-- 2. Separar el nombre editable del comportamiento funcional.
-- 3. Conservar todas las filas actuales y sus llaves foráneas.

-- Precondiciones de solo lectura.
SELECT codigo, descripcion
FROM tipos_participante
ORDER BY codigo;

SELECT cargo, COUNT(*) AS asignaciones
FROM empleado_area
GROUP BY cargo
ORDER BY cargo;

-- Cambio aditivo; no elimina tipos ni asignaciones existentes.
ALTER TABLE tipos_participante
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN actualizado_en DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);

-- La clave ya es primaria y no tiene referencias entrantes. Convertirla en
-- autoincremental evita colisiones al crear asignaciones concurrentes.
ALTER TABLE empleado_area
    MODIFY COLUMN id_empleado_area INT UNSIGNED NOT NULL AUTO_INCREMENT;

CREATE TABLE capacidades_participante (
    codigo VARCHAR(40) NOT NULL,
    descripcion VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (codigo),
    UNIQUE KEY uq_capacidad_descripcion (descripcion)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tipo_participante_capacidad (
    codigo_tipo INT NOT NULL,
    codigo_capacidad VARCHAR(40) NOT NULL,
    PRIMARY KEY (codigo_tipo, codigo_capacidad),
    CONSTRAINT fk_tipo_capacidad_tipo
        FOREIGN KEY (codigo_tipo)
        REFERENCES tipos_participante (codigo)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_tipo_capacidad_capacidad
        FOREIGN KEY (codigo_capacidad)
        REFERENCES capacidades_participante (codigo)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE administracion_catalogo_auditoria (
    id_auditoria BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    catalogo VARCHAR(60) NOT NULL,
    clave_registro VARCHAR(100) NOT NULL,
    operacion ENUM(
        'CREAR', 'EDITAR', 'ACTIVAR', 'DESACTIVAR', 'ELIMINAR'
    ) NOT NULL,
    valor_anterior JSON NULL,
    valor_nuevo JSON NULL,
    actor VARCHAR(100) NOT NULL,
    dispositivo VARCHAR(100) NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_auditoria),
    KEY idx_auditoria_catalogo_clave (catalogo, clave_registro),
    KEY idx_auditoria_creado_en (creado_en)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

INSERT INTO capacidades_participante (codigo, descripcion)
VALUES
    ('EMPLEADO', 'Puede actuar como empleado'),
    ('SUPERVISOR', 'Puede supervisar el área asignada y sus descendientes'),
    ('GERENTE', 'Puede autorizar operaciones reservadas a gerencia');

-- Mapeo transitorio aprobado para conservar el comportamiento de JAIME05.
-- P0001 mantiene por ahora su excepción controlada de gerente en Android.
INSERT INTO tipo_participante_capacidad (codigo_tipo, codigo_capacidad)
VALUES
    (1, 'EMPLEADO'),
    (2, 'EMPLEADO'),
    (3, 'SUPERVISOR');

-- Validaciones posteriores previstas.
SELECT codigo, descripcion, activo
FROM tipos_participante
ORDER BY codigo;

SELECT codigo, descripcion, activo
FROM capacidades_participante
ORDER BY codigo;

SELECT
    tp.codigo,
    tp.descripcion,
    GROUP_CONCAT(tpc.codigo_capacidad ORDER BY tpc.codigo_capacidad) AS capacidades
FROM tipos_participante AS tp
LEFT JOIN tipo_participante_capacidad AS tpc
  ON tpc.codigo_tipo = tp.codigo
GROUP BY tp.codigo, tp.descripcion
ORDER BY tp.codigo;
