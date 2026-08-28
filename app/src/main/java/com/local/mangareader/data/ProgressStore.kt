package com.local.mangareader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "manga_progress")

/**
 * Tracks, per manga, which volume/chapter/page/offset the user last read.
 * Key format: "<mangaName>" -> "volumeName|chapterNumber|pageIndex|scrollOffset"
 */
class ProgressStore(private val context: Context) {

    private fun keyFor(mangaName: String) = stringPreferencesKey("progress_$mangaName")
    private val themeKey = stringPreferencesKey("app_theme")

    suspend fun saveProgress(mangaName: String, volumeName: String, chapterNumber: String, pageIndex: Int, offset: Int = 0) {
        android.util.Log.d("ProgressStore", "Saving: $mangaName -> $volumeName | $chapterNumber | Page $pageIndex | Offset $offset")
        context.dataStore.edit { prefs ->
            prefs[keyFor(mangaName)] = "$volumeName|$chapterNumber|$pageIndex|$offset"
        }
    }

    /** Returns Triple(Volume, Chapter, Page) + Offset */
    fun progressFlow(mangaName: String): Flow<Pair<Triple<String, String, Int>, Int>?> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[keyFor(mangaName)] ?: return@map null
            val parts = raw.split("|")
            if (parts.size < 3) return@map null
            
            val triple = Triple(parts[0], parts[1], parts[2].toIntOrNull() ?: 0)
            val offset = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
            triple to offset
        }

    suspend fun saveRootUri(uri: String) {
        context.dataStore.edit { it[stringPreferencesKey("root_uri")] = uri }
    }

    fun rootUriFlow(): Flow<String?> =
        context.dataStore.data.map { it[stringPreferencesKey("root_uri")] }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[themeKey] = theme }
    }

    /** "system", "light", "dark" */
    fun themeFlow(): Flow<String> =
        context.dataStore.data.map { it[themeKey] ?: "system" }
}
