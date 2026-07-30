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
