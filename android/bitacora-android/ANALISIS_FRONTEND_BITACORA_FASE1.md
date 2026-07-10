# Analisis Frontend Bitacora - Fase 1

Fecha de revision: 2026-07-09

Proyecto Android revisado:

`C:\Bitacora\BitacoraClouding\android\bitacora-android`

Backend FastAPI revisado:

- Swagger: `http://161.22.47.89/bitacora/docs`
- OpenAPI: `http://161.22.47.89/bitacora/openapi.json`
- `servers[0].url`: `/bitacora`
- Base URL Android actual: `http://161.22.47.89/bitacora/`

## Alcance de esta fase

Esta fase es solo analisis y plan. No se modifico codigo Android, backend FastAPI ni base de datos. El unico artefacto creado es este informe Markdown.

## Arquitectura actual detectada

La app Android es un proyecto nativo Kotlin de un solo modulo Gradle:

- Root project: `bitacora-android`
- Modulo principal: `:app`
- Package/namespace: `com.cactus.bitacora`
- `minSdk`: 24
- `compileSdk`: 34
- `targetSdk`: 34
- Java/Kotlin target: 17
- UI principal concentrada en `MainActivity.kt`

La arquitectura actual es simple y funcional para MVP:

- `MainActivity.kt` contiene la navegacion manual por estado, pantallas Compose, estados UI sellados y logica de interaccion.
- `data/Api.kt` contiene configuracion Retrofit, cliente OkHttp y definicion parcial de endpoints.
- `data/BitacoraRepository.kt` coordina llamadas remotas y persistencia local.
- `data/models/Models.kt` contiene modelos Kotlin de una parte del contrato OpenAPI.
- `data/local/*` contiene Room para guardar bitacoras locales y sincronizar pendientes.

No se detecta separacion formal por capas UI/ViewModel/UseCase. La app funciona como MVP, pero la UI conoce directamente el repositorio y maneja estados locales con `remember`.

## Estructura relevante del proyecto Android

```text
android/bitacora-android/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/wrapper/gradle-wrapper.properties
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/cactus/bitacora/MainActivity.kt
    src/main/java/com/cactus/bitacora/data/Api.kt
    src/main/java/com/cactus/bitacora/data/BitacoraRepository.kt
    src/main/java/com/cactus/bitacora/data/models/Models.kt
    src/main/java/com/cactus/bitacora/data/local/BitacoraDao.kt
    src/main/java/com/cactus/bitacora/data/local/BitacoraDatabase.kt
    src/main/java/com/cactus/bitacora/data/local/BitacoraLocalEntity.kt
    src/main/java/com/cactus/bitacora/data/local/SyncStatus.kt
```

Tambien existen directorios generados por Android Studio/Gradle como `.gradle`, `.idea`, `.kotlin`, `build` y `app/build`.

## Gradle revisado

### `settings.gradle.kts`

- Repositorios de plugins: `google`, `mavenCentral`, `gradlePluginPortal`.
- Repositorios de dependencias: `google`, `mavenCentral`.
- `repositoriesMode`: `FAIL_ON_PROJECT_REPOS`.
- Proyecto raiz: `bitacora-android`.
- Modulo incluido: `:app`.

### `build.gradle.kts` raiz

Plugins declarados:

- `com.android.application` version `8.13.2`, sin aplicar en raiz.
- `org.jetbrains.kotlin.android` version `2.0.20`, sin aplicar en raiz.
- `org.jetbrains.kotlin.plugin.compose` version `2.0.20`, sin aplicar en raiz.
- `com.google.devtools.ksp` version `2.0.20-1.0.25`, sin aplicar en raiz.

### `app/build.gradle.kts`

Plugins aplicados:

- `com.android.application`
- `org.jetbrains.kotlin.android`
- `org.jetbrains.kotlin.plugin.compose`
- `com.google.devtools.ksp`

Configuracion relevante:

- `namespace = "com.cactus.bitacora"`
- `applicationId = "com.cactus.bitacora"`
- `compileSdk = 34`
- `minSdk = 24`
- `targetSdk = 34`
- `buildFeatures.compose = true`
- `kotlinOptions.jvmTarget = "17"`
- `compileOptions`: Java 17

### Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` apunta a:

`https://services.gradle.org/distributions/gradle-9.0-milestone-1-bin.zip`

