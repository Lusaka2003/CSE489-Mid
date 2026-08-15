package com.geolandmarks.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.geolandmarks.app.R
import com.geolandmarks.app.data.local.LandmarkEntity
import com.geolandmarks.app.ui.LandmarkViewModel
import com.geolandmarks.app.ui.scoreColorInt
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val BANGLADESH = GeoPoint(23.6850, 90.3563)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: LandmarkViewModel) {
    val landmarks by vm.landmarks.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var permissionAsked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (!vm.hasLocationPermission() && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(7.0)
                    controller.setCenter(BANGLADESH)
                    mapView = this
                    onResume()
                }
            },
            update = { map ->
                map.overlays.removeAll { it is Marker }
                val min = landmarks.minOfOrNull { it.score } ?: 0.0
                val max = landmarks.maxOfOrNull { it.score } ?: 0.0
                landmarks.forEach { item ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(item.lat, item.lon)
                    marker.title = item.title
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.icon = tintedPin(context, scoreColorInt(item.score, min, max))
                    marker.setOnMarkerClickListener { _, _ ->
                        vm.selectedLandmark.value = item
                        true
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            }
        )

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

private fun tintedPin(context: android.content.Context, color: Int): BitmapDrawable {
    val src = ContextCompat.getDrawable(context, R.drawable.ic_map_pin)!!.mutate()
    DrawableCompat.setTint(src, color)
    val w = src.intrinsicWidth.coerceAtLeast(48)
    val h = src.intrinsicHeight.coerceAtLeast(64)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    src.setBounds(0, 0, w, h)
    src.draw(canvas)
    return BitmapDrawable(context.resources, bmp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandmarkDetailSheet(
    item: LandmarkEntity,
    onDismiss: () -> Unit,
    onVisit: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            if (!item.image.isNullOrBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Text("Score: ${"%.2f".format(item.score)}")
            Text("Visits: ${item.visitCount}")
            Text("Avg distance: ${"%.2f".format(item.avgDistance)} m")
            Text("Location: ${"%.5f".format(item.lat)}, ${"%.5f".format(item.lon)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onVisit) { Text("Visit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
