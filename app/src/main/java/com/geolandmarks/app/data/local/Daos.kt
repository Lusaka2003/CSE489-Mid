package com.geolandmarks.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LandmarkDao {
    @Query("SELECT * FROM landmarks WHERE isActive = 1 ORDER BY score DESC")
    fun observeActive(): Flow<List<LandmarkEntity>>

    @Query("SELECT * FROM landmarks WHERE isActive = 0 ORDER BY title")
    fun observeDeleted(): Flow<List<LandmarkEntity>>

    @Query("SELECT * FROM landmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): LandmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LandmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LandmarkEntity)

    @Query("UPDATE landmarks SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Int, active: Boolean)

    @Query("SELECT id FROM landmarks")
    suspend fun allIds(): List<Int>
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Insert
    suspend fun insert(visit: VisitEntity): Long

    @Query("UPDATE visits SET distance = :distance, status = :status WHERE jobId = :jobId")
    suspend fun updateByJob(jobId: Long, distance: Double?, status: String)

    @Query("SELECT * FROM visits WHERE jobId = :jobId LIMIT 1")
    suspend fun getByJob(jobId: Long): VisitEntity?
}

@Dao
interface PendingVisitDao {
    @Query("SELECT * FROM pending_visits ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingVisitEntity>

    @Query("SELECT * FROM pending_visits ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingVisitEntity>>

    @Insert
    suspend fun insert(item: PendingVisitEntity): Long

    @Query("DELETE FROM pending_visits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_visits SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)
}

@Dao
interface VisitJobDao {
    @Query("SELECT * FROM visit_jobs WHERE status = 'pending'")
    suspend fun pending(): List<VisitJobEntity>

    @Query("SELECT * FROM visit_jobs WHERE status = 'pending'")
    fun observePending(): Flow<List<VisitJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: VisitJobEntity)

    @Query("UPDATE visit_jobs SET status = :status, distance = :distance WHERE jobId = :jobId")
    suspend fun update(jobId: Long, status: String, distance: Double?)

    @Query("DELETE FROM visit_jobs WHERE jobId = :jobId")
    suspend fun delete(jobId: Long)
}
