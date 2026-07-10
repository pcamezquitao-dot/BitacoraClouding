# README Frontend Bitacora

## FASE 2 - CONEXION BACKEND

### Objetivo

Crear la capa base de conexion al backend FastAPI usando Retrofit y OkHttp, con URL centralizada, modelos Kotlin basados en OpenAPI, servicios API separados por funcionalidad y repositorios base que devuelven estados claros.

Backend revisado:

- Swagger: `http://161.22.47.89/bitacora/docs`
- OpenAPI: `http://161.22.47.89/bitacora/openapi.json`

### URL base configurada

La URL base esta centralizada en:

`app/src/main/java/com/cactus/bitacora/util/AppConfig.kt`

```kotlin
const val BASE_URL = "http://161.22.47.89/bitacora/"
```

El cliente legacy `data/Api.kt` tambien usa esta configuracion mediante `AppConfig`, para evitar URLs hardcodeadas repetidas.

### Dependencias utilizadas

No fue necesario cambiar versiones de Kotlin, Android Gradle Plugin ni Compose.

Dependencias relevantes ya presentes en `app/build.gradle.kts`:

- Retrofit: `com.squareup.retrofit2:retrofit:2.11.0`
- Gson converter: `com.squareup.retrofit2:converter-gson:2.11.0`
- OkHttp: `com.squareup.okhttp3:okhttp:4.12.0`
- Logging interceptor: `com.squareup.okhttp3:logging-interceptor:4.12.0`
- Corrutinas disponibles via lifecycle/runtime KTX y llamadas `suspend`.

El interceptor HTTP queda en nivel `BASIC` para evitar exponer cuerpos de peticiones o respuestas en logs.

### Cliente Retrofit centralizado

Archivo:

`app/src/main/java/com/cactus/bitacora/api/NetworkClient.kt`

Incluye:

- `OkHttpClient` unico.
- Timeouts de 30 segundos.
- `HttpLoggingInterceptor.Level.BASIC`.
- `Retrofit` unico con `GsonConverterFactory`.
- Metodo generico `createService`.

### Estados de respuesta

Archivo:

`app/src/main/java/com/cactus/bitacora/util/NetworkResult.kt`

Estados:

- `Loading`
- `Success`
- `Error`
- `Offline`

Manejo base de errores:

`app/src/main/java/com/cactus/bitacora/util/NetworkErrorHandler.kt`

Casos cubiertos:

- HTTP error con codigo.
- Timeout.
- Ausencia de conexion o backend no disponible.
- Error inesperado.

### Servicios Retrofit creados

Paquete:

`app/src/main/java/com/cactus/bitacora/api`

Servicios:

- `HealthApiService`
- `AreaApiService`
- `BitacoraApiService`
- `ParticipanteApiService`
- `EmpleadoApiService`
- `RootApiService`

### Endpoints integrados

| Metodo | Endpoint | Servicio |
|---|---|---|
| GET | `health` | `HealthApiService` |
| POST | `areas/by_qr` | `AreaApiService` |
| POST | `bitacora_diaria` | `BitacoraApiService` |
| GET | `bitacora_diaria/{id_bitacora}` | `BitacoraApiService` |
| POST | `bitacora_area_observacion` | `BitacoraApiService` |
| POST | `bitacora_area_evidencia/upload` | `BitacoraApiService` |
| POST | `bitacora_completa/upload` | `BitacoraApiService` |
| GET | `participante/by_qr/{qr}` | `ParticipanteApiService` |
| GET | `empleados/{id_empleado}/supervisor` | `EmpleadoApiService` |
| GET | `.` | `RootApiService` |

### Modelos creados

Paquete:

`app/src/main/java/com/cactus/bitacora/model`

Modelos basados en OpenAPI:

- `HealthOut`
- `AreaByQrIn`
- `AreaOut`
- `BitacoraDiariaCreate`
- `BitacoraDiariaOut`
- `BitacoraAreaObsCreate`
- `BitacoraAreaObsOut`
- `EvidenciaOut`
- `BitacoraCompletaOut`
- `HTTPValidationError`
- `ValidationError`

Los modelos legacy en `data/models/Models.kt` quedaron como alias hacia el paquete `model`, para conservar compatibilidad con pantallas y repositorios existentes sin mantener dos contratos separados.

### Repositorios base creados

Paquete:

`app/src/main/java/com/cactus/bitacora/repository`

Repositorios:

- `HealthRepository`
- `AreaRepository`
- `BitacoraRepository`
- `ParticipanteRepository`
- `EmpleadoRepository`
- `RootRepository`

Estos repositorios consumen Retrofit y devuelven `NetworkResult`.

No se conectaron a Room en esta fase.

No se agrego WorkManager en esta fase.

### Pendientes de backend

No se inventaron campos para respuestas que OpenAPI no define claramente.

Pendientes:

- `GET /participante/by_qr/{qr}` tiene respuesta `200` con schema vacio `{}`.
- `GET /empleados/{id_empleado}/supervisor` tiene respuesta `200` con schema vacio `{}`.
- `GET /` tiene respuesta `200` con schema vacio `{}`.
- No hay `securitySchemes` ni autenticacion documentada en OpenAPI.
- No estan documentados errores de negocio como QR inexistente, empleado sin supervisor, area no encontrada o duplicidad por `client_uuid`.
- No estan documentados catalogos para `tipo_anotacion` ni `id_tipo_evidencia`.
- No estan documentados limites de upload: MIME types, tamano maximo, duracion maxima o extensiones permitidas.

Para endpoints con schema vacio se uso `Response<Unit>` en servicios Retrofit, y los repositorios solo reportan exito/error sin inventar modelos.

### Como probar la compilacion

Desde:

`C:\Bitacora\BitacoraClouding\android\bitacora-android`

En PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean assembleDebug
```

En macOS/Linux o terminal compatible:

```bash
./gradlew clean assembleDebug
```

### Alcance explicitamente no incluido

- No se implemento Fase 3.
- No se redisenaron pantallas.
- No se agrego Room nuevo.
- No se agrego WorkManager.
- No se modifico backend FastAPI.
- No se modifico base de datos MariaDB.

