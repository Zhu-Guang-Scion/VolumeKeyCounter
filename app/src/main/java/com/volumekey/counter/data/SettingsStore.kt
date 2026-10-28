package com.volumekey.counter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val activeCounterKey = longPreferencesKey("active_counter_id")
    private val keepAliveHintShownKey = booleanPreferencesKey("keepalive_hint_shown")

    val activeCounterId: Flow<Long?> = context.settingsDataStore.data.map { it[activeCounterKey] }

    val keepAliveHintShown: Flow<Boolean> = context.settingsDataStore.data.map { it[keepAliveHintShownKey] ?: false }

    suspend fun getActiveCounterId(): Long? = activeCounterId.first()

    suspend fun setActiveCounterId(id: Long) {
        context.settingsDataStore.edit { it[activeCounterKey] = id }
    }

    suspend fun setKeepAliveHintShown() {
        context.settingsDataStore.edit { it[keepAliveHintShownKey] = true }
    }
}
