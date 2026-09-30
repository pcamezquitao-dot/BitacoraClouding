# Ficha común del entorno de pruebas de Bitácora

**Identificador:** ENT-001  
**Revisión:** 01  
**Fecha de verificación del entorno:** 2026-09-30 08:58:11 -05:00 (America/Bogota)  
**Fecha de ejecución de casos de prueba:** PENDIENTE; esta ficha no ejecuta ni aprueba ningún caso.  
**Estado general:** PENDIENTE

## 1. Precondiciones

- **Versión del código fuente:** rama `fix/c03-c11-c12-c16-areas-sync`; commit completo `31124c496b81e058f5481c24fc14fa534ff00ae3`; existen cambios locales sin commit. La APK declarada por el código tiene `versionName=maria49-c22-bitacora-prueba-dev` y `versionCode=49`. La versión instalada y su correspondencia con el código están PENDIENTES porque el V2035 no apareció en ADB.
- **Carpeta del código fuente:** `C:\Bitacora\BitacoraClouding-areas-sync-fix`.
- **Base MariaDB:** nombre confirmado mediante la conexión de FastAPI: `bitacora`; host configurado para la aplicación: `localhost`; puerto `3306`; servidor Clouding alcanzado en `161.22.47.89`, con `@@hostname=maestro`.
- **Base SQLite:** nombre declarado `bitacora_local.db`; versión Room declarada `20`. La existencia y `PRAGMA user_version` de la base instalada en el V2035 están PENDIENTES.
- **Fecha de ejecución:** PENDIENTE. La verificación del entorno se realizó el `2026-09-30` en zona `America/Bogota`.
- **Acceso a Administración:** PENDIENTE. No se pudo realizar una comprobación física ni identificar al usuario de la sesión porque no había dispositivo ADB conectado. La presencia del selector `Administrador` en el código no se considera prueba de acceso real.
- **Celular:** modelo esperado por el requerimiento: V2035; identificador ADB y estado: PENDIENTES. `adb devices -l` no devolvió dispositivos.
- **Catálogo sincronizado:** PENDIENTE. MariaDB devolvió 7 filas de `areas_administrativas`; SQLite no pudo consultarse, por lo que no se compararon contenido e identificadores.
- **Query de referencia C16:** ejecutado en MariaDB el `2026-09-30T08:57:52-05:00`. Consulta y resultado completo: `evidencias/ENT-001/rev01/areas_administrativas_mariadb.json`.
- **Documentos rectores:** `AGENTS.md` e `INVARIANTES.md` leídos. `CATALOGO.md` no existe en la raíz ni en otra carpeta del repositorio al momento de la verificación.

## 2. Evidencias y estado

| Precondición | Valor verificado | Método de comprobación | Evidencia | Fecha/hora | Estado |
|---|---|---|---|---|---|
| Rama y commit | `fix/c03-c11-c12-c16-areas-sync`; `31124c496b81e058f5481c24fc14fa534ff00ae3` | `git branch --show-current`; `git rev-parse HEAD` | `git_estado.txt` | 2026-09-30 08:58 -05:00 | CUMPLE |
| Estado Git | Hay archivos modificados y no rastreados; no es un árbol limpio | `git status --short` y `git status --porcelain=v2 --branch` | `git_estado.txt` | 2026-09-30 08:58 -05:00 | CUMPLE |
| Versión declarada en código | `versionName=maria49-c22-bitacora-prueba-dev`; `versionCode=49` | Lectura de `app/build.gradle.kts` | `versiones_codigo.txt` | 2026-09-30 08:58 -05:00 | CUMPLE |
| APK instalada | No verificable; tampoco puede demostrarse correspondencia con el commit actual | `adb devices -l` vacío; no se ejecutó instalación | `dispositivo_adb.txt` | 2026-09-30 08:58 -05:00 | PENDIENTE |
| Ruta de código | `C:\Bitacora\BitacoraClouding-areas-sync-fix` | `Get-Location` | `git_estado.txt` | 2026-09-30 08:58 -05:00 | CUMPLE |
| MariaDB — base | `bitacora` | `SELECT DATABASE()` usando `app.core.db.engine` en `/srv/bitacora` | `areas_administrativas_mariadb.json` | 2026-09-30 08:57:52 -05:00 | CUMPLE |
| MariaDB — servidor | Aplicación: `localhost:3306`; host Clouding: `161.22.47.89`; servidor DB: `maestro:3306` | Configuración efectiva cargada por FastAPI y `SELECT @@hostname, @@port` | `areas_administrativas_mariadb.json` | 2026-09-30 08:57:52 -05:00 | CUMPLE |
| SQLite — nombre declarado | `bitacora_local.db` | Lectura de `Room.databaseBuilder` | `versiones_codigo.txt` | 2026-09-30 08:58 -05:00 | CUMPLE |
| Room — versión declarada | `20` | Lectura de `@Database(version = 20)` | `versiones_codigo.txt` | 2026-09-30 08:58 -05:00 | CUMPLE |
| SQLite — base y versión instaladas | No verificables; falta `PRAGMA user_version` del V2035 | El dispositivo no apareció en ADB | `dispositivo_adb.txt` | 2026-09-30 08:58 -05:00 | PENDIENTE |
| Acceso real a Administración | No comprobado; usuario/identificador de la sesión no disponible | Requiere abrir la APK instalada y acceder a Administración en el V2035 | `dispositivo_adb.txt` | 2026-09-30 08:58 -05:00 | PENDIENTE |
| Conexión V2035 | Ningún identificador; estado desconectado/no visible para ADB | `adb devices -l` | `dispositivo_adb.txt` | 2026-09-30 08:58 -05:00 | NO CUMPLE |
| Catálogo C16 en MariaDB | 7 áreas; IDs y contenido conservados | `SELECT id_Area_Administrativa, descripcion, nodo_padre, nombre_corto ...` | `areas_administrativas_mariadb.json` | 2026-09-30 08:57:52 -05:00 | CUMPLE |
| Comparación MariaDB–SQLite | No realizada; no se usaron conteos ni estado READY como sustituto | Falta acceso a `areas_administrativas_locales` en el dispositivo | `comparacion_catalogo.txt` | 2026-09-30 08:58 -05:00 | PENDIENTE |
| `CATALOGO.md` | No encontrado en todo el repositorio | Búsqueda recursiva exacta por nombre | `documentos_rectores.txt` | 2026-09-30 08:58 -05:00 | PENDIENTE |

