package com.geolandmarks.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LandmarkEntity::class,
        VisitEntity::class,
        PendingVisitEntity::class,
        VisitJobEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun landmarkDao(): LandmarkDao
    abstract fun visitDao(): VisitDao
    abstract fun pendingVisitDao(): PendingVisitDao
    abstract fun visitJobDao(): VisitJobDao
}
