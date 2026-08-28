package com.local.mangareader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.local.mangareader.data.Manga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    manga: List<Manga>,
    isScanning: Boolean,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManga: (Manga) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моя бібліотека") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Налаштування")
                    }
                    TextButton(onClick = onPickFolder) { Text("Обрати папку") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isScanning -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                manga.isEmpty() -> EmptyLibraryHint(onPickFolder, Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(manga) { m ->
                        MangaCard(m, onClick = { onOpenManga(m) })
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        DeveloperCredit()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperCredit() {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Розробник: reveriesgrace",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun EmptyLibraryHint(onPickFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Бібліотека порожня.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Обери папку, куди ти скидаєш манґу кабелем " +
                "(та, що містить папки з назвами тайтлів).",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPickFolder) { Text("Обрати папку") }
    }
}

@Composable
private fun MangaCard(manga: Manga, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Card(Modifier.aspectRatio(0.7f)) {
            AsyncImage(
                model = manga.coverPage,
                contentDescription = manga.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            manga.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
