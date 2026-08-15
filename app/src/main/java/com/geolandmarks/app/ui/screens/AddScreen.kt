package com.geolandmarks.app.ui.screens

import android.Manifest
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.geolandmarks.app.ui.LandmarkViewModel
import kotlinx.coroutines.launch

@Composable
fun AddScreen(vm: LandmarkViewModel) {
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var showCamera by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageUri = uri
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showCamera = true }

    if (showCamera) {
        CameraCaptureScreen(
            onCaptured = { uri ->
                imageUri = uri
                showCamera = false
            },
            onCancel = { showCamera = false }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add landmark", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = latText,
            onValueChange = { latText = it },
            label = { Text("Latitude") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = lonText,
            onValueChange = { lonText = it },
            label = { Text("Longitude") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                scope.launch {
                    val point = vm.currentLocation()
                    if (point != null) {
                        latText = point.lat.toString()
                        lonText = point.lon.toString()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Use current GPS location") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Photo picker") }
            OutlinedButton(onClick = {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }) { Text("Camera") }
        }

        imageUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Selected image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Button(
            onClick = {
                val lat = latText.toDoubleOrNull()
                val lon = lonText.toDoubleOrNull()
                if (lat == null || lon == null) {
                    vm.showError("Latitude and longitude must be valid numbers.")
                    return@Button
                }
                val bytes = imageUri?.let { readBytes(context.contentResolver, it) }
                val mime = imageUri?.let { context.contentResolver.getType(it) }
                val name = imageUri?.lastPathSegment
                vm.createLandmark(title, lat, lon, bytes, mime, name)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create landmark") }

        if (deleted.isNotEmpty()) {
            Text("Soft-deleted (restore)", style = MaterialTheme.typography.titleMedium)
            deleted.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text("id ${item.id}")
                        }
                        TextButton(onClick = { vm.restoreLandmark(item.id) }) { Text("Restore") }
                    }
                }
            }
        }
    }
}

private fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray? {
    return runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
}
