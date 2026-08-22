package com.baoverung.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual class DatabaseBuilder {
    actual fun createBuilder(): RoomDatabase.Builder<AppDatabase> {
        val fileManager = NSFileManager.defaultManager
        val documentDirectory = fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        val dbFilePath = documentDirectory?.path + "/baoverung.db"
        
        return Room.databaseBuilder<AppDatabase>(
            name = dbFilePath ?: "baoverung.db",
            factory = { AppDatabaseConstructor.initialize() }
        )
    }
}