### Impedimentos y acciones necesarias

1. Conectar y desbloquear el V2035, autorizar depuración USB y confirmar que aparezca como `device` en `adb devices -l`.
2. Consultar en lectura `dumpsys package com.cactus.bitacora` para obtener `versionName` y `versionCode`; conservar evidencia del APK/artefacto si se pretende acreditar correspondencia con el commit.
3. Acceder en lectura a `bitacora_local.db` mediante `run-as` o mecanismo equivalente y ejecutar `PRAGMA user_version`.
4. Consultar `areas_administrativas_locales` y comparar fila por fila `idArea`, `nombreArea`, `idPadre` y `nombreCorto` contra MariaDB. El modelo SQLite no conserva exactamente los mismos nombres de columnas; la correspondencia esperada debe comprobarse, no suponerse.
5. Abrir la APK y demostrar acceso efectivo a Administración, registrando el identificador aplicable. Seleccionar el entorno en la interfaz no basta si no se confirma la pantalla protegida requerida por el caso.
6. Determinar con Patricia si la ausencia de `CATALOGO.md` requiere suministrar ese documento. Su ausencia se registra y no se reemplaza con información histórica.

## 3. Reutilización y vigencia

- **Ruta, servidor y nombres de bases:** verificar nuevamente cuando cambie la configuración.
- **Rama, commit, APK y versión Room:** verificar al iniciar cada ciclo y después de cambios de código o instalación.
- **Fecha, usuario, acceso y conexión del celular:** comprobar al iniciar cada sesión.
- **Sincronización y query de referencia:** comprobar antes de los casos que dependan del catálogo y repetir si los datos fueron modificados.
- Una revisión de esta ficha deja de acreditar el entorno cuando cambia cualquiera de esos elementos. Los estados PENDIENTE o NO CUMPLE impiden declarar el entorno listo.

Referencia para casos de prueba:

**Entorno de pruebas:** ENT-001, revisión 01.  
**Comprobación de vigencia:** 2026-09-30 08:58:11 -05:00; resultado PENDIENTE por ausencia del V2035 en ADB, falta de comprobación de APK/Room/acceso y falta de comparación MariaDB–SQLite.  
**Precondiciones específicas del caso:** consultar la tabla de la sección 2 y, para C16, `evidencias/ENT-001/rev01/areas_administrativas_mariadb.json` junto con la comparación SQLite todavía pendiente.

## 4. Alcance de esta revisión

Se realizaron únicamente lecturas de Git, archivos fuente, configuración efectiva no secreta, servicio FastAPI, MariaDB y estado ADB. No se modificaron código, configuración, bases, permisos ni usuarios; no se instaló la APK ni se forzó sincronización. La creación de esta ficha y sus evidencias es la única escritura realizada.

