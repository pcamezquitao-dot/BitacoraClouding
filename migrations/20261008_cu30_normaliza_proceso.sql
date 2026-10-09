CREATE TABLE IF NOT EXISTS cu30_trabajo (
    id_trabajo CHAR(36) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    requerimiento_original LONGTEXT NOT NULL,
    requerimiento_sha256 CHAR(64) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    version_actual INT UNSIGNED NOT NULL DEFAULT 1,
    creado_por VARCHAR(100) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_trabajo),
    KEY ix_cu30_trabajo_estado (estado),
    KEY ix_cu30_trabajo_actualizado (actualizado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cu30_version (
    id_version CHAR(36) NOT NULL,
    id_trabajo CHAR(36) NOT NULL,
    numero INT UNSIGNED NOT NULL,
    definicion_json LONGTEXT NOT NULL,
    texto_normalizado LONGTEXT NOT NULL,
    plantuml LONGTEXT NOT NULL,
    xml_definicion LONGTEXT NOT NULL,
    pendientes_json LONGTEXT NOT NULL,
    catalogo_sha256 CHAR(64) NOT NULL,
    contenido_sha256 CHAR(64) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_por VARCHAR(100) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    aprobado_por VARCHAR(100) NULL,
    aprobado_en DATETIME(6) NULL,
    PRIMARY KEY (id_version),
    UNIQUE KEY uq_cu30_version_numero (id_trabajo, numero),
    CONSTRAINT fk_cu30_version_trabajo FOREIGN KEY (id_trabajo)
        REFERENCES cu30_trabajo (id_trabajo) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cu30_auditoria (
    id_auditoria BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_trabajo CHAR(36) NOT NULL,
    id_version CHAR(36) NULL,
    accion VARCHAR(30) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    detalle_json LONGTEXT NULL,
    registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_auditoria),
    KEY ix_cu30_auditoria_trabajo (id_trabajo, registrado_en),
    CONSTRAINT fk_cu30_auditoria_trabajo FOREIGN KEY (id_trabajo)
        REFERENCES cu30_trabajo (id_trabajo) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cu30_auditoria_version FOREIGN KEY (id_version)
        REFERENCES cu30_version (id_version) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
