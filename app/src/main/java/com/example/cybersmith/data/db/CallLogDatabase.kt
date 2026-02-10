package com.example.cybersmith.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cybersmith.data.model.CallLogRecord

@Database(entities = [CallLogRecord::class], version = 2, exportSchema = false)
abstract class CallLogDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile
        private var INSTANCE: CallLogDatabase? = null

        fun getDatabase(context: Context): CallLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallLogDatabase::class.java,
                    "cybersmith_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