Riesgo: es una version milestone de Gradle. Para estabilidad de equipo conviene evaluar una version estable compatible con AGP cuando el MVP este consolidado.

## Librerias actuales

### UI

- Jetpack Compose: si.
- Material 3: si.
- XML Views: no se detectan layouts XML.
- Activity Compose: si.

### Red

- Retrofit: si.
- Gson converter para Retrofit: si.
- OkHttp: si.
- OkHttp logging interceptor: si.

### Persistencia local

- Room runtime: si.
- Room KTX: si.
- Room compiler via KSP: si.

### Inyeccion de dependencias

- Hilt: no detectado.
- Dagger/Hilt plugins o dependencias: no detectados.

### Estado y arquitectura UI

- ViewModel: no detectado.
- `remember` / `mutableStateOf`: si.
- `rememberCoroutineScope`: si.
- Corrutinas: si, usadas desde pantallas Compose y `suspend fun` en Retrofit/Room.

### Navegacion

- Navigation Component / Navigation Compose: no detectado.
- Navegacion actual: enum `AppScreen` y botones que cambian `currentScreen`.

### Sincronizacion en background

- WorkManager: no detectado.
- Sincronizacion actual: manual desde pantalla `Sync`, llamando `repository.sincronizarPendientes()`.

### Camara y QR

- CameraX: si.
- ML Kit Barcode Scanning: si.
- ZXing core y JourneyApps ZXing Embedded: presentes como dependencias.
- Permiso `CAMERA`: declarado en `AndroidManifest.xml`.
- Permiso `INTERNET`: declarado en `AndroidManifest.xml`.

## Que ya existe en Android

### Pantallas/flujo UI existentes

En `MainActivity.kt` existen pantallas Compose para:

- Estado de backend / health.
- Crear bitacora diaria.
- Consultar bitacora diaria por `id_bitacora`.
- Sincronizacion local manual.
- QR Area con lectura por camara y validacion de formato.

### API remota implementada parcialmente

`BitacoraApi` implementa:

- `GET health`
- `POST areas/by_qr`
- `POST bitacora_diaria`
- `GET bitacora_diaria/{id_bitacora}`

### Modelos Kotlin existentes

`Models.kt` implementa:

- `HealthOut`
- `AreaByQrIn`
- `AreaOut`
- `BitacoraDiariaCreate`
- `BitacoraDiariaOut`

### Persistencia local existente

Room guarda bitacoras diarias locales con:

- `localId`
- `backendId`
- `idEmpleado`
- `idSupervisor`
- `tsInMin`
- `tsOutMin`
- `tipoAnotacion`
- `observaciones`
- `clientUuid`
- `syncStatus`
- `errorMessage`
- timestamps locales

Estados de sincronizacion:

- `PENDIENTE`
- `SINCRONIZADO`
- `ERROR`

### Sincronizacion existente

El repositorio intenta crear la bitacora diaria contra backend. Si falla, guarda localmente en Room como pendiente. Luego la pantalla `Sync` puede reintentar manualmente.

## Contrato OpenAPI del backend

OpenAPI consultado: `http://161.22.47.89/bitacora/openapi.json`

Version OpenAPI: `3.1.0`

Titulo: `BACKEND_FASTAPI_BITACORA3`

Version API: `0.1.0`

### Autenticacion

No se detectan `securitySchemes` en `components`.

No se detectan requisitos `security` globales ni por endpoint en el OpenAPI revisado.

Conclusion: segun OpenAPI, no hay autenticacion documentada. Si el backend aplica autenticacion fuera de OpenAPI, debe documentarse.

## Endpoints disponibles

| Metodo | Path | Proposito | Implementado en Android actual |
|---|---|---|---|
| GET | `/health` | Health check | Si |
| GET | `/participante/by_qr/{qr}` | Buscar participante por QR | No |
| POST | `/areas/by_qr` | Resolver area desde QR | Si |
| GET | `/empleados/{id_empleado}/supervisor` | Obtener supervisor de empleado | No |
| POST | `/bitacora_diaria` | Crear bitacora diaria | Si |
| POST | `/bitacora_area_observacion` | Crear observacion por area | No |
| POST | `/bitacora_area_evidencia/upload` | Subir evidencia de area | No |
| POST | `/bitacora_completa/upload` | Crear bitacora completa con archivo | No |
| GET | `/bitacora_diaria/{id_bitacora}` | Consultar bitacora diaria | Si |
| GET | `/` | Root | No |

