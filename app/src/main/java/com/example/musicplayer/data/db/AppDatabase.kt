package com.example.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.musicplayer.data.model.Song

/**
 * Base de datos Room de la aplicación.
 *
 * Singleton: se obtiene una única instancia compartida a través de [getInstance].
 */
@Database(
    entities = [Song::class],
    version = 2,
    exportSchema = false, // Desactivado para mantener el scaffold simple; en producción se exporta el esquema.
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        private const val DATABASE_NAME = "music_player.db"

        /**
         * Migración 1 -> 2: añade la columna `is_favorite` a la tabla `songs`
         * (las canciones existentes se consideran no favoritas).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Devuelve la instancia única de la base de datos, creándola bajo demanda.
         * El `synchronized` garantiza que solo se construya una vez aunque varios
         * hilos la soliciten a la vez.
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}