-- Carga idempotente de dimension_calendario para el quinquenio 2025-2029.
-- Solo inserta registros ausentes. No modifica festivos ni periodos existentes.

DELIMITER //

DROP PROCEDURE IF EXISTS cargar_dimension_calendario_2025_2029//

CREATE PROCEDURE cargar_dimension_calendario_2025_2029()
BEGIN
    DECLARE fecha_actual DATE DEFAULT '2025-01-01';
    DECLARE id_quinquenio BIGINT UNSIGNED;
    DECLARE id_anio BIGINT UNSIGNED;
    DECLARE id_mes BIGINT UNSIGNED;

    START TRANSACTION;

    INSERT IGNORE INTO dimension_calendario (
        id_padre, nivel, codigo, nombre, fecha_inicio, fecha_fin,
        numero_anio, numero_mes, numero_dia, numero_dia_semana,
        nombre_dia_semana, anio_semana_iso, numero_semana_iso,
        es_fin_semana, es_festivo, nombre_festivo, orden_periodo, activo
    ) VALUES (
        NULL, 'QUINQUENIO', 'CAL-Q-2025-2029', '2025-2029',
        '2025-01-01', '2029-12-31', NULL, NULL, NULL, NULL,
        NULL, NULL, NULL, NULL, FALSE, NULL, 20250000, TRUE
    );

    SELECT id_periodo INTO id_quinquenio
      FROM dimension_calendario
     WHERE nivel = 'QUINQUENIO' AND codigo = 'CAL-Q-2025-2029';

    WHILE fecha_actual <= '2029-12-31' DO
        IF DAYOFYEAR(fecha_actual) = 1 THEN
            INSERT IGNORE INTO dimension_calendario (
                id_padre, nivel, codigo, nombre, fecha_inicio, fecha_fin,
                numero_anio, numero_mes, numero_dia, numero_dia_semana,
                nombre_dia_semana, anio_semana_iso, numero_semana_iso,
                es_fin_semana, es_festivo, nombre_festivo, orden_periodo, activo
            ) VALUES (
                id_quinquenio,
                'ANIO',
                CONCAT('CAL-Y-', YEAR(fecha_actual)),
                CAST(YEAR(fecha_actual) AS CHAR),
                MAKEDATE(YEAR(fecha_actual), 1),
                STR_TO_DATE(CONCAT(YEAR(fecha_actual), '-12-31'), '%Y-%m-%d'),
                YEAR(fecha_actual), NULL, NULL, NULL,
                NULL, NULL, NULL, NULL, FALSE, NULL,
                YEAR(fecha_actual) * 10000,
                TRUE
            );
        END IF;

        SELECT id_periodo INTO id_anio
          FROM dimension_calendario
         WHERE nivel = 'ANIO'
           AND codigo = CONCAT('CAL-Y-', YEAR(fecha_actual));

        IF DAY(fecha_actual) = 1 THEN
            INSERT IGNORE INTO dimension_calendario (
                id_padre, nivel, codigo, nombre, fecha_inicio, fecha_fin,
                numero_anio, numero_mes, numero_dia, numero_dia_semana,
                nombre_dia_semana, anio_semana_iso, numero_semana_iso,
                es_fin_semana, es_festivo, nombre_festivo, orden_periodo, activo
            ) VALUES (
                id_anio,
                'MES',
                CONCAT('CAL-M-', DATE_FORMAT(fecha_actual, '%Y-%m')),
                ELT(
                    MONTH(fecha_actual),
                    'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
                    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre',
                    'Diciembre'
                ),
                fecha_actual,
                LAST_DAY(fecha_actual),
                YEAR(fecha_actual), MONTH(fecha_actual), NULL, NULL,
                NULL, NULL, NULL, NULL, FALSE, NULL,
                YEAR(fecha_actual) * 10000 + MONTH(fecha_actual) * 100,
                TRUE
            );
        END IF;

        SELECT id_periodo INTO id_mes
          FROM dimension_calendario
         WHERE nivel = 'MES'
           AND codigo = CONCAT('CAL-M-', DATE_FORMAT(fecha_actual, '%Y-%m'));

        INSERT IGNORE INTO dimension_calendario (
            id_padre, nivel, codigo, nombre, fecha_inicio, fecha_fin,
            numero_anio, numero_mes, numero_dia, numero_dia_semana,
            nombre_dia_semana, anio_semana_iso, numero_semana_iso,
            es_fin_semana, es_festivo, nombre_festivo, orden_periodo, activo
        ) VALUES (
            id_mes,
            'DIA',
            CONCAT('CAL-D-', DATE_FORMAT(fecha_actual, '%Y-%m-%d')),
            CONCAT(
                ELT(
                    WEEKDAY(fecha_actual) + 1,
                    'Lunes', 'Martes', 'Miércoles', 'Jueves',
                    'Viernes', 'Sábado', 'Domingo'
                ),
                ' ', DAY(fecha_actual)
            ),
            fecha_actual,
            fecha_actual,
            YEAR(fecha_actual),
            MONTH(fecha_actual),
            DAY(fecha_actual),
            WEEKDAY(fecha_actual) + 1,
            ELT(
                WEEKDAY(fecha_actual) + 1,
                'Lunes', 'Martes', 'Miércoles', 'Jueves',
                'Viernes', 'Sábado', 'Domingo'
            ),
            FLOOR(YEARWEEK(fecha_actual, 3) / 100),
            MOD(YEARWEEK(fecha_actual, 3), 100),
            WEEKDAY(fecha_actual) >= 5,
            FALSE,
            NULL,
            YEAR(fecha_actual) * 10000
                + MONTH(fecha_actual) * 100
                + DAY(fecha_actual),
            TRUE
        );

        SET fecha_actual = DATE_ADD(fecha_actual, INTERVAL 1 DAY);
    END WHILE;

    COMMIT;
END//

CALL cargar_dimension_calendario_2025_2029()//
DROP PROCEDURE cargar_dimension_calendario_2025_2029//

DELIMITER ;
