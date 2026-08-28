package com.local.mangareader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mangareader.data.Chapter
import com.local.mangareader.data.Manga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    manga: Manga,
    lastRead: Triple<String, String, Int>?,
    onBack: () -> Unit,
    onOpenChapter: (volumeName: String, chapter: Chapter, startPage: Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(manga.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (lastRead != null) {
                item {
                    val (_, chapNum, pageIdx) = lastRead
                    Button(
                        onClick = {
                            val (_, chapNum, pageIdx) = lastRead
                            val chapter = manga.volumes.find { it.name == lastRead.first }
                                ?.chapters?.find { it.number == chapNum }
                            if (chapter != null) {
                                onOpenChapter(lastRead.first, chapter, pageIdx)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text("Продовжити читання (Рзд. $chapNum, стор. ${pageIdx + 1})")
                    }
                }
            }

            manga.volumes.forEach { volume ->
                item {
                    Text(
                        volume.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
                    )
                }
                items(volume.chapters) { chapter ->
                    val isLast = lastRead?.first == volume.name && lastRead.second == chapter.number
                    ListItem(
                        headlineContent = { Text("Розділ ${chapter.number}") },
                        supportingContent = {
                            Text(
                                if (isLast) "Сторінка ${lastRead!!.third + 1} з ${chapter.pages.size} · продовжити"
                                else "${chapter.pages.size} стор."
                            )
                        },
                        modifier = Modifier.clickable { onOpenChapter(volume.name, chapter, -1) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
