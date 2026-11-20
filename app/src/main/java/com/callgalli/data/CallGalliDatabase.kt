package com.callgalli.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RuleEntity::class, CallSessionEntity::class, DailyUsageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CallGalliDatabase : RoomDatabase() {
    abstract fun rules(): RuleDao
    abstract fun usage(): DailyUsageDao
    abstract fun sessions(): CallSessionDao

    companion object {
        fun create(context: Context): CallGalliDatabase =
            Room.databaseBuilder(context, CallGalliDatabase::class.java, "callgalli.db").build()
    }
}
