package com.cactus.bitacora.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BitacoraLocalEntity::class,
        BitacoraEvidenceEntity::class,
        FaceTemplateEntity::class,
        ParticipanteLocalEntity::class,
        AreaAdministrativaLocalEntity::class,
        EmpleadoAreaLocalEntity::class,
        CatalogSyncStateEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class BitacoraDatabase : RoomDatabase() {
    abstract fun bitacoraDao(): BitacoraDao
    abstract fun evidenceDao(): BitacoraEvidenceDao
    abstract fun faceTemplateDao(): FaceTemplateDao
    abstract fun referenceCatalogDao(): ReferenceCatalogDao

    companion object {
        @Volatile
        private var instance: BitacoraDatabase? = null

        fun getInstance(context: Context): BitacoraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BitacoraDatabase::class.java,
                    "bitacora_local.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                ).build().also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bitacoras_locales ADD COLUMN qrArea TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bitacora_evidences (
                        localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId INTEGER,
                        bitacoraLocalId INTEGER NOT NULL,
                        clientUuid TEXT NOT NULL,
                        evidenceType TEXT NOT NULL,
                        textContent TEXT,
                        localFilePath TEXT,
                        mimeType TEXT,
                        fileSize INTEGER,
                        durationSeconds INTEGER,
                        createdAt INTEGER NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        accuracy REAL,
                        altitude REAL,
                        gpsTimestamp INTEGER,
                        locationProvider TEXT,
                        gpsStatus TEXT NOT NULL,
                        syncStatus TEXT NOT NULL,
                        syncAttempts INTEGER NOT NULL,
                        lastSyncError TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bitacora_evidences_bitacoraLocalId ON bitacora_evidences(bitacoraLocalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bitacora_evidences_syncStatus ON bitacora_evidences(syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bitacora_evidences_createdAt ON bitacora_evidences(createdAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bitacora_evidences_clientUuid ON bitacora_evidences(clientUuid)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "openLatitude REAL", "openLongitude REAL", "openAccuracy REAL",
                    "openAltitude REAL", "openGpsTimestamp INTEGER", "openLocationProvider TEXT",
                    "closeLatitude REAL", "closeLongitude REAL", "closeAccuracy REAL",
                    "closeAltitude REAL", "closeGpsTimestamp INTEGER", "closeLocationProvider TEXT"
                ).forEach { definition ->
                    db.execSQL("ALTER TABLE bitacoras_locales ADD COLUMN $definition")
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bitacora_evidences ADD COLUMN areaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bitacora_evidences ADD COLUMN originalName TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS face_templates (
                        participantId INTEGER NOT NULL PRIMARY KEY,
                        participantCode TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        encryptedEmbedding BLOB NOT NULL,
                        enrolledAtMillis INTEGER NOT NULL,
                        modelVersion TEXT NOT NULL,
                        active INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_face_templates_participantCode " +
                        "ON face_templates(participantCode)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_face_templates_active " +
                        "ON face_templates(active)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE face_templates ADD COLUMN remoteTemplateId INTEGER")
                db.execSQL("ALTER TABLE face_templates ADD COLUMN embeddingSha256 TEXT")
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN encryptionVersion TEXT " +
                        "NOT NULL DEFAULT 'local-keystore-aesgcm-v1'"
                )
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN centralSyncState TEXT " +
                        "NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL("ALTER TABLE face_templates ADD COLUMN lastSyncError TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE bitacoras_locales ADD COLUMN syncAttempts " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN syncAttempts " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE bitacoras_locales SET syncStatus = 'PENDIENTE_CREAR' " +
                        "WHERE syncStatus IN ('LOCAL', 'PENDIENTE', 'SYNCING', 'PENDING_GPS')"
                )
                db.execSQL(
                    "UPDATE bitacora_evidences SET syncStatus = 'PENDIENTE_CREAR' " +
                        "WHERE syncStatus IN ('LOCAL', 'PENDIENTE', 'SYNCING', 'PENDING_GPS')"
                )
                db.execSQL(
                    "UPDATE face_templates SET centralSyncState = 'PENDIENTE_CREAR' " +
                        "WHERE centralSyncState IN ('PENDING', 'ERROR')"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN localSyncUuid " +
                        "TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("ALTER TABLE bitacora_evidences ADD COLUMN fileHash TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS participantes_locales (
                        idParticipante INTEGER NOT NULL PRIMARY KEY,
                        codigoQr TEXT NOT NULL,
                        nombre TEXT,
                        apellido TEXT,
                        documento TEXT,
                        activo INTEGER NOT NULL DEFAULT 1,
                        updatedAtServer TEXT,
                        syncedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_participantes_locales_codigoQr " +
                        "ON participantes_locales(codigoQr)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_participantes_locales_activo " +
                        "ON participantes_locales(activo)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS areas_administrativas_locales (
                        idArea INTEGER NOT NULL PRIMARY KEY,
                        codigoQr TEXT NOT NULL,
                        nombreArea TEXT NOT NULL,
                        activo INTEGER NOT NULL DEFAULT 1,
                        updatedAtServer TEXT,
                        syncedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_areas_administrativas_locales_codigoQr " +
                        "ON areas_administrativas_locales(codigoQr)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_areas_administrativas_locales_activo " +
                        "ON areas_administrativas_locales(activo)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS empleado_area_locales (
                        idParticipante INTEGER NOT NULL,
                        idArea INTEGER NOT NULL,
                        cargo INTEGER,
                        fechaFinal TEXT,
                        activo INTEGER NOT NULL DEFAULT 1,
                        updatedAtServer TEXT,
                        syncedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(idParticipante, idArea)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_empleado_area_locales_idParticipante " +
                        "ON empleado_area_locales(idParticipante)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_empleado_area_locales_idArea " +
                        "ON empleado_area_locales(idArea)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_empleado_area_locales_activo " +
                        "ON empleado_area_locales(activo)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catalog_sync_state (
                        catalogKey TEXT NOT NULL PRIMARY KEY,
                        lastSuccessfulSyncMillis INTEGER,
                        participantCount INTEGER NOT NULL DEFAULT 0,
                        areaCount INTEGER NOT NULL DEFAULT 0,
                        assignmentCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE bitacora_evidences ADD COLUMN bitacoraServerId INTEGER"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_bitacora_evidences_bitacoraServerId " +
                        "ON bitacora_evidences(bitacoraServerId)"
                )
                db.execSQL(
                    """
                    UPDATE bitacora_evidences
                    SET bitacoraServerId = (
                        SELECT backendId FROM bitacoras_locales b
                        WHERE b.localId = bitacora_evidences.bitacoraLocalId
                    )
                    WHERE bitacoraServerId IS NULL
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE face_templates
                    SET active = 1
                    WHERE active = 0
                      AND centralSyncState = 'SINCRONIZADO'
                      AND length(encryptedEmbedding) > 0
                    """.trimIndent()
                )
            }
        }
    }
}
