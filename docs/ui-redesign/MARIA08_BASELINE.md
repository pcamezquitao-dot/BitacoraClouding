# Línea base visual para maria08

## Punto de restauración

- Commit estable: `cf0ea9c`
- Etiqueta: `maria07-stable`
- Rama de trabajo: `feature/ui-redesign-maria08`
- APK de referencia: `maria07.apk`
- versionCode de referencia: `8`

## Pantallas y funciones existentes

- Selección de ambiente:
  - Administrador.
  - Ciudadano.
  - Seguimiento satelital de embalses.
- Inicio de Administrador:
  - Estado del backend.
  - Enrolamiento facial.
  - Administrar maestros.
  - Borrar las diez bitácoras más antiguas.
  - Crear, consultar y sincronizar.
- Inicio de Ciudadano:
  - Selección de los tipos de novedad.
  - Crear, consultar y sincronizar.
- Creación de bitácora:
  - Participante, supervisor y área.
  - Reconocimiento facial y QR.
  - GPS.
  - Evidencias de texto, foto, audio, video y galería.
  - Guardado local y sincronización.
- Consulta:
  - Bitácoras y evidencias locales/remotas.
- Sincronización:
  - Bitácoras, evidencias, catálogos y plantillas faciales.
- Administración:
  - Enrolamiento facial.
  - Maestros y empleado-área.
  - Actualización de catálogos.
  - Borrado administrativo.
- Seguimiento satelital:
  - Catálogo de seis embalses.
  - Imagen demostrativa de La Copa.
  - Zoom y regreso a selección de ambiente.

## Restricciones del rediseño

- No modificar Room, SQLite, migraciones ni entidades.
- No modificar FastAPI, MariaDB, Retrofit ni contratos.
- No modificar repositorios ni sincronización.
- No modificar cámaras, evidencias, GPS, QR ni biometría.
- No actualizar dependencias.
- Mantener los tres ambientes de `maria07`.

## Captura anterior

`docs/ui-redesign/maria07-before.png`