## Detalle de endpoints

### `GET /health`

Parametros obligatorios: ninguno.

Entrada: ninguna.

Salida `200`: `HealthOut`.

Errores documentados: no hay errores explicitos.

### `GET /participante/by_qr/{qr}`

Parametros obligatorios:

- `qr` en path, tipo `string`.

Entrada: ninguna.

Salida `200`: schema vacio `{}` en OpenAPI.

Errores documentados:

- `422`: `HTTPValidationError`.

Nota: no hay modelo de salida definido, por lo que no se deben inventar campos Android hasta que backend documente la respuesta.

### `POST /areas/by_qr`

Parametros obligatorios: ninguno en path/query.

Body obligatorio: `AreaByQrIn`.

Salida `200`: `AreaOut`.

Errores documentados:

- `422`: `HTTPValidationError`.

### `GET /empleados/{id_empleado}/supervisor`

Parametros obligatorios:

- `id_empleado` en path, tipo `integer`.

Entrada: ninguna.

Salida `200`: schema vacio `{}` en OpenAPI.

Errores documentados:

- `422`: `HTTPValidationError`.

Nota: no hay modelo de salida definido. No se deben asumir campos del supervisor.

### `POST /bitacora_diaria`

Parametros obligatorios: ninguno en path/query.

Body obligatorio: `BitacoraDiariaCreate`.

Salida `200`: `BitacoraDiariaOut`.

Errores documentados:

- `422`: `HTTPValidationError`.

### `POST /bitacora_area_observacion`

Parametros obligatorios: ninguno en path/query.

Body obligatorio: `BitacoraAreaObsCreate`.

Salida `200`: `BitacoraAreaObsOut`.

Errores documentados:

- `422`: `HTTPValidationError`.

### `POST /bitacora_area_evidencia/upload`

Parametros obligatorios: ninguno en path/query.

Body obligatorio: `multipart/form-data` con schema `Body_upload_evidencia_bitacora_area_evidencia_upload_post`.

Salida `200`: `EvidenciaOut`.

Errores documentados:

- `422`: `HTTPValidationError`.

### `POST /bitacora_completa/upload`

Parametros obligatorios: ninguno en path/query.

Body obligatorio: `multipart/form-data` con schema `Body_crear_bitacora_completa_upload_bitacora_completa_upload_post`.

Salida `200`: `BitacoraCompletaOut`.

Errores documentados:

- `422`: `HTTPValidationError`.

### `GET /bitacora_diaria/{id_bitacora}`

Parametros obligatorios:

- `id_bitacora` en path, tipo `integer`.

Entrada: ninguna.

Salida `200`: `BitacoraDiariaOut`.

Errores documentados:

- `422`: `HTTPValidationError`.

### `GET /`

Parametros obligatorios: ninguno.

Entrada: ninguna.

Salida `200`: schema vacio `{}` en OpenAPI.

Errores documentados: no hay errores explicitos.

## Modelos principales del backend

### `HealthOut`

Campos:

- `status`: `string`, default `ok`.

Campos obligatorios: ninguno segun schema.

### `AreaByQrIn`

Campos:

- `qr`: `string`, obligatorio.

### `AreaOut`

Campos:

- `id_area`: `integer`, obligatorio.
- `descripcion`: `string`, obligatorio.

### `BitacoraDiariaCreate`

Campos:

- `id_empleado`: `integer`, obligatorio. Descripcion: `id_participante del empleado`.
- `id_supervisor`: `integer | null`, opcional. Si no se envia, el backend intenta calcularlo.
- `ts_in_min`: `integer | null`, opcional. Minutos Unix; si no se envia, usa la hora actual.
- `ts_out_min`: `integer | null`, opcional.
- `tipo_anotacion`: `integer | null`, opcional.
- `observaciones`: `string | null`, opcional, `maxLength: 200`.
- `client_uuid`: `string | null`, opcional, `maxLength: 40`.

### `BitacoraDiariaOut`

Campos:

- `id_bitacora`: `integer`, obligatorio.
- `id_empleado`: `integer`, obligatorio.
- `id_supervisor`: `integer | null`.
- `ts_in_min`: `integer`, obligatorio.
- `ts_out_min`: `integer | null`.
- `tipo_anotacion`: `integer | null`.
- `observaciones`: `string | null`.

