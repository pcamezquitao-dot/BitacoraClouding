# Versiones MARIA

## maria01.apk

- Fecha: 2026-07-30
- versionCode: 2
- versionName: maria01
- Objetivo: corrección puntual del error al abrir la cámara externa al grabar
  evidencias de tipo VIDEO detectado en `jaime02.apk`.
- Causa: fallo `NullPointerException` dentro de `com.android.camera` (Vivo),
  en `VerticalSeekBar.setThumb`, al abrir su módulo `VideoModule`.
- Solución: captura de video interna con CameraX 1.3.4; se conserva el mismo
  flujo posterior de MP4, vista previa, almacenamiento privado, Room y
  sincronización.
- Archivos principales:
  - `android/bitacora-android/app/build.gradle.kts`
  - `android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/evidence/EvidencePanel.kt`
  - `android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/evidence/InternalVideoCapture.kt`
- Pruebas automatizadas: 153 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success`; instalada sobre los datos existentes con
  `adb install -r`.
- SHA-256:
  `84B067E98921CD82EF4BC6BF02B46FEBD7892E2F048860B60F86110CBD08418A`.
- Recuperación facial: 3 plantillas centrales activas descargadas y marcadas
  `SINCRONIZADO` (`P0001`, `P0002`, `P0030`).
- Validación física: pendiente.
- Commit: pendiente de aprobación física.

## maria02.apk

- Fecha: 2026-07-30
- versionCode: 3
- versionName: maria02
- Objetivo: hacer visible la previsualización mientras CameraX graba VIDEO.
- Causa: la implementación de superficie predeterminada de `PreviewView` no se
  componía correctamente dentro de la interfaz Compose del dispositivo Vivo.
- Solución: `PreviewView.ImplementationMode.COMPATIBLE` y escala
  `FILL_CENTER`; grabación, MP4, Room y sincronización permanecen sin cambios.
- Pruebas automatizadas: 153 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`.
- SHA-256:
  `9AABB97E13E8D05EC534A70911E9DD66095418CA502BCEE12E7C82993F97277E`.
- Validación física: pendiente.
- Commit: pendiente.

## maria04.apk

- Fecha: 2026-07-30
- versionCode: 5
- versionName: maria04
- Objetivo: permitir al administrador alternar entre cámara delantera y
  trasera durante el enrolamiento facial y enrolar varias personas sin salir
  del módulo.
- Alcance: interfaz y enlace de CameraX del enrolamiento; el reconocimiento
  facial conserva la cámara y el comportamiento anteriores.
- Pruebas automatizadas: 154 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`; copia adicional en
  `/sdcard/Download/BitacoraVersiones/maria04.apk`.
- SHA-256:
  `EF1F3A07F417C2F44CCCC3D5B2DCD168BBBB44840A58040E17D6373010617AE5`.
- Validación física: pendiente.
- Commit: pendiente.

## maria03.apk

- Fecha: 2026-07-30
- versionCode: 4
- versionName: maria03
- Objetivo: corregir la vista previa negra durante la grabación interna de
  evidencias de VIDEO.
- Causa: `PreviewView` usaba peso flexible dentro de un contenedor desplazable
  sin altura vertical acotada, por lo que podía recibir una altura útil nula.
- Solución: asignar a la superficie de cámara una altura explícita de 420 dp;
  captura, MP4, Room, sincronización y demás evidencias permanecen sin cambios.
- Pruebas automatizadas: 153 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`; copia adicional en
  `/sdcard/Download/BitacoraVersiones/maria03.apk`.
- SHA-256:
  `E9D54EAFB56041728E271ADC7490950B4507C77191DCE974D344943CFAEAFA95`.
- Validación física: pendiente.
- Commit: pendiente.

## maria05.apk

- Fecha: 2026-07-30
- versionCode: 6
- versionName: maria05
- Objetivo: Iteración 1 del seguimiento satelital de embalses.
- Alcance: botón principal, pantalla independiente, catálogo dinámico de seis
  embalses, selección de fechas, metadatos, imagen demostrativa de La Copa y
  botón Regresar.
- Base estable: commit `8807c61`, etiqueta `maria04-stable`.
- Pruebas automatizadas: 84 FastAPI aprobadas (2 omitidas) y 156 pruebas
  Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`; copia adicional en
  `/sdcard/Download/BitacoraVersiones/maria05.apk`.
- SHA-256:
  `307F514508B4480FCCE54A1C0105145D45598DADC7956DB11D31A601A8F221DA`.
- Validación física: pendiente.
- Commit: pendiente.

## maria06.apk

- Fecha: 2026-07-30
- versionCode: 7
- versionName: maria06
- Objetivo: presentar el seguimiento satelital como pantalla exclusiva y
  permitir ampliar manualmente la imagen.
- Alcance: oculta temporalmente las opciones generales durante el seguimiento;
  las restaura al regresar. Añade pellizco, desplazamiento, zoom 1×–5× y
  controles `−`, `+` y `Restablecer`.
- Pruebas automatizadas: 156 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`; copia adicional en
  `/sdcard/Download/BitacoraVersiones/maria06.apk`.
- SHA-256:
  `C64D33ED91A7AA0D780BFB769DDA51C9C73801A1D7C6E19BEC7509C4F3E87D62`.
- Validación física: pendiente.
- Commit: pendiente.

## maria07.apk

- Fecha: 2026-07-30
- versionCode: 8
- versionName: maria07
- Objetivo: convertir Seguimiento satelital de embalses en un tercer ambiente,
  junto a Administrador y Ciudadano.
- Alcance: el ambiente satelital abre directamente su pantalla exclusiva y al
  regresar vuelve a la selección de ambientes. Administrador y Ciudadano
  conservan sus menús sin el botón satelital interno.
- Pruebas automatizadas: 156 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`; copia adicional en
  `/sdcard/Download/BitacoraVersiones/maria07.apk`.
- SHA-256:
  `CA7F7239CF2D997565AD6960E4A013D5E813AD2B9DEE10C5E7D8B79CBCA69945`.
- Validación física: pendiente.
- Commit: pendiente.

## maria08.apk

- Fecha: 2026-07-31
- versionCode: 9
- versionName: maria08
- Objetivo: rediseño visual controlado sobre la base estable `maria07`.
- Alcance: tema Material 3 verde, encabezado con ambiente y estado de conexión,
  tarjetas grandes en dos columnas, barra inferior y pantalla Administración.
- Punto de restauración: commit `cf0ea9c`, etiqueta `maria07-stable`.
- Protección: sin cambios en Room, SQLite, API, repositorios, sincronización,
  evidencias, GPS, QR o biometría.
- Pruebas automatizadas: pendiente de recuento final.
- Instalación ADB: pendiente.
- Validación física: pendiente.
- Commit definitivo: pendiente.

## maria09.apk

- Fecha: 2026-07-31
- versionCode: 10
- versionName: maria09
- Objetivo: corregir el ajuste visual detectado en `maria08` con tamaño de
  fuente aumentado.
- Alcance: conservar las cinco etiquetas de la barra inferior en una línea.
- Pruebas automatizadas: 156 pruebas Android aprobadas, 0 fallos.
- Instalación ADB: `Success` mediante `adb install -r`; copia adicional en
  `/sdcard/Download/BitacoraVersiones/maria09.apk`.
- SHA-256:
  `3497B9696A019690985FD9367986304F9D56D686CF90FCD68912F46C1CDABCC3`.
- Validación física: pendiente.
- Commit definitivo: pendiente.
