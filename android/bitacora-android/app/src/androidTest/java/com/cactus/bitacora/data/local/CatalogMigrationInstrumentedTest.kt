package com.cactus.bitacora.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMigrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "catalog-migration-test.db"

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
    fun migrationPreservesBitacorasAndEvidences() {
        createVersion8().use {
            it.writableDatabase.execSQL(
                "INSERT INTO bitacoras_locales(localId, marker) VALUES (20, 'log-kept')"
            )
            it.writableDatabase.execSQL(
                "INSERT INTO bitacora_evidences(localId, marker) VALUES (30, 'file-kept')"
            )
        }
        migrateToVersion9().use {
            assertEquals(
                1,
                it.readableDatabase.query(
                    "SELECT COUNT(*) FROM bitacoras_locales WHERE marker='log-kept'"
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            )
            assertEquals(
                1,
                it.readableDatabase.query(
                    "SELECT COUNT(*) FROM bitacora_evidences WHERE marker='file-kept'"
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            )
            assertCatalogTablesExist(it.readableDatabase)
        }
    }

    private fun createVersion8(): SupportSQLiteOpenHelper =
        helper(8, object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE bitacoras_locales(localId INTEGER PRIMARY KEY, marker TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE bitacora_evidences(localId INTEGER PRIMARY KEY, marker TEXT)"
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
