package com.baoverung.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual class DatabaseBuilder {
    actual fun createBuilder(): RoomDatabase.Builder<AppDatabase> {
        val dbFilePath = NSHomeDirectory() + "/Documents/baoverung.db"
        return Room.databaseBuilder<AppDatabase>(
            name = dbFilePath,
            factory = { AppDatabaseConstructor.initialize().create() }
        ).setDriver(BundledSQLiteDriver())
         .setQueryCoroutineContext(Dispatchers.IO)
    }
}
