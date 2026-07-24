package com.cactus.bitacora.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceTemplatePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "face-template-persistence-test.db"

    @Before
    fun resetBefore() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun resetAfter() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun enrollmentSurvivesDatabaseCloseAndReopen() {
        runBlocking {
            val encrypted = byteArrayOf(10, 20, 30, 40)
            val uuid = UUID.randomUUID().toString()
            val database = openDatabase()
            try {
                database.faceTemplateDao().upsert(
                    FaceTemplateEntity(
                        participantId = 14,
                        localSyncUuid = uuid,
                        participantCode = "P0014",
                        displayName = "Participante 14",
                        encryptedEmbedding = encrypted,
                        enrolledAtMillis = 1234L,
                        modelVersion = "FaceNet-160/128",
                        active = true,
                        centralSyncState = "PENDIENTE_CREAR"
                    )
                )
            } finally {
                database.close()
            }

            val reopened = openDatabase()
            try {
                val saved = reopened.faceTemplateDao().getByParticipantId(14)
                assertNotNull(saved)
                assertEquals(uuid, saved!!.localSyncUuid)
                assertArrayEquals(encrypted, saved.encryptedEmbedding)
                assertEquals("PENDIENTE_CREAR", saved.centralSyncState)
            } finally {
                reopened.close()
            }
        }
    }

    private fun openDatabase(): BitacoraDatabase =
        Room.databaseBuilder(context, BitacoraDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
}