### `BitacoraAreaObsCreate`

Campos:

- `id_empleado`: `integer`, obligatorio. Descripcion: `id_participante del empleado (desde QR)`.
- `qr_area`: `string`, obligatorio. Descripcion: `QR de area: AREA_ADMINISTRATIVA|id|descripcion`.
- `observaciones`: `string | null`, opcional.
- `tipo_anotacion`: `integer | null`, opcional.

### `BitacoraAreaObsOut`

Campos:

- `id_bitacora`: `integer | null`.
- `id_empleado`: `integer`, obligatorio.
- `id_supervisor`: `integer`, obligatorio.
- `ts_in_min`: `integer`, obligatorio.
- `id_area`: `integer`, obligatorio.
- `area_descripcion`: `string`, obligatorio.

### `Body_upload_evidencia_bitacora_area_evidencia_upload_post`

Multipart/form-data.

Campos:

- `id_bitacora`: `integer`, obligatorio.
- `id_empleado`: `integer`, obligatorio.
- `id_supervisor`: `integer`, obligatorio.
- `ts_in_min`: `integer`, obligatorio.
- `id_tipo_evidencia`: `integer`, obligatorio.
- `duracion_seg`: `integer | null`, opcional.
- `orden`: `integer | null`, opcional.
- `archivo`: archivo binario, obligatorio.

### `Body_crear_bitacora_completa_upload_bitacora_completa_upload_post`

Multipart/form-data.

Campos:

- `id_empleado`: `integer`, obligatorio.
- `id_tipo_evidencia`: `integer`, obligatorio.
- `archivo`: archivo binario, obligatorio.
- `id_supervisor`: `integer | null`, opcional.
- `ts_in_min`: `integer | null`, opcional.
- `ts_out_min`: `integer | null`, opcional.
- `tipo_anotacion`: `integer | null`, opcional.
- `observaciones`: `string | null`, opcional.
- `client_uuid`: `string | null`, opcional.
- `qr_area`: `string | null`, opcional.
- `duracion_seg`: `integer | null`, opcional.
- `orden`: `integer | null`, opcional.

### `EvidenciaOut`

Campos:

- `id_evidencia`: `integer`, obligatorio.
- `id_bitacora`: `integer | null`.
- `id_empleado`: `integer`, obligatorio.
- `id_supervisor`: `integer`, obligatorio.
- `ts_in_min`: `integer`, obligatorio.
- `id_tipo_evidencia`: `integer`, obligatorio.
- `archivo_url`: `string`, obligatorio.
- `archivo_nombre`: `string | null`.
- `archivo_hash`: `string | null`.
- `tamanio_bytes`: `integer | null`.
- `duracion_seg`: `integer | null`.
- `orden`: `integer | null`.

### `BitacoraCompletaOut`

Campos:

- `id_bitacora`: `integer`, obligatorio.
- `evidencia`: `EvidenciaOut`, obligatorio.

### `HTTPValidationError`

Campos:

- `detail`: lista de `ValidationError`.

### `ValidationError`

Campos:

- `loc`: lista de `string | integer`, obligatorio.
- `msg`: `string`, obligatorio.
- `type`: `string`, obligatorio.
- `input`: sin tipo explicito.
- `ctx`: objeto.

## Posibles errores documentados

El OpenAPI documenta principalmente:

- `422 Validation Error` para endpoints con parametros o body.
- `HTTPValidationError` como estructura de error.

No se documentan explicitamente:

- `400`
- `401`
- `403`
- `404`
- `409`
- `500`
- errores de negocio
- codigos para QR inexistente
- codigos para empleado sin supervisor
- codigos para duplicidad por `client_uuid`

En Android no se deben asumir reglas de negocio no documentadas. Debe manejarse fallo HTTP generico y mostrar mensajes robustos.

## Brechas entre Android actual y OpenAPI

### Ya cubierto

- Health check.
- Resolver area por QR.
- Crear bitacora diaria.
- Consultar bitacora diaria.
- Persistencia local basica para bitacora diaria.
- Reintento manual de bitacoras pendientes.
- Lectura de QR Area por camara en la UI actual.

### Falta implementar o completar

