# C09_EVIDENCIAS

**ID:** C09_EVIDENCIAS  
**Objetivo:** adjuntar, almacenar y transcribir evidencias de bitácora.  
**Responsabilidad:** metadatos, archivos, límites, subida y recuperación de transcripciones.

## Propietario de

- `bitacora_area_evidencia`, `evidencia_transcripcion`, router `bitacora_area_evidencia.py`.
- Servicios de archivos, almacenamiento y transcripción.
- Android `EvidencePanel.kt`, entidades/DAO/repositorios de evidencia y pruebas.

## Archivos modificables

Router, schemas, servicios de evidencia, UI, entidades, DAO y pruebas propios.

## Archivos solo lectura relacionados

Bitácora, GPS, configuración compartida, sync worker y Manifest.

## Dependencias y contratos

- Consume `id_bitacora` de C07 y ubicación C15.
- Publica metadatos y estado de transcripción a consultas consumidoras.

## Datos / tablas

MariaDB `bitacora_area_evidencia`, `evidencia_transcripcion`; Room `bitacora_evidences`; directorios configurados de evidencia/uploads.

## Pruebas

- **Propia:** tipos, tamaño, hash, archivo y transcripción.
- **Contrato:** asociación con bitácora y cola offline.
- **Global mínima:** captura, subida y consulta de evidencia.

**Estado:** PRUEBAS  
**Versión:** PENDIENTE  
**Último commit estable:** PENDIENTE  
**Riesgos:** almacenamiento externo, recuperación al inicio y límites de tamaño.  
**Observaciones:** `app/main.py` inicia recuperación de transcripciones; es dependencia compartida.

## Extensión autorizada C20B

- Migración aditiva: `20260822_c20b_evidence_supervisor_actor.sql`.
- Agrega `id_supervisor_actor INT(11) NULL`, índice y FK a `participante.id_participante`.
- Los registros históricos permanecen en `NULL`; no se infieren actores.
- C20B exige actor no nulo al crear su evidencia de texto.
- Reversión: `20260822_c20b_evidence_supervisor_actor_rollback.sql`.
- El esquema real fue inspeccionado con `SHOW CREATE TABLE` y la migración fue aplicada únicamente al servidor HTTPS de desarrollo el 2026-08-22.

### Evidencia integrada 2026-08-22

- Respaldo: `/root/backups/c20a_c09_c20b_20260822_205223/bitacora_area_evidencia.sql.gz`.
- SHA-256: `a7045a991ba60df28f9a297270ac456903226d223424c25a5ab9ceca24f8b3dd`.
- Respaldo validado con `gzip -t` y `sha256sum -c`.
- Antes: 15 evidencias, máximo 259, `CHECK TABLE = OK`.
- Después de migrar: columna `INT(11) NULL`, índice y FK presentes; 15 de 15 históricas con actor `NULL`.
- Después de la prueba C20B: 16 evidencias; las 15 históricas siguen en `NULL` y solo la evidencia nueva 260 tiene actor 2.
- `CHECK TABLE bitacora_area_evidencia = OK`.

### Aplicación controlada posterior

1. Detener escrituras de evidencias.
2. Respaldar estructura y filas con `mysqldump --single-transaction bitacora bitacora_area_evidencia` usando credenciales suministradas fuera del repositorio.
3. Verificar el respaldo y ejecutar `migrations/20260822_c20b_evidence_supervisor_actor.sql`.
4. Confirmar columna, índice, FK, conteo de filas sin cambios y `CHECK TABLE bitacora_area_evidencia`.
5. Para revertir, detener escrituras, respaldar nuevamente y ejecutar `migrations/20260822_c20b_evidence_supervisor_actor_rollback.sql`.
