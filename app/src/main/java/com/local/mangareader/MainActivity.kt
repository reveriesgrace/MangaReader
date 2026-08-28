package com.local.mangareader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.mangareader.data.Chapter
import com.local.mangareader.data.Manga
import com.local.mangareader.data.MangaRepository
import com.local.mangareader.data.ProgressStore
import com.local.mangareader.ui.SettingsScreen
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private lateinit var repository: MangaRepository
    private lateinit var progressStore: ProgressStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MangaRepository(this)
        progressStore = ProgressStore(this)

        setContent {
            val themePreference by progressStore.themeFlow().collectAsState(initial = "system")
            val darkTheme = when (themePreference) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(repository, progressStore)
                }
            }
        }
    }
}

@Composable
fun AppNavHost(repository: MangaRepository, progressStore: ProgressStore) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var mangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isScanning by remember { mutableStateOf(value = false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val themePreference by progressStore.themeFlow().collectAsState(initial = "system")

    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        
        try {
            Log.d("MainActivity", "Selected URI: $uri")
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch {
                try {
                    progressStore.saveRootUri(uri.toString())
                    isScanning = true
                    mangaList = repository.scanLibrary(uri)
                    isScanning = false
                    if (mangaList.isEmpty()) {
                        Toast.makeText(context, "Манґу не знайдено в цій папці", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error during scan", e)
                    isScanning = false
                    Toast.makeText(context, "Помилка сканування: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error taking permissions", e)
            Toast.makeText(context, "Помилка доступу до папки", Toast.LENGTH_LONG).show()
        }
    }

    // Try to restore the previously picked folder on launch.
    LaunchedEffect(Unit) {
        progressStore.rootUriFlow().collect { saved ->
            if (saved != null && mangaList.isEmpty() && !isScanning) {
                try {
                    val uri = Uri.parse(saved)
                    Log.d("MainActivity", "Restoring URI: $uri")
                    isScanning = true
                    mangaList = repository.scanLibrary(uri)
                    isScanning = false
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error restoring library", e)
                    isScanning = false
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            com.local.mangareader.ui.LibraryScreen(
                manga = mangaList,
                isScanning = isScanning,
                onPickFolder = { folderPicker.launch(null) },
                onOpenSettings = { navController.navigate("settings") }
            ) { manga ->
                navController.navigate("manga/${encode(manga.name)}")
            }
        }

        composable("settings") {
            SettingsScreen(
                currentTheme = themePreference,
                onBack = { navController.popBackStack() },
                onThemeChanged = { scope.launch { progressStore.setTheme(it) } }
            )
        }

        composable(
            "manga/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = decode(backStackEntry.arguments?.getString("name") ?: "")
            val manga = mangaList.find { it.name == name }
            if (manga == null) return@composable

            var lastRead by remember { mutableStateOf<Pair<Triple<String, String, Int>, Int>?>(null) }
            LaunchedEffect(name) {
                progressStore.progressFlow(name).collect { lastRead = it }
            }

            com.local.mangareader.ui.MangaDetailScreen(
                manga = manga,
                lastRead = lastRead?.first,
                onBack = { navController.popBackStack() },
                onOpenChapter = { volumeName, chapter, startPage ->
                    val offset = if (lastRead?.first?.first == volumeName && 
                        lastRead?.first?.second == chapter.number && 
                        lastRead?.first?.third == startPage) lastRead!!.second else 0

                    Log.d("MainActivity", "Opening chapter ${chapter.number} at page $startPage with offset $offset")

                    navController.navigate(
                        "reader/${encode(name)}/${encode(volumeName)}/${encode(chapter.number)}?page=$startPage&offset=$offset"
                    )
                }
            )
        }

        composable(
            "reader/{name}/{volume}/{chapter}?page={page}&offset={offset}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("volume") { type = NavType.StringType },
                navArgument("chapter") { type = NavType.StringType },
                navArgument("page") { 
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("offset") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val name = decode(backStackEntry.arguments?.getString("name") ?: "")
            val volumeName = decode(backStackEntry.arguments?.getString("volume") ?: "")
            val chapterNumber = decode(backStackEntry.arguments?.getString("chapter") ?: "")
            val startPage = backStackEntry.arguments?.getInt("page") ?: -1
            val startOffset = backStackEntry.arguments?.getInt("offset") ?: 0

            val manga = mangaList.find { it.name == name }
            val allChapters = manga?.volumes?.flatMap { vol ->
                vol.chapters.map { vol.name to it }
            } ?: emptyList()

            val currentIdx = allChapters.indexOfFirst {
                it.first == volumeName && it.second.number == chapterNumber
            }
            val nextChapter = if (currentIdx != -1 && currentIdx < allChapters.size - 1) {
                allChapters[currentIdx + 1]
            } else null

            val chapter: Chapter? = allChapters.getOrNull(currentIdx)?.second

            if (manga == null || chapter == null) return@composable

            com.local.mangareader.ui.ReaderScreen(
                title = "${manga.name} · Розділ ${chapter.number}",
                pages = chapter.pages,
                startPageIndex = startPage,
                startPageOffset = startOffset,
                onBack = { navController.popBackStack() },
                onPageChanged = { pageIndex, offset ->
                    scope.launch {
                        progressStore.saveProgress(name, volumeName, chapterNumber, pageIndex, offset)
                    }
                },
                onNextChapter = nextChapter?.let { (vol, chap) ->
                    {
                        navController.navigate(
                            "reader/${encode(name)}/${encode(vol)}/${encode(chap.number)}?page=0&offset=0"
                        ) {
                            // Pop the current reader from backstack so "back" goes to manga details, 
                            // not the previous chapter.
                            popUpTo("manga/${encode(name)}")
                        }
                    }
                }
            )
        }
    }
}

private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")
