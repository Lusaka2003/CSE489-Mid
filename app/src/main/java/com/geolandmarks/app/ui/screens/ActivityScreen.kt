package com.geolandmarks.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolandmarks.app.ui.LandmarkViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(vm: LandmarkViewModel) {
    val visits by vm.visits.collectAsStateWithLifecycle()
    val queued by vm.queued.collectAsStateWithLifecycle()
    val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    if (visits.isEmpty() && queued.isEmpty()) {
        BoxEmpty("No visits yet. Open a landmark and tap Visit.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (queued.isNotEmpty()) {
            item { Text("Queued (offline)", style = MaterialTheme.typography.titleMedium) }
            items(queued, key = { "q-${it.id}" }) { item ->
                HistoryCard(
                    title = item.landmarkTitle,
                    time = fmt.format(Date(item.createdAt)),
                    distance = "Waiting for network",
                    status = "queued"
                )
            }
        }
        item { Text("Visit history", style = MaterialTheme.typography.titleMedium) }
        items(visits, key = { "v-${it.id}" }) { item ->
            val distance = when (item.status) {
                "done" -> item.distance?.let { "${"%.2f".format(it)} m" } ?: "done"
                "pending" -> "Distance pending"
                "failed" -> "Failed"
                else -> item.status
            }
            HistoryCard(
                title = item.landmarkTitle,
                time = fmt.format(Date(item.visitedAt)),
                distance = distance,
                status = item.status
            )
        }
    }
}

@Composable
private fun HistoryCard(title: String, time: String, distance: String, status: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(time, style = MaterialTheme.typography.bodyMedium)
            Text("Distance: $distance")
            Text("Status: $status", style = MaterialTheme.typography.bodySmall)
        }
    }
}
