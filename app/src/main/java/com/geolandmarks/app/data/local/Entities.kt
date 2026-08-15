package com.geolandmarks.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "landmarks")
data class LandmarkEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val lat: Double,
    val lon: Double,
    val image: String?,
    val score: Double,
    val visitCount: Int,
    val avgDistance: Double,
    val isActive: Boolean,
    val cachedAt: Long
)

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val landmarkId: Int,
    val landmarkTitle: String,
    val visitedAt: Long,
    val distance: Double?,
    val jobId: Long?,
    val status: String
)

@Entity(tableName = "pending_visits")
data class PendingVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val landmarkId: Int,
    val landmarkTitle: String,
    val userLat: Double,
    val userLon: Double,
    val createdAt: Long,
    val attempts: Int = 0
)

@Entity(tableName = "visit_jobs")
data class VisitJobEntity(
    @PrimaryKey val jobId: Long,
    val landmarkId: Int,
    val landmarkTitle: String,
    val createdAt: Long,
    val status: String,
    val distance: Double? = null
)
