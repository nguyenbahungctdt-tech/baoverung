package com.baoverung.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseBuilder(private val context: Context) {
    actual fun createBuilder(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = context.getDatabasePath("baoverung.db")
        return Room.databaseBuilder<AppDatabase>(
            context = context,
            name = dbFile.absolutePath
        )
    }
}
