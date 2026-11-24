package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.LibraryOverviewSheet
import com.example.ui.screens.BookshelfScreen
import com.example.ui.screens.ImportCenterScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TonalFolioTheme
import com.example.ui.viewmodel.BookshelfFilter
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.ReaderViewModel

private object AppRoutes {
    const val BOOKSHELF = "bookshelf"
    const val IMPORT = "import"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}"

    fun reader(bookId: Long): String = "reader/$bookId"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopLevelDestination(
        route = AppRoutes.BOOKSHELF,
        label = "书架",
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
        testTag = "nav_bookshelf"
    ),
    TopLevelDestination(
        route = AppRoutes.IMPORT,
        label = "导入",
        selectedIcon = Icons.Filled.CreateNewFolder,
        unselectedIcon = Icons.Outlined.CreateNewFolder,
        testTag = "nav_import"
    ),
    TopLevelDestination(
        route = AppRoutes.SETTINGS,
        label = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

class MainActivity : ComponentActivity() {

    private var pendingExternalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val libraryViewModel: LibraryViewModel = viewModel()
            val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(pendingExternalUri) {
                val uri = pendingExternalUri
                if (uri != null) {
                    libraryViewModel.importFiles(listOf(uri), persistLocalCopy = true)
                    pendingExternalUri = null
                }
            }

            val darkTheme = uiState.preferences.darkModeOverride ?: isSystemInDarkTheme()
            TonalFolioTheme(
                darkTheme = darkTheme,
                dynamicColor = uiState.preferences.useDynamicColor
            ) {
                TonalFolioRoot(libraryViewModel = libraryViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                pendingExternalUri = uri
            }
        }
    }
}

