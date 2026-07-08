# Analisis Fase 2 - Android BitacoraClouding

Fecha de analisis: 2026-07-06

Rama revisada: `feature/android-frontend-fase-1`

Frontend Android oficial: `android/bitacora-android`

Backend oficial:

- Swagger UI: `http://161.22.47.89/bitacora/docs`
- OpenAPI: `http://161.22.47.89/bitacora/openapi.json`
- Base URL real para Retrofit: `http://161.22.47.89/bitacora/`

## 1. Estructura actual del Android

El proyecto Android actual es una app Kotlin/Compose pequena y reutilizable como punto de partida.

Archivos principales:

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/cactus/bitacora/MainActivity.kt`
- `app/src/main/java/com/cactus/bitacora/data/Api.kt`
- `app/src/main/java/com/cactus/bitacora/data/models/Models.kt`

Dependencias relevantes ya presentes:

- Jetpack Compose
- Material 3
- Retrofit 2.11.0
- Gson converter
- OkHttp
- OkHttp logging interceptor
- ZXing

No se observa Room, SQLite offline, WorkManager ni autenticacion persistente. Eso coincide con el alcance minimo solicitado para Fase 2.

## 2. OpenAPI remoto consultado

El OpenAPI remoto responde correctamente en:

`http://161.22.47.89/bitacora/openapi.json`

El contrato declara:

- `openapi`: `3.1.0`
- `title`: `BACKEND_FASTAPI_BITACORA3`
- `version`: `0.1.0`
- `servers[0].url`: `/bitacora`

La prueba directa de salud tambien responde correctamente:

`GET http://161.22.47.89/bitacora/health`

Respuesta observada:

```json
{"status":"ok"}
```

## 3. Endpoints reales disponibles hoy

Todos los paths siguientes son relativos a la base `/bitacora`.

| Metodo | Ruta | Estado para Android | Modelo principal |
| --- | --- | --- | --- |
| GET | `/health` | Recomendado para Fase 2 | `HealthOut` |
| GET | `/participante/by_qr/{qr}` | Disponible, pero schema de respuesta no documentado | Respuesta sin schema |
| POST | `/areas/by_qr` | Disponible | `AreaByQrIn`, `AreaOut` |
| GET | `/empleados/{id_empleado}/supervisor` | Disponible, pero schema de respuesta no documentado | Respuesta sin schema |
| POST | `/bitacora_diaria` | Disponible y recomendado despues de health | `BitacoraDiariaCreate`, `BitacoraDiariaOut` |
| POST | `/bitacora_area_observacion` | Disponible | `BitacoraAreaObsCreate`, `BitacoraAreaObsOut` |
| POST | `/bitacora_area_evidencia/upload` | Disponible, multipart | `EvidenciaOut` |
| POST | `/bitacora_completa/upload` | Disponible, multipart | `BitacoraCompletaOut` |
| GET | `/bitacora_diaria/{id_bitacora}` | Disponible | `BitacoraDiariaOut` |
| GET | `/` | Disponible, diagnostico/root | Respuesta sin schema |

## 4. Modelos reales documentados por OpenAPI

### HealthOut

```kotlin
data class HealthOut(
    val status: String = "ok"
)
```

### AreaByQrIn

```kotlin
data class AreaByQrIn(
    val qr: String
)
```

### AreaOut

```kotlin
data class AreaOut(
    val id_area: Int,
    val descripcion: String
)
```

### BitacoraDiariaCreate

```kotlin
data class BitacoraDiariaCreate(
    val id_empleado: Int,
    val id_supervisor: Int? = null,
    val ts_in_min: Int? = null,
    val ts_out_min: Int? = null,
    val tipo_anotacion: Int? = null,
    val observaciones: String? = null,
    val client_uuid: String? = null
)
```

Notas:

- `id_empleado` es obligatorio.
- `id_supervisor` es opcional; si no se envia, el backend intenta calcularlo.
- `ts_in_min` es opcional; si no se envia, el backend usa la hora actual.
- `observaciones` acepta maximo 200 caracteres segun OpenAPI.

### BitacoraDiariaOut

```kotlin
data class BitacoraDiariaOut(
    val id_bitacora: Int,
    val id_empleado: Int,
    val id_supervisor: Int?,
    val ts_in_min: Int,
    val ts_out_min: Int?,
    val tipo_anotacion: Int?,
    val observaciones: String?
)
```

### BitacoraAreaObsCreate

