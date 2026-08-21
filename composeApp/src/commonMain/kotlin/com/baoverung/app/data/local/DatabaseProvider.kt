package com.baoverung.app.data.local

import androidx.room.RoomDatabase

expect class DatabaseBuilder {
    fun createBuilder(): RoomDatabase.Builder<AppDatabase>
}
