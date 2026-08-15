package com.geolandmarks.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geolandmarks.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.apiKeyDataStore by preferencesDataStore(name = "api_key_store")

class ApiKeyStore(private val context: Context) {
    private val keyPref = stringPreferencesKey("api_key")

    val keyFlow: Flow<String> = context.apiKeyDataStore.data.map { prefs ->
        prefs[keyPref]?.trim().orEmpty().ifEmpty { BuildConfig.LANDMARK_API_KEY.trim() }
    }

    suspend fun current(): String = keyFlow.first()

    suspend fun save(value: String) {
        context.apiKeyDataStore.edit { it[keyPref] = value.trim() }
    }

    suspend fun hasKey(): Boolean = current().isNotBlank()
}
