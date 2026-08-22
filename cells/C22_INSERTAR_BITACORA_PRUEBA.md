# C22_INSERTAR_BITACORA_PRUEBA

**Estado:** VALIDADA

**Versión:** Maria49

**Objetivo:** permitir que un administrador genere, revise e inserte lotes reproducibles de marcaciones sintéticas, sin reemplazar registros existentes y usando Room y la sincronización oficial.

## Propiedad y contratos

- Propietaria del generador determinístico, la vista previa y las políticas de acceso, colisión e idempotencia del lote.
- Consume C02 para identificar un participante activo, C04 para su asignación, C07 para el contrato de entrada/salida, C11 para la transacción Room, C12 para la cola oficial y C16/C18 para el acceso administrativo.
- El backend existente solo amplía el origen admitido por `POST /bitacora_diaria` con `PRUEBA`; no existe una ruta paralela.
- No cambia esquemas, migraciones, asignaciones, catálogos ni registros reales.

## Seguridad y protección

- `ENABLE_TEST_BITACORA_INSERTION=true` únicamente en `debug`; en `release` es `false`.
- La pantalla se publica solo en Administración y el repositorio vuelve a comprobar administrador + compilación autorizada.
- La vista previa exige conexión para comparar el periodo con el catálogo remoto sin escribir.
- Un periodo con registros reales u otro lote queda bloqueado.
- Un lote ya existente y cualquier colisión de `client_uuid` quedan bloqueados.
- La escritura local usa una única transacción Room; una falla produce rollback completo.
- Cada fila usa origen `PRUEBA`, observación `DATOS_PRUEBA_<CODIGO>` y UUID determinístico.

## Evidencia técnica — 2026-08-22

- Pruebas focalizadas Android: 12 aprobadas; cubren 13 días/26 filas, tipos 4/5, campos `in/out`, estadística, reproducibilidad, domingo, autorización, release bloqueada, colisiones, lote repetido, rollback y consulta Room.
- Backend focalizado: 3 aprobadas.
- Suite backend: 169 aprobadas y 2 omitidas.
- Suite Android: 226 ejecutadas; 225 aprobadas y una falla preexistente ajena en `BitacoraQueryPolicyTest.remoteEvidenceWithoutLocalFileRequiresConnection`.
- `compileProductionDebugKotlin`, `compileProductionReleaseKotlin` y `assembleProductionDebug`: satisfactorios.
- BuildConfig comprobado: debug `true`, release `false`.
- Backend `bitacora-api` reiniciado correctamente; `GET /health` HTTPS respondió 200.

## Evidencia física — 2026-08-22

- Dispositivo único: V2035, serial `3065397347006YM`.
- Maria49 instalada con `adb install -r`, conservando almacenamiento y SQLite.
- Menú Administración mostró `INSERTAR BITÁCORA DE PRUEBA`; la pantalla mostró advertencia, parámetros, periodo inicial de dos semanas y vista previa.
- P0015 / 2026-08-09 a 2026-08-22 quedó bloqueado porque ya contenía registros.
- Lote autorizado P0015 / 2026-06-01 a 2026-06-14: 13 días, 1 domingo, 13 entradas, 13 salidas y 26 filas.
- Estadística persistida: 6.240 minutos, promedio 480,0000 min, desviación muestral 59,9236 min, mínimo 375 y máximo 578.
- MariaDB y SQLite: 26 UUID únicos; 0 duplicados; entradas con `out` no nulo = 0; salidas con campos/instantes distintos = 0.
- SQLite: integridad `ok`, Room 20, 173 bitácoras totales y 0 pendientes después de sincronizar.
- Consultar por P0015 y por `*` funcionó; entradas y salidas permanecieron separadas.
- Logcat y journal de backend: sin cierres, errores Room/SQLite, excepciones ni respuestas HTTP 4xx/5xx nuevas.

## Artefacto y reversión

- APK: `salidas/Maria49.apk`.
- Tamaño: 117.651.092 bytes.
- SHA-256: `A849152F6A71121E4BAF4D647068BAA7AED85E1B31B52E79060D0534D59A6BCA`.
- Versión: `versionCode 49`, `versionName maria49-c22-bitacora-prueba-dev`.
- Respaldo SQLite previo: `respaldos/c22_preinstall_20260822/databases.tar`.
- Respaldo backend: `/root/backups/c22_20260822/bitacora_uc03.py`.

## Congelamiento

La célula queda VALIDADA. El commit se limita a los cambios propios de C22; no se hace push ni tag en esta fase.
