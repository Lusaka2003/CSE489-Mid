package com.geolandmarks.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.geolandmarks.app.data.local.LandmarkEntity
import com.geolandmarks.app.ui.LandmarkViewModel
import com.geolandmarks.app.ui.SortMode
import com.geolandmarks.app.ui.scoreColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandmarksScreen(vm: LandmarkViewModel) {
    val all by vm.landmarks.collectAsStateWithLifecycle()
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val filterOn by vm.minScoreEnabled.collectAsStateWithLifecycle()
    val minScore by vm.minScore.collectAsStateWithLifecycle()
    val visible = vm.visibleLandmarks()
    val rangeMin = all.minOfOrNull { it.score } ?: 0.0
    val rangeMax = all.maxOfOrNull { it.score } ?: 0.0
    var sortExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(expanded = sortExpanded, onExpandedChange = { sortExpanded = it }) {
                OutlinedTextField(
                    value = when (sort) {
                        SortMode.SCORE_DESC -> "Sort: score high → low"
                        SortMode.SCORE_ASC -> "Sort: score low → high"
                        SortMode.TITLE -> "Sort: title"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    DropdownMenuItem(text = { Text("Score high → low") }, onClick = {
                        vm.sortMode.value = SortMode.SCORE_DESC
                        sortExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Score low → high") }, onClick = {
                        vm.sortMode.value = SortMode.SCORE_ASC
                        sortExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Title") }, onClick = {
                        vm.sortMode.value = SortMode.TITLE
                        sortExpanded = false
                    })
                }
            }
            FilterChip(
                selected = filterOn,
                onClick = {
                    vm.minScoreEnabled.value = !filterOn
                    if (!filterOn) vm.minScore.value = rangeMin.toFloat()
                },
                label = { Text("Filter by minimum score") }
            )
            if (filterOn && all.isNotEmpty()) {
                Text("Minimum score: ${"%.1f".format(minScore)}")
                Slider(
                    value = minScore.coerceIn(rangeMin.toFloat(), rangeMax.toFloat().coerceAtLeast(rangeMin.toFloat() + 0.01f)),
                    onValueChange = { vm.minScore.value = it },
                    valueRange = rangeMin.toFloat()..rangeMax.toFloat().coerceAtLeast(rangeMin.toFloat() + 0.01f)
                )
            }
            Text("${visible.size} landmarks", style = MaterialTheme.typography.bodySmall)
        }

        if (visible.isEmpty()) {
            BoxEmpty("No landmarks to show. Pull data from the Map tab refresh, or loosen the filter.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visible, key = { it.id }) { item ->
                    LandmarkRow(
                        item = item,
                        min = rangeMin,
                        max = rangeMax,
                        onClick = { vm.selectedLandmark.value = item }
                    )
                }
            }
        }
    }
}

@Composable
fun LandmarkRow(
    item: LandmarkEntity,
    min: Double,
    max: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.image.isNullOrBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Score ${"%.2f".format(item.score)}") },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        disabledContainerColor = scoreColor(item.score, min, max).copy(alpha = 0.18f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
fun BoxEmpty(text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
