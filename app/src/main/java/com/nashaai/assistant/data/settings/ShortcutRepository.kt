package com.nashaai.assistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Shortcut(
    val packageName: String,
    val label: String
)

private val Context.shortcutsDataStore by preferencesDataStore(name = "nasha_shortcuts")

/**
 * Persists user-selected app shortcuts separately from general settings.
 * Invalid package names and blank labels are rejected; adding an existing
 * package updates its label instead of creating a duplicate.
 */
class ShortcutRepository(context: Context) {
    private val dataStore = context.applicationContext.shortcutsDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val shortcutsKey = stringPreferencesKey("shortcuts_json")

    val shortcuts: Flow<List<Shortcut>> = dataStore.data.map { preferences ->
        decode(preferences[shortcutsKey])
    }

    suspend fun add(packageName: String, label: String): Boolean {
        val normalizedPackage = packageName.trim()
        val normalizedLabel = label.trim().take(MAX_LABEL_LENGTH)
        if (!isValidPackageName(normalizedPackage) || normalizedLabel.isBlank()) {
            return false
        }

        dataStore.edit { preferences ->
            val current = decode(preferences[shortcutsKey])
            val updated = current.filterNot {
                it.packageName == normalizedPackage
            } + Shortcut(normalizedPackage, normalizedLabel)
            preferences[shortcutsKey] = json.encodeToString(updated)
        }
        return true
    }

    suspend fun remove(packageName: String): Boolean {
        val normalizedPackage = packageName.trim()
        if (!isValidPackageName(normalizedPackage)) return false

        var removed = false
        dataStore.edit { preferences ->
            val current = decode(preferences[shortcutsKey])
            val updated = current.filterNot {
                it.packageName == normalizedPackage
            }
            removed = updated.size != current.size
            if (removed) {
                preferences[shortcutsKey] = json.encodeToString(updated)
            }
        }
        return removed
    }

    private fun decode(raw: String?): List<Shortcut> {
        if (raw.isNullOrBlank()) return emptyList()
        val decoded = try {
            json.decodeFromString<List<Shortcut>>(raw)
        } catch (error: Exception) {
            throw IllegalStateException("Saved app shortcuts could not be read", error)
        }
        return decoded
            .filter { isValidPackageName(it.packageName) && it.label.isNotBlank() }
            .distinctBy(Shortcut::packageName)
    }

    private fun isValidPackageName(value: String): Boolean =
        value.length <= MAX_PACKAGE_LENGTH && PACKAGE_NAME.matches(value)

    private companion object {
        const val MAX_LABEL_LENGTH = 120
        const val MAX_PACKAGE_LENGTH = 255
        val PACKAGE_NAME =
            Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    }
}