- Endpoint `GET /participante/by_qr/{qr}`.
- Modelo de salida para participante, si backend lo documenta.
- Endpoint `GET /empleados/{id_empleado}/supervisor`.
- Modelo de salida para supervisor, si backend lo documenta.
- Endpoint `POST /bitacora_area_observacion`.
- Modelos Android para `BitacoraAreaObsCreate` y `BitacoraAreaObsOut`.
- Upload multipart para evidencia de area.
- Upload multipart para bitacora completa.
- Modelos Android para evidencia.
- Persistencia offline de observaciones por area.
- Persistencia offline de evidencias/archivos.
- Sincronizacion en background con WorkManager.
- ViewModels para separar estado UI de Compose.
- Navegacion formal con Navigation Compose si la app crece.
- Manejo uniforme de errores HTTP.
- Estrategia de autenticacion si backend la agrega.

## Riesgos tecnicos

1. `MainActivity.kt` concentra demasiada responsabilidad: UI, estado, permisos, scanner, llamadas de repositorio y navegacion manual.
2. No hay ViewModel; el estado se pierde mas facilmente ante recreacion de actividad y es mas dificil testear.
3. No hay Hilt ni contenedor de dependencias; `Api.create()` y Room se crean manualmente.
4. No hay WorkManager; la sincronizacion offline depende de accion manual.
5. El OpenAPI tiene endpoints con respuesta `{}` para participante y supervisor; esto bloquea modelos Android confiables.
6. La app usa Gradle `9.0-milestone-1`; version no estable para un proyecto de produccion.
7. Existen dependencias QR duplicadas conceptualmente: CameraX/ML Kit y ZXing/JourneyApps. Conviene decidir una estrategia unica.
8. La base local actual solo modela bitacora diaria; no cubre area, evidencia ni archivos pendientes.
9. El contrato de errores del backend es minimo; Android debe manejar errores no documentados.
10. No hay autenticacion documentada; si se requiere en produccion, impacta Retrofit, almacenamiento seguro y flujos UI.
11. `client_uuid` tiene `maxLength: 40`; Android genera UUID estandar de 36 caracteres, correcto, pero debe mantenerse asi.
12. `observaciones` en `BitacoraDiariaCreate` tiene `maxLength: 200`; la UI deberia validar antes de enviar.

## PENDIENTES DE BACKEND

1. Definir schema de respuesta de `GET /participante/by_qr/{qr}`. Actualmente OpenAPI muestra `{}`.
2. Definir schema de respuesta de `GET /empleados/{id_empleado}/supervisor`. Actualmente OpenAPI muestra `{}`.
3. Confirmar si existe autenticacion o si la API queda abierta para el MVP.
4. Documentar errores de negocio: QR no encontrado, QR invalido, empleado sin supervisor, area no encontrada, archivo invalido, evidencia rechazada.
5. Confirmar formato exacto del QR de participante.
6. Confirmar formato exacto del QR de area. OpenAPI documenta `AREA_ADMINISTRATIVA|id|descripcion`.
7. Confirmar si `id_area` debe persistirse en bitacora diaria o solo en endpoints de area/observacion/evidencia.
8. Confirmar si `POST /bitacora_area_observacion` crea una bitacora nueva, agrega a una existente o ambas segun backend.
9. Confirmar semantica de `ts_in_min` y `ts_out_min`: minutos Unix UTC, hora local, zona horaria esperada.
10. Confirmar catalogo de `tipo_anotacion`.
11. Confirmar catalogo de `id_tipo_evidencia`.
12. Confirmar limites de archivo: tipos MIME permitidos, tamano maximo, extensiones, duracion maxima.
13. Confirmar si `client_uuid` garantiza idempotencia y que respuesta/codigo se entrega ante duplicados.
14. Documentar respuesta de `GET /`.
15. Confirmar si habra endpoints de catalogos para areas, tipos de evidencia o tipos de anotacion.

## Que falta implementar en Android

Sin modificar codigo en esta fase, el backlog tecnico recomendado es:

- Completar modelos Retrofit contra OpenAPI, sin inventar respuestas vacias.
- Separar `MainActivity.kt` en componentes o pantallas cuando se apruebe refactor.
- Introducir ViewModel para pantallas principales.
- Agregar capa de manejo de errores HTTP.
- Implementar lectura QR de participante cuando backend documente respuesta.
- Implementar flujo de empleado/supervisor segun respuesta real.
- Implementar observacion por area usando `BitacoraAreaObsCreate`.
- Implementar uploads multipart para evidencia.
- Extender Room para offline real de observaciones/evidencias.
- Agregar WorkManager para sincronizacion automatica.
- Documentar pruebas manuales y comandos de build.

