package com.geolandmarks.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lon: Double)

class LocationTracker(private val context: Context) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val last = runCatching { fused.lastLocation.awaitOrNull() }.getOrNull()
        if (last != null && last.latitude != 0.0 && last.longitude != 0.0) {
            return GeoPoint(last.latitude, last.longitude)
        }
        val fresh = withTimeoutOrNull(8_000) { requestFresh(fused) }
        if (fresh != null) return GeoPoint(fresh.latitude, fresh.longitude)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fallback = manager.getProviders(true).firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        return fallback?.let { GeoPoint(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFresh(
        fused: com.google.android.gms.location.FusedLocationProviderClient
    ): Location? = suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fused.removeLocationUpdates(this)
                if (cont.isActive) cont.resume(result.lastLocation)
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        cont.invokeOnCancellation { fused.removeLocationUpdates(callback) }
    }
}

@SuppressLint("MissingPermission")
private suspend fun com.google.android.gms.tasks.Task<Location>.awaitOrNull(): Location? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }
