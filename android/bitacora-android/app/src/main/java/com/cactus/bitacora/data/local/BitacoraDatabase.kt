package com.cactus.bitacora.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BitacoraLocalEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BitacoraDatabase : RoomDatabase() {
    abstract fun bitacoraDao(): BitacoraDao

    companion object {
        @Volatile
        private var instance: BitacoraDatabase? = null

        fun getInstance(context: Context): BitacoraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BitacoraDatabase::class.java,
                    "bitacora_local.db"
                ).build().also { instance = it }
            }
    }
}
