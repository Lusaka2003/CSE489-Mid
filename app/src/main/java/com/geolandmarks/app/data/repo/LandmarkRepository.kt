package com.geolandmarks.app.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.geolandmarks.app.LandmarkApplication
import com.geolandmarks.app.data.local.AppDatabase
import com.geolandmarks.app.data.local.LandmarkEntity
import com.geolandmarks.app.data.local.PendingVisitEntity
import com.geolandmarks.app.data.local.VisitEntity
import com.geolandmarks.app.data.local.VisitJobEntity
import com.geolandmarks.app.data.prefs.ApiKeyStore
import com.geolandmarks.app.data.remote.LandmarkApi
import com.geolandmarks.app.data.remote.VisitRequest
import com.geolandmarks.app.data.remote.asLandmarkList
import com.geolandmarks.app.data.remote.errorMessage
import com.geolandmarks.app.data.remote.toJobStatusOrNull
import com.geolandmarks.app.work.LandmarkSyncWorker
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import com.google.gson.JsonElement
import java.util.concurrent.TimeUnit

sealed class RepoResult<out T> {
    data class Ok<T>(val data: T, val message: String? = null) : RepoResult<T>()
    data class Err(val message: String, val dialog: Boolean = true) : RepoResult<Nothing>()
}

class LandmarkRepository(
    private val context: Context,
    private val api: LandmarkApi,
    private val db: AppDatabase,
    private val apiKeyStore: ApiKeyStore
) {
    private val landmarks = db.landmarkDao()
    private val visits = db.visitDao()
    private val pending = db.pendingVisitDao()
    private val jobs = db.visitJobDao()

    fun observeLandmarks(): Flow<List<LandmarkEntity>> = landmarks.observeActive()
    fun observeDeleted(): Flow<List<LandmarkEntity>> = landmarks.observeDeleted()
    fun observeVisits(): Flow<List<VisitEntity>> = visits.observeAll()
    fun observeQueued(): Flow<List<PendingVisitEntity>> = pending.observeAll()
    fun observePendingJobs(): Flow<List<VisitJobEntity>> = jobs.observePending()
    fun apiKeyFlow(): Flow<String> = apiKeyStore.keyFlow

    suspend fun saveApiKey(value: String) = apiKeyStore.save(value)

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun refreshLandmarks(): RepoResult<Int> {
        val key = apiKeyStore.current()
        if (key.isBlank()) return RepoResult.Err("Set your API key before fetching landmarks.")
        if (!isOnline()) return RepoResult.Err("You are offline. Showing cached landmarks.", dialog = false)
        return try {
            val response = api.getLandmarks(key = key)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return RepoResult.Err(httpError(response), dialog = true)
            }
            val err = body.errorMessage()
            if (err != null && body.asLandmarkList().isEmpty()) {
                return RepoResult.Err(err)
            }
            val list = body.asLandmarkList()
            val now = System.currentTimeMillis()
            val remoteIds = list.map { it.id }.toSet()
            landmarks.upsertAll(list.map { dto ->
                LandmarkEntity(
                    id = dto.id,
                    title = dto.title,
                    lat = dto.lat,
                    lon = dto.lon,
                    image = dto.imageUrl(),
                    score = dto.score,
                    visitCount = dto.visitCount,
                    avgDistance = dto.avgDistance,
                    isActive = dto.isActive,
                    cachedAt = now
                )
            })
            // Landmarks that vanished from the API were likely soft-deleted server-side.
            landmarks.allIds().filter { it !in remoteIds }.forEach { id ->
                val existing = landmarks.getById(id)
                if (existing?.isActive == true) {
                    landmarks.setActive(id, false)
                }
            }
            RepoResult.Ok(list.size, "Landmarks updated.")
        } catch (e: Exception) {
            RepoResult.Err("Could not reach the server: ${e.localizedMessage ?: "network error"}")
        }
    }

    suspend fun visitLandmark(landmarkId: Int, lat: Double, lon: Double): RepoResult<String> {
        val key = apiKeyStore.current()
        if (key.isBlank()) return RepoResult.Err("Set your API key before visiting a landmark.")
        val landmark = landmarks.getById(landmarkId)
        val title = landmark?.title ?: "Landmark #$landmarkId"
        if (!isOnline()) {
            pending.insert(
                PendingVisitEntity(
                    landmarkId = landmarkId,
                    landmarkTitle = title,
                    userLat = lat,
                    userLon = lon,
                    createdAt = System.currentTimeMillis()
                )
            )
            enqueueSync(expedited = false)
            return RepoResult.Ok(
                "queued",
                "You are offline. Visit queued and will sync automatically."
            )
        }
        return submitVisit(key, landmarkId, title, lat, lon, queueOnFailure = true)
    }

    private suspend fun submitVisit(
        key: String,
        landmarkId: Int,
        title: String,
        lat: Double,
        lon: Double,
        queueOnFailure: Boolean
    ): RepoResult<String> {
        return try {
            val response = api.visitLandmark(
                key = key,
                body = VisitRequest(landmark_id = landmarkId, user_lat = lat, user_lon = lon)
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return RepoResult.Err(httpError(response))
            }
            val err = body.errorMessage()
            val job = body.toJobStatusOrNull()
            if (job == null) {
                return RepoResult.Err(err ?: "Visit was accepted but no job_id was returned.")
            }
            jobs.upsert(
                VisitJobEntity(
                    jobId = job.jobId,
                    landmarkId = landmarkId,
                    landmarkTitle = title,
                    createdAt = System.currentTimeMillis(),
                    status = "pending"
                )
            )
            visits.insert(
                VisitEntity(
                    landmarkId = landmarkId,
                    landmarkTitle = title,
                    visitedAt = System.currentTimeMillis(),
                    distance = null,
                    jobId = job.jobId,
                    status = "pending"
                )
            )
            enqueueSync(expedited = true)
            RepoResult.Ok("pending", "Visit submitted. Waiting for distance in the background…")
        } catch (e: Exception) {
            if (queueOnFailure) {
                pending.insert(
                    PendingVisitEntity(
                        landmarkId = landmarkId,
                        landmarkTitle = title,
                        userLat = lat,
                        userLon = lon,
                        createdAt = System.currentTimeMillis()
                    )
                )
                enqueueSync(expedited = false)
                RepoResult.Ok(
                    "queued",
                    "Network dropped. Visit queued for later: ${e.localizedMessage ?: "offline"}"
                )
            } else {
                RepoResult.Err(e.localizedMessage ?: "network error", dialog = false)
            }
        }
    }

    suspend fun createLandmark(
        title: String,
        lat: Double,
        lon: Double,
        imageBytes: ByteArray?,
        imageMime: String?,
        fileName: String?
    ): RepoResult<Unit> {
        val key = apiKeyStore.current()
        if (key.isBlank()) return RepoResult.Err("Set your API key before adding a landmark.")
        if (!isOnline()) return RepoResult.Err("Adding a landmark requires an internet connection.")
        val textType = "text/plain".toMediaType()
        val imagePart = if (imageBytes != null && imageBytes.isNotEmpty()) {
            val mime = (imageMime ?: "image/jpeg").toMediaType()
            val body = imageBytes.toRequestBody(mime)
            MultipartBody.Part.createFormData("image", fileName ?: "photo.jpg", body)
        } else null
        return try {
            val response = api.createLandmark(
                key = key,
                title = title.toRequestBody(textType),
                lat = lat.toString().toRequestBody(textType),
                lon = lon.toString().toRequestBody(textType),
                image = imagePart
            )
            if (!response.isSuccessful) return RepoResult.Err(httpError(response))
            val err = response.body()?.errorMessage()
            if (err != null && response.code() >= 400) return RepoResult.Err(err)
            refreshLandmarks()
            RepoResult.Ok(Unit, "Landmark created.")
        } catch (e: Exception) {
            RepoResult.Err("Create failed: ${e.localizedMessage ?: "network error"}")
        }
    }

    suspend fun deleteLandmark(id: Int): RepoResult<Unit> {
        val key = apiKeyStore.current()
        if (key.isBlank()) return RepoResult.Err("Set your API key first.")
        if (!isOnline()) return RepoResult.Err("Deleting requires an internet connection.")
        return try {
            val response = api.deleteLandmark(key = key, landmarkId = id, id = id)
            if (!response.isSuccessful) return RepoResult.Err(httpError(response))
            landmarks.setActive(id, false)
            refreshLandmarks()
            RepoResult.Ok(Unit, "Landmark deleted.")
        } catch (e: Exception) {
            RepoResult.Err("Delete failed: ${e.localizedMessage ?: "network error"}")
        }
    }

    suspend fun restoreLandmark(id: Int): RepoResult<Unit> {
        val key = apiKeyStore.current()
        if (key.isBlank()) return RepoResult.Err("Set your API key first.")
        if (!isOnline()) return RepoResult.Err("Restore requires an internet connection.")
        return try {
            val response = api.restoreLandmark(key = key, landmarkId = id, id = id)
            if (!response.isSuccessful) return RepoResult.Err(httpError(response))
            landmarks.setActive(id, true)
            refreshLandmarks()
            RepoResult.Ok(Unit, "Landmark restored.")
        } catch (e: Exception) {
            RepoResult.Err("Restore failed: ${e.localizedMessage ?: "network error"}")
        }
    }

    /**
     * Called from WorkManager. Drains the offline visit queue, then polls every
     * pending job_id until the server marks it done.
     */
    suspend fun runBackgroundSync(): Boolean {
        val key = apiKeyStore.current()
        if (key.isBlank() || !isOnline()) return false
        var stillPending = false

        for (item in pending.getAll()) {
            val result = submitVisit(
                key,
                item.landmarkId,
                item.landmarkTitle,
                item.userLat,
                item.userLon,
                queueOnFailure = false
            )
            when (result) {
                is RepoResult.Ok -> pending.delete(item.id)
                is RepoResult.Err -> {
                    pending.bumpAttempts(item.id)
                    if (item.attempts + 1 >= 8) {
                        pending.delete(item.id)
                        visits.insert(
                            VisitEntity(
                                landmarkId = item.landmarkId,
                                landmarkTitle = item.landmarkTitle,
                                visitedAt = item.createdAt,
                                distance = null,
                                jobId = null,
                                status = "failed"
                            )
                        )
                    } else {
                        stillPending = true
                    }
                }
            }
        }

        for (job in jobs.pending()) {
            try {
                val response = api.getJobStatus(key = key, jobId = job.jobId)
                if (response.code() == 404) {
                    jobs.update(job.jobId, "failed", null)
                    visits.updateByJob(job.jobId, null, "failed")
                    notifyVisit("${job.landmarkTitle}: job not found")
                    continue
                }
                if (!response.isSuccessful) {
                    stillPending = true
                    continue
                }
                val status = response.body()?.toJobStatusOrNull()
                if (status == null || status.isPending) {
                    stillPending = true
                    continue
                }
                if (status.isDone) {
                    jobs.update(job.jobId, "done", status.distance)
                    visits.updateByJob(job.jobId, status.distance, "done")
                    val dist = status.distance?.let { String.format("%.2f m", it) } ?: "n/a"
                    notifyVisit("Visited ${job.landmarkTitle}: $dist")
                } else {
                    jobs.update(job.jobId, status.status, status.distance)
                    visits.updateByJob(job.jobId, status.distance, status.status)
                }
            } catch (_: Exception) {
                stillPending = true
            }
        }
        return !stillPending && pending.getAll().isEmpty()
    }

    fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<LandmarkSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        enqueueSync(expedited = false)
    }

    fun enqueueSync(expedited: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val builder = OneTimeWorkRequestBuilder<LandmarkSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        if (expedited) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK,
            if (expedited) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            builder.build()
        )
    }

    private fun httpError(response: Response<JsonElement>): String {
        val parsed = try {
            response.errorBody()?.string()?.let {
                com.google.gson.JsonParser.parseString(it).errorMessage()
            }
        } catch (_: Exception) {
            null
        }
        return parsed
            ?: response.body()?.errorMessage()
            ?: "Request failed (HTTP ${response.code()})."
    }

    private fun notifyVisit(text: String) {
        val notification = NotificationCompat.Builder(context, LandmarkApplication.VISIT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Landmark visit")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(text.hashCode(), notification)
        }
    }

    companion object {
        const val PERIODIC_WORK = "landmark-periodic-sync"
        const val ONE_SHOT_WORK = "landmark-oneshot-sync"
    }
}
