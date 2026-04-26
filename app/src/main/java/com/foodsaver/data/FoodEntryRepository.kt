package com.foodsaver.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foodsaver.model.FoodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

object FoodEntryRepository {
    private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "foodsaver_prefs")

    private val keyEntries = stringPreferencesKey("entries")

    fun entriesFlow(context: Context): Flow<List<FoodEntry>> =
        context.dataStore.data.map { preferences ->
            val entriesJson = preferences[keyEntries] ?: return@map emptyList()
            parseJson(entriesJson)
        }

    suspend fun save(
        context: Context,
        entries: List<FoodEntry>,
    ) {
        context.dataStore.edit { preferences ->
            preferences[keyEntries] = toJson(entries)
        }
    }

    private fun toJson(entries: List<FoodEntry>): String =
        JSONArray()
            .apply {
                entries.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("name", entry.name)
                            put("expiry", entry.expiry.toString())
                            put("id", entry.id)
                        },
                    )
                }
            }.toString()

    private fun parseJson(json: String): List<FoodEntry> =
        try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                val id = if (obj.has("id")) obj.getString("id") else UUID.randomUUID().toString()
                FoodEntry(
                    obj.getString("name"),
                    LocalDate.parse(obj.getString("expiry")),
                    id,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
}
