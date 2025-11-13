package com.soundman.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SoundLabel::class, PersonLabel::class, SoundDetection::class],
    version = 3,
    exportSchema = false
)
abstract class SoundDatabase : RoomDatabase() {
    abstract fun soundLabelDao(): SoundLabelDao
    abstract fun personLabelDao(): PersonLabelDao
    abstract fun soundDetectionDao(): SoundDetectionDao

    companion object {
        @Volatile
        private var INSTANCE: SoundDatabase? = null

        fun getDatabase(context: Context): SoundDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoundDatabase::class.java,
                    "sound_database"
                )
                .fallbackToDestructiveMigration() // For development - will clear data on schema change
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
