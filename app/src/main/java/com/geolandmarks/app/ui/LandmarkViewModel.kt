package com.geolandmarks.app.ui

import android.app.Application
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.geolandmarks.app.LandmarkApplication
import com.geolandmarks.app.data.local.LandmarkEntity
import com.geolandmarks.app.data.local.PendingVisitEntity
import com.geolandmarks.app.data.local.VisitEntity
import com.geolandmarks.app.data.local.VisitJobEntity
import com.geolandmarks.app.data.location.GeoPoint
import com.geolandmarks.app.data.location.LocationTracker
import com.geolandmarks.app.data.repo.LandmarkRepository
import com.geolandmarks.app.data.repo.RepoResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiEvent {
    data class Toast(val text: String) : UiEvent()
    data class Error(val text: String) : UiEvent()
}

enum class SortMode { SCORE_DESC, SCORE_ASC, TITLE }

class LandmarkViewModel(
    application: Application,
    private val repo: LandmarkRepository,
    private val locationTracker: LocationTracker
) : AndroidViewModel(application) {

    val landmarks: StateFlow<List<LandmarkEntity>> = repo.observeLandmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deleted: StateFlow<List<LandmarkEntity>> = repo.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val visits: StateFlow<List<VisitEntity>> = repo.observeVisits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val queued: StateFlow<List<PendingVisitEntity>> = repo.observeQueued()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingJobs: StateFlow<List<VisitJobEntity>> = repo.observePendingJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val apiKey: StateFlow<String> = repo.apiKeyFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _online = MutableStateFlow(repo.isOnline())
    val online = _online.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    var sortMode = MutableStateFlow(SortMode.SCORE_DESC)
    var minScoreEnabled = MutableStateFlow(false)
    var minScore = MutableStateFlow(0f)
    var selectedLandmark = MutableStateFlow<LandmarkEntity?>(null)

    init {
        refresh(showToast = false)
    }

    fun refreshOnlineFlag() {
        _online.value = repo.isOnline()
    }

    fun refresh(showToast: Boolean = true) {
        viewModelScope.launch {
            _busy.value = true
            refreshOnlineFlag()
            when (val result = repo.refreshLandmarks()) {
                is RepoResult.Ok -> if (showToast) emit(UiEvent.Toast(result.message ?: "Updated."))
                is RepoResult.Err -> {
                    if (result.dialog) emit(UiEvent.Error(result.message))
                    else if (showToast) emit(UiEvent.Toast(result.message))
                }
            }
            _busy.value = false
        }
    }

    fun saveApiKey(value: String) {
        viewModelScope.launch {
            repo.saveApiKey(value)
            emit(UiEvent.Toast("API key saved."))
            refresh()
        }
    }

    fun hasLocationPermission(): Boolean = locationTracker.hasPermission()

    suspend fun currentLocation(): GeoPoint? {
        val point = locationTracker.current()
        if (point == null) {
            emit(UiEvent.Error("Could not read GPS. Grant location permission and turn on location."))
        }
        return point
    }

    fun showError(message: String) {
        viewModelScope.launch { emit(UiEvent.Error(message)) }
    }

    fun visit(landmark: LandmarkEntity) {
        viewModelScope.launch {
            if (!locationTracker.hasPermission()) {
                emit(UiEvent.Error("Location permission is required to visit a landmark."))
                return@launch
            }
            _busy.value = true
            val point = locationTracker.current()
            if (point == null) {
                _busy.value = false
                emit(UiEvent.Error("Could not read GPS. Turn on location and try again."))
                return@launch
            }
            when (val result = repo.visitLandmark(landmark.id, point.lat, point.lon)) {
                is RepoResult.Ok -> emit(UiEvent.Toast(result.message ?: "Visit sent."))
                is RepoResult.Err -> emit(UiEvent.Error(result.message))
            }
            _busy.value = false
        }
    }

    fun createLandmark(title: String, lat: Double, lon: Double, image: ByteArray?, mime: String?, name: String?) {
        viewModelScope.launch {
            if (title.isBlank()) {
                emit(UiEvent.Error("Title is required."))
                return@launch
            }
            _busy.value = true
            when (val result = repo.createLandmark(title.trim(), lat, lon, image, mime, name)) {
                is RepoResult.Ok -> emit(UiEvent.Toast(result.message ?: "Created."))
                is RepoResult.Err -> emit(UiEvent.Error(result.message))
            }
            _busy.value = false
        }
    }

    fun deleteLandmark(id: Int) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = repo.deleteLandmark(id)) {
                is RepoResult.Ok -> emit(UiEvent.Toast(result.message ?: "Deleted."))
                is RepoResult.Err -> emit(UiEvent.Error(result.message))
            }
            _busy.value = false
        }
    }

    fun restoreLandmark(id: Int) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = repo.restoreLandmark(id)) {
                is RepoResult.Ok -> emit(UiEvent.Toast(result.message ?: "Restored."))
                is RepoResult.Err -> emit(UiEvent.Error(result.message))
            }
            _busy.value = false
        }
    }

    fun visibleLandmarks(): List<LandmarkEntity> {
        val min = if (minScoreEnabled.value) minScore.value.toDouble() else Double.NEGATIVE_INFINITY
        val filtered = landmarks.value.filter { it.score >= min }
        return when (sortMode.value) {
            SortMode.SCORE_DESC -> filtered.sortedByDescending { it.score }
            SortMode.SCORE_ASC -> filtered.sortedBy { it.score }
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    private suspend fun emit(event: UiEvent) = _events.emit(event)

    companion object {
        fun factory(app: LandmarkApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LandmarkViewModel(
                        app,
                        app.container.repository,
                        app.container.locationTracker
                    ) as T
                }
            }
    }
}

fun scoreColor(score: Double, min: Double, max: Double): Color {
    val t = if (max <= min) 0.5 else ((score - min) / (max - min)).coerceIn(0.0, 1.0)
    val android = when {
        t < 0.5 -> {
            val u = (t / 0.5).toFloat()
            lerpColor(AndroidColor.rgb(198, 40, 40), AndroidColor.rgb(245, 158, 11), u)
        }
        else -> {
            val u = ((t - 0.5) / 0.5).toFloat()
            lerpColor(AndroidColor.rgb(245, 158, 11), AndroidColor.rgb(22, 163, 74), u)
        }
    }
    return Color(android)
}

fun scoreColorInt(score: Double, min: Double, max: Double): Int {
    val t = if (max <= min) 0.5 else ((score - min) / (max - min)).coerceIn(0.0, 1.0)
    return when {
        t < 0.5 -> lerpColor(AndroidColor.rgb(198, 40, 40), AndroidColor.rgb(245, 158, 11), (t / 0.5).toFloat())
        else -> lerpColor(AndroidColor.rgb(245, 158, 11), AndroidColor.rgb(22, 163, 74), ((t - 0.5) / 0.5).toFloat())
    }
}

private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val cr = AndroidColor.red(a) + ((AndroidColor.red(b) - AndroidColor.red(a)) * t).toInt()
    val cg = AndroidColor.green(a) + ((AndroidColor.green(b) - AndroidColor.green(a)) * t).toInt()
    val cb = AndroidColor.blue(a) + ((AndroidColor.blue(b) - AndroidColor.blue(a)) * t).toInt()
    return AndroidColor.rgb(cr, cg, cb)
}
