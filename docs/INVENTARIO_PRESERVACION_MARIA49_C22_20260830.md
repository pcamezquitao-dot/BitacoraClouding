# Inventario de preservación local Maria49/C22

Esta rama conserva una copia de seguridad técnica del estado local encontrado el
2026-08-30 en `C:\Bitacora\BitacoraClouding-c20-isolated`.

La preservación parte del commit
`fb31eff40d995c6f121f4d56d9aea080146ca1e8` y no afirma que el commit resultante
reproduzca exactamente la APK instalada `maria49-c22-bitacora-prueba-dev`.

## Código preservado

- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/SupervisorModeScreen.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/ReferenceCatalogRepository.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/RemoteBitacoraRepository.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/local/BitacoraDao.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/local/BitacoraDatabase.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/local/ReferenceCatalogDao.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/local/SupervisorEventLocalEntity.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/data/local/WorkScheduleLocal.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/feature/supervisorevents/SupervisorEventContract.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/feature/supervisorevents/SupervisorEventForm.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/feature/supervisorevents/SupervisorEventRepository.kt`
- `android/bitacora-android/app/src/main/java/com/cactus/bitacora/sync/OfflineSyncWorker.kt`

## Pruebas y documentación preservadas

- `android/bitacora-android/app/src/test/java/com/cactus/bitacora/data/ReferenceCatalogRepositoryTest.kt`
- `android/bitacora-android/app/src/test/java/com/cactus/bitacora/data/RemoteBitacoraRepositoryTest.kt`
- `android/bitacora-android/app/src/test/java/com/cactus/bitacora/feature/supervisorevents/SupervisorEventRepositoryTest.kt`
- `android/bitacora-android/app/src/test/java/com/cactus/bitacora/feature/supervisorevents/SupervisorEventValidationTest.kt`
- `docs/INVENTARIO_PRESERVACION_MARIA49_C22_20260830.md`

## Artefactos excluidos y conservados físicamente

No se movieron, eliminaron, limpiaron ni sobrescribieron los siguientes archivos:

- `room_db_inspection/BitacoraDatabase.kt`
- `room_db_inspection/BitacoraDatabase.rescue.kt`
- `room_db_inspection/ReferenceCatalogDao.kt`
- `room_db_inspection/ReferenceCatalogDao.rescue.kt`
- `room_db_inspection/SupervisorEventLocalEntity.kt`
- `room_db_inspection/SupervisorEventLocalEntity.rescue.kt`
- `room_db_inspection/WorkScheduleLocal.kt`
- `room_db_inspection/WorkScheduleLocal.rescue.kt`
- `room_db_inspection/inspect_room_copy.py`

También quedan excluidos por política cualquier APK, resultado de compilación,
registro, video, captura, respaldo, salida o directorio `.codex-*` que exista o se
genere localmente.