## Plan por fases

### Fase 2: conexion Retrofit y modelos

Objetivo: alinear cliente Android con OpenAPI.

Tareas:

- Mantener `BASE_URL = http://161.22.47.89/bitacora/`.
- Revisar endpoints ya implementados y nombres contra OpenAPI.
- Agregar modelos faltantes que si estan definidos:
  - `BitacoraAreaObsCreate`
  - `BitacoraAreaObsOut`
  - `EvidenciaOut`
  - `BitacoraCompletaOut`
  - bodies multipart como parametros Retrofit.
- No implementar modelos para participante/supervisor hasta que backend documente su schema.
- Agregar wrappers de resultado o manejo centralizado de errores.
- Confirmar que Retrofit soporte multipart con `MultipartBody.Part` y `@Part`.

Criterio de salida:

- Android compila.
- Retrofit tiene definiciones para endpoints documentados con schema claro.
- Los endpoints con schema vacio quedan documentados como pendientes, no inventados.

### Fase 3: pantallas principales

Objetivo: construir flujos de uso principales del MVP.

Pantallas candidatas:

- Health/conectividad.
- Escaneo QR participante, sujeto a schema backend.
- Escaneo QR area.
- Crear bitacora diaria.
- Crear observacion por area.
- Adjuntar evidencia.
- Consultar bitacora.
- Estado de sincronizacion.

Tareas:

- Separar pantallas Compose en funciones/archivos si se aprueba.
- Evaluar ViewModel por pantalla.
- Agregar validaciones UI:
  - `id_empleado` requerido.
  - `observaciones` maximo 200 para bitacora diaria.
  - QR area con formato esperado.
  - archivos requeridos para evidencia.
- Mantener flujo manual tecnico solo donde ayude a pruebas.

Criterio de salida:

- Pantallas principales operables en celular.
- Errores visibles y comprensibles.
- No se rompe creacion de bitacora existente.

### Fase 4: Room/offline

Objetivo: persistir trabajo de campo sin conexion.

Tareas:

- Revisar entidad actual `BitacoraLocalEntity`.
- Agregar entidades solo si el contrato y flujo lo exigen:
  - bitacora diaria pendiente.
  - observacion por area pendiente.
  - evidencia pendiente con ruta local de archivo.
- Definir estado de sincronizacion unico.
- Guardar `client_uuid` en operaciones idempotentes.
- Guardar errores de sincronizacion para diagnostico.
- Considerar migraciones Room si cambia schema.

Criterio de salida:

- Modo offline permite crear registros criticos.
- Datos pendientes quedan consultables localmente.
- App no pierde registros al cerrar/reabrir.

### Fase 5: WorkManager/sincronizacion

Objetivo: sincronizar pendientes automaticamente.

Tareas:

- Agregar WorkManager.
- Crear worker de sincronizacion con restricciones de red.
- Reintentar pendientes con backoff.
- Sincronizar primero bitacoras base y luego evidencias dependientes.
- Evitar duplicados usando `client_uuid` si backend confirma idempotencia.
- Mantener pantalla manual de Sync como control operativo.

Criterio de salida:

- Pendientes se sincronizan cuando vuelve internet.
- La UI muestra conteos y errores.
- No se duplican registros en condiciones normales.

### Fase 6: pruebas, compilacion y documentacion

Objetivo: cerrar MVP con evidencia reproducible.

Tareas:

- Compilar `:app:assembleDebug`.
- Probar en celular real:
  - online.
  - offline.
  - reconexion.
  - QR valido.
  - QR invalido.
  - backend no disponible.
  - upload de evidencia.
- Documentar comandos de build.
- Documentar permisos Android.
- Documentar ejemplos de QR.
- Documentar limitaciones conocidas.

Criterio de salida:

- APK debug compila.
- Flujos principales probados manualmente.
- Documentacion suficiente para repetir pruebas.

## Recomendacion de siguiente paso

Antes de implementar Fase 2, conviene pedir al backend aclaracion de los schemas vacios para participante y supervisor. Mientras eso se resuelve, Android puede avanzar con modelos ya definidos por OpenAPI: bitacora diaria, area, observacion por area y evidencia multipart.