```kotlin
data class BitacoraAreaObsCreate(
    val id_empleado: Int,
    val qr_area: String,
    val observaciones: String? = null,
    val tipo_anotacion: Int? = null
)
```

### BitacoraAreaObsOut

```kotlin
data class BitacoraAreaObsOut(
    val id_bitacora: Int?,
    val id_empleado: Int,
    val id_supervisor: Int,
    val ts_in_min: Int,
    val id_area: Int,
    val area_descripcion: String
)
```

### EvidenciaOut

```kotlin
data class EvidenciaOut(
    val id_evidencia: Int,
    val id_bitacora: Int?,
    val id_empleado: Int,
    val id_supervisor: Int,
    val ts_in_min: Int,
    val id_tipo_evidencia: Int,
    val archivo_url: String,
    val archivo_nombre: String?,
    val archivo_hash: String?,
    val tamanio_bytes: Int?,
    val duracion_seg: Int?,
    val orden: Int?
)
```

### BitacoraCompletaOut

```kotlin
data class BitacoraCompletaOut(
    val id_bitacora: Int,
    val evidencia: EvidenciaOut
)
```

## 5. Comparacion contra Android heredado

### `Api.kt`

El cliente heredado define estos endpoints:

```kotlin
@POST("auth/qr")
suspend fun loginQr(@Body req: QRLoginRequest): LoginResponse

@GET("catalogos/tipo_novedad")
suspend fun getTiposNovedad(): List<TipoNovedadOut>

@GET("bitacora/supervisor/{id}/empleados")
suspend fun getEmpleados(@Path("id") supervisorId: Int): List<EmpleadoOut>

@POST("bitacora/")
suspend fun crearBitacora(@Body req: BitacoraCreate): BitacoraCreateResponse
```

Ninguna de esas rutas aparece en el OpenAPI actual de BitacoraClouding.

Tambien usa una URL local heredada:

```kotlin
private const val BASE_URL = "http://192.168.0.5:8000/"
```

Debe reemplazarse por configuracion centralizada con:

```kotlin
http://161.22.47.89/bitacora/
```

### `Models.kt`

Modelos heredados que no corresponden directamente al contrato actual:

- `QRLoginRequest`
- `ParticipanteOut`
- `LoginResponse`
- `EmpleadoOut`
- `TipoNovedadOut`
- `BitacoraCreate`
- `BitacoraCreateResponse`

El backend actual no expone `auth/qr`, no expone catalogo de tipos de novedad y no expone lista de empleados por supervisor con la ruta heredada.

### `MainActivity.kt`

La pantalla heredada implementa un flujo completo de:

1. Login por QR de supervisor.
2. Validacion de supervisor.
3. Carga de empleados por supervisor.
4. Carga de tipos de novedad.
5. Seleccion de empleado.
6. Seleccion de tipo.
7. Creacion de bitacora en `/bitacora/`.

Ese flujo ya no coincide con el backend publicado. Para Fase 2 debe reemplazarse por una pantalla mucho mas pequena orientada a conectividad.

## 6. Partes reutilizables del Android heredado

Se pueden reutilizar:

- Estructura general Gradle del proyecto.
- Configuracion Android/Kotlin/Compose.
- Dependencias Retrofit, Gson, OkHttp y logging interceptor.
- Patron simple `Api.create()` como punto de inicio, idealmente separando luego `ApiConfig`, `BitacoraApi` y `Repository`.
- Uso de corrutinas desde Compose con `rememberCoroutineScope`.
- Estados basicos de UI: cargando, exito, error.
- `Scaffold`, `LazyColumn`, `Button`, `OutlinedButton`, `Text`, `MaterialTheme`.
- Manejo basico de excepciones de red para mostrar error en pantalla.
- Dependencias ZXing, aunque no deben activarse en Fase 2 salvo que se haga una prueba aislada posterior de QR.

## 7. Rutas, modelos y funciones antiguas que deben dejar de usarse

### Rutas a retirar o no usar

- `POST auth/qr`
- `GET catalogos/tipo_novedad`
- `GET bitacora/supervisor/{id}/empleados`
- `POST bitacora/`

### Funciones Retrofit a retirar o no usar

- `loginQr`
- `getTiposNovedad`
- `getEmpleados`
- `crearBitacora` en su forma heredada

### Modelos a retirar o no usar