@Composable
private fun TonalFolioRoot(
    libraryViewModel: LibraryViewModel
) {
    val navController = rememberNavController()
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isInReader = currentRoute?.startsWith("reader") == true

    val snackbarHostState = remember { SnackbarHostState() }
    var showOverviewSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        libraryViewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val openSingleDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            libraryViewModel.importDocumentToInternalStorage(uri)
            if (currentRoute != AppRoutes.IMPORT) {
                navController.navigate(AppRoutes.IMPORT) {
                    popUpTo(AppRoutes.BOOKSHELF) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val openMultipleDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            libraryViewModel.importFiles(uris, persistLocalCopy = true)
            if (currentRoute != AppRoutes.IMPORT) {
                navController.navigate(AppRoutes.IMPORT) {
                    popUpTo(AppRoutes.BOOKSHELF) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            libraryViewModel.scanFolder(treeUri)
        }
    }

    val supportedDocumentMimeTypes = remember {
        arrayOf(
            "application/zip",
            "application/x-cbz",
            "application/vnd.comicbook+zip",
            "application/epub+zip",
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "application/x-zip-compressed",
            "application/octet-stream",
            "*/*"
        )
    }

    val launchSingleDocumentPicker: () -> Unit = {
        openSingleDocumentLauncher.launch(supportedDocumentMimeTypes)
    }

    val launchMultipleDocumentsPicker: () -> Unit = {
        openMultipleDocumentsLauncher.launch(supportedDocumentMimeTypes)
    }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(AppRoutes.BOOKSHELF) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = if (isInReader) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            bottomBar = {
                if (!isInReader && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isInReader && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.BOOKSHELF
                    ) {
                        composable(AppRoutes.BOOKSHELF) {
                            BookshelfScreen(
                                uiState = uiState,
                                onSearchChange = libraryViewModel::updateBookshelfSearch,
                                onSelectFilter = libraryViewModel::selectBookshelfFilter,
                                onToggleGridView = libraryViewModel::toggleGridView,
                                onSelectSortOrder = libraryViewModel::setSortOrder,
                                onOpenBook = { bookId ->
                                    navController.navigate(AppRoutes.reader(bookId))
                                },
                                onQuickImportFiles = launchSingleDocumentPicker,
                                onNavigateToImportCenter = { navigateTopLevel(AppRoutes.IMPORT) },
                                onOpenProfileOverview = {
                                    libraryViewModel.refreshStorageStats()
                                    showOverviewSheet = true
                                },
                                onUpdateBookMetadata = libraryViewModel::updateBookMetadata,
                                onToggleBookTag = libraryViewModel::toggleBookTag,
                                onSetBookStatus = libraryViewModel::setBookStatus,
                                onToggleBookFavorite = libraryViewModel::toggleBookFavorite,
                                onDeleteBook = libraryViewModel::deleteBook
                            )
                        }

                        composable(AppRoutes.IMPORT) {
                            ImportCenterScreen(
                                uiState = uiState,
                                onPickLocalFile = launchSingleDocumentPicker,
                                onPickMultipleFiles = launchMultipleDocumentsPicker,
                                onPickFolderTree = { openFolderLauncher.launch(null) },
                                onRescanFolder = libraryViewModel::rescanFolder,
                                onRemoveFolder = libraryViewModel::removeAuthorizedFolder,
                                onRetryTask = libraryViewModel::retryImportTask,
                                onClearFinishedTasks = libraryViewModel::clearFinishedImportTasks,
                                onOpenCreatedBook = { bookId ->
                                    navController.navigate(AppRoutes.reader(bookId))
                                },
                                onSetAutoCacheCovers = libraryViewModel::setAutoCacheCovers,
                                onSetAutoTagByFilename = libraryViewModel::setAutoTagByFilename,
                                onSetSkipDuplicates = libraryViewModel::setSkipDuplicates,
                                onOpenProfileOverview = {
                                    libraryViewModel.refreshStorageStats()
                                    showOverviewSheet = true
                                }
                            )
                        }

                        composable(AppRoutes.SETTINGS) {
                            SettingsScreen(
                                uiState = uiState,
                                onTagSearchChange = libraryViewModel::updateTagSearch,
                                onToggleBatchMode = libraryViewModel::toggleTagBatchMode,
                                onToggleBatchTagSelection = libraryViewModel::toggleBatchTagSelection,
                                onCreateTag = libraryViewModel::createTag,
                                onUpdateTag = libraryViewModel::updateTag,
                                onDeleteTags = libraryViewModel::deleteTags,
                                onMergeSelectedTagsInto = libraryViewModel::mergeSelectedTagsInto,
                                onMoveSelectedTagsToCategory = libraryViewModel::moveSelectedTagsToCategory,
                                onToggleSmartRule = libraryViewModel::setSmartRuleEnabled,
                                onSaveSmartRule = libraryViewModel::saveSmartRule,
                                onDeleteSmartRule = libraryViewModel::deleteSmartRule,
                                onApplySmartRulesNow = libraryViewModel::applySmartRulesNow,
                                onFilterBookshelfByTag = { tag ->
                                    libraryViewModel.selectBookshelfFilter(BookshelfFilter.ByTag(tag.id, tag.name))
                                    navigateTopLevel(AppRoutes.BOOKSHELF)
                                },
                                onSetDarkModeOverride = libraryViewModel::setDarkModeOverride,
                                onSetUseDynamicColor = libraryViewModel::setUseDynamicColor,
                                onSetDefaultComicDirection = libraryViewModel::setDefaultComicDirection,
                                onSetDefaultKeepScreenOn = libraryViewModel::setDefaultKeepScreenOn,
                                onOpenProfileOverview = {
                                    libraryViewModel.refreshStorageStats()
                                    showOverviewSheet = true
                                }
                            )
                        }

                        composable(
                            route = AppRoutes.READER,
                            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                        ) { entry ->
                            val bookId = entry.arguments?.getLong("bookId") ?: -1L
                            val readerViewModel: ReaderViewModel = viewModel(entry)
                            ReaderScreen(
                                bookId = bookId,
                                viewModel = readerViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOverviewSheet) {
        LibraryOverviewSheet(
            books = uiState.allBooks,
            storageStatus = uiState.storageStatus,
            preferences = uiState.preferences,
            onSetDarkModeOverride = libraryViewModel::setDarkModeOverride,
            onSetUseDynamicColor = libraryViewModel::setUseDynamicColor,
            onDismiss = { showOverviewSheet = false }
        )
    }
}
