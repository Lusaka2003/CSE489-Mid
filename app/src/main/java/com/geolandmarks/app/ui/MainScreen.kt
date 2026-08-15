package com.geolandmarks.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolandmarks.app.ui.screens.ActivityScreen
import com.geolandmarks.app.ui.screens.AddScreen
import com.geolandmarks.app.ui.screens.LandmarkDetailSheet
import com.geolandmarks.app.ui.screens.LandmarksScreen
import com.geolandmarks.app.ui.screens.MapScreen

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: LandmarkViewModel) {
    val tabs = listOf(
        Tab("Map", Icons.Outlined.Map),
        Tab("Landmarks", Icons.Outlined.List),
        Tab("Activity", Icons.Outlined.History),
        Tab("Add/View", Icons.Outlined.AddLocationAlt)
    )
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()
    val selected by vm.selectedLandmark.collectAsStateWithLifecycle()
    var errorText by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(apiKey.isBlank()) }
    var keyDraft by remember { mutableStateOf(apiKey) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.refreshOnlineFlag()
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) keyDraft = apiKey
        if (apiKey.isBlank()) showSettings = true
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Toast -> snackbar.showSnackbar(event.text)
                is UiEvent.Error -> errorText = event.text
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (online) tabs[selectedTab].label else "${tabs[selectedTab].label} · offline") },
                actions = {
                    if (!online) {
                        Icon(Icons.Outlined.WifiOff, contentDescription = "Offline")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        keyDraft = apiKey
                        showSettings = true
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "API key")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (selectedTab) {
                0 -> MapScreen(vm)
                1 -> LandmarksScreen(vm)
                2 -> ActivityScreen(vm)
                else -> AddScreen(vm)
            }
        }
    }

    selected?.let { item ->
        LandmarkDetailSheet(
            item = item,
            onDismiss = { vm.selectedLandmark.value = null },
            onVisit = { vm.visit(item) },
            onDelete = {
                vm.deleteLandmark(item.id)
                vm.selectedLandmark.value = null
            }
        )
    }

    errorText?.let { message ->
        AlertDialog(
            onDismissRequest = { errorText = null },
            title = { Text("Something went wrong") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorText = null }) { Text("OK") }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { if (apiKey.isNotBlank()) showSettings = false },
            title = { Text("API key") },
            text = {
                Column {
                    Text("Paste the unique key issued for this semester. It is sent as ?key= on every request.")
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        label = { Text("key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveApiKey(keyDraft)
                    showSettings = false
                }) { Text("Save") }
            },
            dismissButton = {
                if (apiKey.isNotBlank()) {
                    TextButton(onClick = { showSettings = false }) { Text("Cancel") }
                }
            }
        )
    }
}
