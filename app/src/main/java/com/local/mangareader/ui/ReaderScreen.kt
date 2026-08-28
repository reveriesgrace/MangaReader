package com.local.mangareader.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.local.mangareader.data.Page
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class ReaderMode {
    VERTICAL, HORIZONTAL, SELECTOR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    title: String,
    pages: List<Page>,
    startPageIndex: Int, // -1 means "show page selector"
    startPageOffset: Int = 0,
    onBack: () -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onNextChapter: (() -> Unit)? = null
) {
    var readerMode by remember { 
        mutableStateOf(if (startPageIndex >= 0) ReaderMode.VERTICAL else ReaderMode.SELECTOR) 
    }
    var selectedPage by remember { mutableIntStateOf(if (startPageIndex >= 0) startPageIndex else 0) }
    
    val pagerState = rememberPagerState(initialPage = selectedPage.coerceIn(0, pages.size - 1)) { 
        if (onNextChapter != null) pages.size + 1 else pages.size 
    }
    
    val verticalListState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = selectedPage.coerceIn(0, pages.size - 1),
        initialFirstVisibleItemScrollOffset = startPageOffset
    )
    
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage, readerMode) {
        if (pagerState.currentPage < pages.size && readerMode == ReaderMode.HORIZONTAL) {
            selectedPage = pagerState.currentPage
            onPageChanged(pagerState.currentPage, 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val pageText = if (readerMode == ReaderMode.SELECTOR) "Вибір сторінки" else "${selectedPage + 1}/${pages.size}"
                    Text("$title · $pageText", maxLines = 1) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (readerMode != ReaderMode.SELECTOR && startPageIndex == -1) {
                            readerMode = ReaderMode.SELECTOR
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (readerMode != ReaderMode.SELECTOR) {
                        if (selectedPage > 0) {
                            IconButton(onClick = {
                                scope.launch {
                                    if (readerMode == ReaderMode.VERTICAL) {
                                        verticalListState.animateScrollToItem(selectedPage - 1)
                                    } else {
                                        pagerState.animateScrollToPage(selectedPage - 1)
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Попередня")
                            }
                        }
                        IconButton(onClick = { readerMode = ReaderMode.SELECTOR }) {
                            Icon(Icons.Default.GridView, contentDescription = "Всі сторінки")
                        }
                    }
                    IconButton(onClick = { 
                        readerMode = if (readerMode == ReaderMode.VERTICAL) ReaderMode.HORIZONTAL else ReaderMode.VERTICAL
                    }) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Змінити режим")
                    }
                }
            )
        },
        bottomBar = {
            if (readerMode != ReaderMode.SELECTOR) {
                BottomPageNavigation(
                    currentPage = selectedPage,
                    totalPages = pages.size,
                    onPrev = {
                        scope.launch {
                            if (readerMode == ReaderMode.VERTICAL) {
                                verticalListState.animateScrollToItem((selectedPage - 1).coerceAtLeast(0))
                            } else {
                                pagerState.animateScrollToPage((selectedPage - 1).coerceAtLeast(0))
                            }
                        }
                    },
                    onNext = {
                        scope.launch {
                            if (selectedPage < pages.size - 1) {
                                if (readerMode == ReaderMode.VERTICAL) {
                                    verticalListState.animateScrollToItem(selectedPage + 1)
                                } else {
                                    pagerState.animateScrollToPage(selectedPage + 1)
                                }
                            } else {
                                onNextChapter?.invoke()
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (pages.isEmpty()) {
                Text("Немає сторінок", Modifier.align(Alignment.Center))
                return@Box
            }

            when (readerMode) {
                ReaderMode.SELECTOR -> {
                    PageSelector(pages) { idx ->
                        selectedPage = idx
                        readerMode = ReaderMode.VERTICAL
                        scope.launch { verticalListState.scrollToItem(idx) }
                        onPageChanged(idx, 0)
                    }
                }
                ReaderMode.VERTICAL -> {
                    VerticalReader(pages, verticalListState, onNextChapter) { idx, offset ->
                        selectedPage = idx
                        onPageChanged(idx, offset)
                    }
                }
                ReaderMode.HORIZONTAL -> {
                    HorizontalPager(
                        state = pagerState, 
                        modifier = Modifier.fillMaxSize(),
                        key = { if (it < pages.size) pages[it].uri.toString() else "next_prompt" }
                    ) { idx ->
                        if (idx < pages.size) {
                            MangaImage(pages[idx], ContentScale.Fit, Modifier.fillMaxSize())
                        } else {
                            NextChapterPrompt(onNextChapter, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomPageNavigation(
    currentPage: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onPrev,
                enabled = currentPage > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, null)
                Spacer(Modifier.width(8.dp))
                Text("Попередня")
            }
            
            Text(
                "${currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (currentPage < totalPages - 1) "Наступна" else "Далі")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.NavigateNext, null)
            }
        }
    }
}

@Composable
private fun MangaImage(page: Page, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(page.uri)
            .crossfade(true)
            .allowHardware(false)
            .build(),
        contentDescription = page.name,
        contentScale = contentScale,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        },
        error = { result ->
            val errorMessage = result.result.throwable.message ?: "Невідома помилка"
            Column(
                Modifier.fillMaxSize().background(Color.DarkGray).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color.White)
                Text("Помилка файлу", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text(errorMessage, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun PageSelector(pages: List<Page>, onSelect: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(80.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pages.size) { idx ->
            Button(
                onClick = { onSelect(idx) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.aspectRatio(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text("${idx + 1}")
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun VerticalReader(
    pages: List<Page>,
    listState: LazyListState,
    onNextChapter: (() -> Unit)?,
    onVisiblePage: (Int, Int) -> Unit
) {
    var canSave by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        canSave = true
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(1000)
            .collectLatest { (idx, offset) -> 
                if (idx < pages.size && canSave) {
                    onVisiblePage(idx, offset) 
                }
            }
    }

    androidx.compose.foundation.lazy.LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(
            count = pages.size, 
            key = { pages[it].uri.toString() }
        ) { idx ->
            MangaImage(
                page = pages[idx],
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 500.dp)
            )
        }
        item(key = "next_prompt") {
            NextChapterPrompt(onNextChapter, Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 128.dp))
        }
    }
}

@Composable
private fun NextChapterPrompt(onNextChapter: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (onNextChapter != null) {
            Button(onClick = onNextChapter) {
                Text("Наступний розділ")
            }
        } else {
            Text("Кінець манґи")
        }
    }
}
