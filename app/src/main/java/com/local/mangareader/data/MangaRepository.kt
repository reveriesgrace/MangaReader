package com.local.mangareader.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Expected folder layout (selected root is the parent of all manga folders):
 *
 * <root>/
 *   Manga Name/
 *     Том 1/
 *       1 - 001.webp
 *       1 - 002.webp
 *       2 - 001.webp
 *     Том 2/
 *       3 - 001.webp
 *       ...
 *
 * File name convention: "<chapter> - <page>.<ext>"
 */

data class Page(val uri: Uri, val name: String)

data class Chapter(val number: String, val pages: List<Page>)

data class Volume(val name: String, val chapters: List<Chapter>)

data class Manga(val name: String, val folderUri: Uri, val volumes: List<Volume>) {
    val coverPage: Uri?
        get() = volumes.firstOrNull()?.chapters?.firstOrNull()?.pages?.firstOrNull()?.uri
}

private val IMAGE_EXT = setOf("webp", "jpg", "jpeg", "png", "bmp", "gif", "heic", "heif", "avif")

/** Extracts the numeric-ish sort key from a name like "12 - 003" -> chapter "12", page "003". */
private fun parseFileName(nameNoExt: String): Pair<String, String> {
    val parts = nameNoExt.split(" - ", limit = 2)
    return if (parts.size == 2) parts[0].trim() to parts[1].trim() else nameNoExt to "0"
}

/** Natural sort so "2" < "10", not lexicographic "10" < "2". */
private fun naturalKey(s: String): Double = s.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0

class MangaRepository(private val context: Context) {

    suspend fun scanLibrary(rootUri: Uri): List<Manga> = withContext(Dispatchers.IO) {
        try {
            Log.d("MangaRepository", "Scanning library at: $rootUri")
            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
            val allFiles = root.listFiles()
            Log.d("MangaRepository", "Found ${allFiles.size} items in root")
            
            val mangaFolders = allFiles.filter { it.isDirectory }

            mangaFolders.mapNotNull { mangaDir ->
                try {
                    val volumes = scanManga(mangaDir)
                    if (volumes.isEmpty()) {
                        Log.d("MangaRepository", "Skipping empty manga folder: ${mangaDir.name}")
                        null
                    } else {
                        Manga(name = mangaDir.name ?: "Unknown", folderUri = mangaDir.uri, volumes = volumes)
                    }
                } catch (e: Exception) {
                    Log.e("MangaRepository", "Error scanning manga folder ${mangaDir.name}", e)
                    null
                }
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e("MangaRepository", "Critical error scanning library", e)
            emptyList()
        }
    }

    private fun scanManga(mangaDir: DocumentFile): List<Volume> {
        val allFiles = mangaDir.listFiles()
        val volumeFolders = allFiles.filter { it.isDirectory }

        // If there are no subfolders, treat the manga folder itself as a single volume.
        val effectiveVolumeDirs = volumeFolders.ifEmpty { listOf(mangaDir) }

        return effectiveVolumeDirs
            .sortedBy { naturalKey(it.name ?: "") }
            .mapNotNull { volDir ->
                val chapters = scanVolume(volDir)
                if (chapters.isEmpty()) null else Volume(name = volDir.name ?: "Volume", chapters = chapters)
            }
    }

    private fun scanVolume(volDir: DocumentFile): List<Chapter> {
        val allFiles = volDir.listFiles()
        val files = allFiles.filter { it.isFile && (it.name?.substringAfterLast('.', "")?.lowercase() in IMAGE_EXT) }

        val byChapter = LinkedHashMap<String, MutableList<Page>>()
        
        // Strategy: if most files contain " - ", they probably follow "Chapter - Page" convention.
        // Otherwise, treat the entire folder as a single chapter.
        val filesWithSeparator = files.count { it.name?.contains(" - ") == true }
        val useSeparator = files.isNotEmpty() && filesWithSeparator > files.size / 2

        for (f in files) {
            val name = f.name ?: continue
            val nameNoExt = name.substringBeforeLast('.')
            
            val chapterKey = if (useSeparator) {
                parseFileName(nameNoExt).first
            } else {
                volDir.name ?: "1"
            }
            
            byChapter.getOrPut(chapterKey) { mutableListOf() }.add(Page(uri = f.uri, name = name))
        }

        return byChapter.entries
            .sortedBy { naturalKey(it.key) }
            .map { (chapterKey, pages) ->
                Chapter(
                    number = chapterKey,
                    pages = pages.sortedBy { 
                        if (useSeparator) naturalKey(parseFileName(it.name.substringBeforeLast('.')).second)
                        else naturalKey(it.name.substringBeforeLast('.'))
                    },
                )
            }
    }
}
