package com.cactus.bitacora.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogMigrationJvmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "catalog-migration-jvm-test.db"

    @Before
    fun resetBefore() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun resetAfter() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesFaceTemplates() {
        createVersion8().use {
            it.writableDatabase.execSQL(
                "INSERT INTO face_templates(participantId, marker) VALUES (2, 'face-kept')"
            )
        }
        migrateToVersion9().use {
            assertEquals(
                "face-kept",
                it.readableDatabase.query(
                    "SELECT marker FROM face_templates WHERE participantId=2"
                ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
            )
            assertCatalogTablesExist(it.readableDatabase)
        }
    }

    @Test
    fun migrationPreservesBitacorasEvidencesAndPendingStates() {
        createVersion8().use {
            it.writableDatabase.execSQL(
                "INSERT INTO bitacoras_locales(localId, marker, syncStatus) " +
                    "VALUES (20, 'log-kept', 'PENDIENTE_CREAR')"
            )
            it.writableDatabase.execSQL(
                "INSERT INTO bitacora_evidences(localId, marker, syncStatus) " +
                    "VALUES (30, 'file-kept', 'ERROR')"
            )
        }
        migrateToVersion9().use {
            assertEquals(
                "PENDIENTE_CREAR",
                value(
                    it.readableDatabase,
                    "SELECT syncStatus FROM bitacoras_locales WHERE localId=20"
                )
            )
            assertEquals(
                "ERROR",
                value(
                    it.readableDatabase,
                    "SELECT syncStatus FROM bitacora_evidences WHERE localId=30"
                )
            )
            assertCatalogTablesExist(it.readableDatabase)
        }
    }

    @Test
    fun migrationNineToTenPreservesEvidenceAndBackfillsServerRelation() {
        createVersion9ForEvidence().use {
            it.writableDatabase.execSQL(
                "INSERT INTO bitacoras_locales(localId, backendId) VALUES (7, 68)"
            )
            it.writableDatabase.execSQL(
                "INSERT INTO bitacora_evidences(localId, bitacoraLocalId, clientUuid) " +
                    "VALUES (30, 7, 'legacy-evidence')"
            )
        }
        migrateToVersion10().use {
            assertEquals(
                "68",
                value(
                    it.readableDatabase,
                    "SELECT bitacoraServerId FROM bitacora_evidences WHERE localId=30"
                )
            )
            assertEquals(
                "legacy-evidence",
                value(
                    it.readableDatabase,
                    "SELECT clientUuid FROM bitacora_evidences WHERE localId=30"
                )
            )
        }
    }

    @Test
    fun migrationTenToElevenRepairsOnlySynchronizedInactiveTemplates() {
        createVersion10ForFaces().use {
            it.writableDatabase.execSQL(
                "INSERT INTO face_templates VALUES " +
                    "(2, 'P0002', X'0102', 0, 'SINCRONIZADO')," +
                    "(3, 'P0003', X'0304', 0, 'PENDIENTE_ELIMINAR')"
            )
        }
        migrateToVersion11().use {
            assertEquals(
                "1",
                value(it.readableDatabase, "SELECT active FROM face_templates WHERE participantId=2")
            )
            assertEquals(
                "0",
                value(it.readableDatabase, "SELECT active FROM face_templates WHERE participantId=3")
            )
            assertEquals(
                "P0002",
                value(
                    it.readableDatabase,
                    "SELECT participantCode FROM face_templates WHERE participantId=2"
                )
            )
        }
    }

    private fun createVersion8(): SupportSQLiteOpenHelper =
        helper(8, object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE bitacoras_locales(" +
                        "localId INTEGER PRIMARY KEY, marker TEXT, syncStatus TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE bitacora_evidences(" +
                        "localId INTEGER PRIMARY KEY, marker TEXT, syncStatus TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE face_templates(participantId INTEGER PRIMARY KEY, marker TEXT)"
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        })

    private fun migrateToVersion9(): SupportSQLiteOpenHelper =
        helper(9, object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                BitacoraDatabase.MIGRATION_8_9.migrate(db)
            }
        })

    private fun createVersion9ForEvidence(): SupportSQLiteOpenHelper =
        helper(9, object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE bitacoras_locales(" +
                        "localId INTEGER PRIMARY KEY, backendId INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE bitacora_evidences(" +
                        "localId INTEGER PRIMARY KEY, bitacoraLocalId INTEGER NOT NULL, " +
                        "clientUuid TEXT NOT NULL)"
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        })

    private fun migrateToVersion10(): SupportSQLiteOpenHelper =
        helper(10, object : SupportSQLiteOpenHelper.Callback(10) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                BitacoraDatabase.MIGRATION_9_10.migrate(db)
            }
        })

    private fun createVersion10ForFaces(): SupportSQLiteOpenHelper =
        helper(10, object : SupportSQLiteOpenHelper.Callback(10) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE face_templates(" +
                        "participantId INTEGER PRIMARY KEY, participantCode TEXT NOT NULL, " +
                        "encryptedEmbedding BLOB NOT NULL, active INTEGER NOT NULL, " +
                        "centralSyncState TEXT NOT NULL)"
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        })

    private fun migrateToVersion11(): SupportSQLiteOpenHelper =
        helper(11, object : SupportSQLiteOpenHelper.Callback(11) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                BitacoraDatabase.MIGRATION_10_11.migrate(db)
            }
        })

    private fun helper(
        version: Int,
        callback: SupportSQLiteOpenHelper.Callback
    ): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        ).also { it.writableDatabase }

    private fun value(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

    private fun assertCatalogTablesExist(db: SupportSQLiteDatabase) {
        val expected = setOf(
            "participantes_locales",
            "areas_administrativas_locales",
            "empleado_area_locales",
            "catalog_sync_state"
        )
        val actual = db.query(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertEquals(expected, expected.intersect(actual))
    }
}