- `QRLoginRequest`
- `ParticipanteOut`
- `LoginResponse`
- `EmpleadoOut`
- `TipoNovedadOut`
- `BitacoraCreate`
- `BitacoraCreateResponse`

### Flujo de UI a retirar o no usar en Fase 2

- Login por supervisor.
- Selector de empleados por supervisor.
- Selector de tipos de novedad desde catalogo.
- Guardado de novedad usando `id_supervisor`, `id_empleado`, `tipo_novedad`, `observaciones`.
- Cualquier supuesto de autenticacion.
- Cualquier almacenamiento offline.

## 8. Fase 2 minima y segura propuesta

La Fase 2 debe ser deliberadamente pequena para alinear Android con el backend real sin introducir complejidad prematura.

### Alcance incluido

1. Configuracion centralizada de base URL:

```kotlin
const val BASE_URL = "http://161.22.47.89/bitacora/"
```

2. Retrofit apuntando solo a rutas reales del OpenAPI.

3. Endpoint de salud:

```kotlin
@GET("health")
suspend fun health(): HealthOut
```

4. Repository minimo:

```kotlin
class BitacoraRepository(
    private val api: BitacoraApi = Api.create()
) {
    suspend fun checkHealth(): HealthOut = api.health()
}
```

5. Pantalla inicial simple:

- Titulo: BitacoraClouding
- Texto de base URL actual.
- Boton: Probar conexion
- Estado: pendiente, conectando, disponible, no disponible.
- Mostrar `status=ok` cuando `/health` responda.

6. Modelos reales iniciales:

- `HealthOut`
- `BitacoraDiariaCreate`
- `BitacoraDiariaOut`
- Opcionalmente `AreaByQrIn` y `AreaOut`, si se deja documentado pero sin UI.

7. Documentacion local de endpoints:

- Disponibles y modelados.
- Disponibles pero pendientes por falta de schema claro.
- Disponibles pero fuera de Fase 2 por ser multipart o flujo complejo.

### Fuera de alcance explicitamente

- Room.
- SQLite offline.
- WorkManager.
- Autenticacion.
- Login por QR.
- Pantallas complejas.
- Sincronizacion.
- Captura/subida de evidencia.
- Manejo de archivos multipart.
- Catalogos no presentes en OpenAPI.

## 9. Endpoints disponibles vs pendientes

### Implementar en Fase 2

- `GET /health`

### Documentar y preparar modelos

- `POST /bitacora_diaria`
- `GET /bitacora_diaria/{id_bitacora}`
- `POST /areas/by_qr`

### Pendientes por schema incompleto

- `GET /participante/by_qr/{qr}`
- `GET /empleados/{id_empleado}/supervisor`
- `GET /`

Aunque estas rutas existen, el OpenAPI no documenta claramente la forma de la respuesta. Conviene probarlas con datos reales o pedir que el backend publique schemas de respuesta antes de acoplar UI Android.

### Pendientes por complejidad multipart

- `POST /bitacora_area_evidencia/upload`
- `POST /bitacora_completa/upload`

Estos endpoints requieren archivos, `MultipartBody.Part` y decisiones de UI/permisos. Deben quedar fuera de Fase 2.

## 10. Recomendacion despues de health

El primer endpoint real recomendado despues de `GET /health` es:

`POST /bitacora_diaria`

Motivos:

- Esta documentado con request y response schemas completos.
- No requiere multipart.
- No requiere autenticacion segun OpenAPI.
- Tiene un payload minimo: solo `id_empleado` es obligatorio.
- Representa el nucleo funcional de la app: crear una bitacora diaria.
- Permite una siguiente pantalla de prueba segura con campos simples: `id_empleado`, `observaciones` y opcionalmente `id_supervisor`.

Implementacion sugerida para una fase posterior inmediata:

```kotlin
@POST("bitacora_diaria")
suspend fun crearBitacoraDiaria(
    @Body request: BitacoraDiariaCreate
): BitacoraDiariaOut
```

## 11. Conclusiones

El Android heredado es util como base tecnica, pero no como flujo funcional actual. La prioridad debe ser cortar dependencias con rutas inexistentes y confirmar conectividad real contra `http://161.22.47.89/bitacora/`.

La Fase 2 segura debe limitarse a configurar la URL oficial, implementar `health`, ajustar Retrofit/Repository a modelos reales y mostrar una pantalla simple de disponibilidad del backend.

Despues de eso, el siguiente paso natural es `POST /bitacora_diaria`, no login, no catalogos y no evidencia multipart.
