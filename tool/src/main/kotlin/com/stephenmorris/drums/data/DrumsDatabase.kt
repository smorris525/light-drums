package com.stephenmorris.drums.data

import androidx.room.Database
import androidx.room.RoomDatabase

/** The tool's local database: patterns only. */
@Database(entities = [PatternEntity::class], version = 1, exportSchema = false)
abstract class DrumsDatabase : RoomDatabase() {
    abstract fun patterns(): PatternDao
}
