package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.BookFormat
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ComicReadingDirection
import com.example.data.preferences.EpubReaderTheme
import com.example.parser.EpubContentBlock
import com.example.ui.theme.NotoSansFontFamily
import com.example.ui.theme.ReaderNightBg
import com.example.ui.theme.ReaderNightText
import com.example.ui.theme.ReaderPaperBg
import com.example.ui.theme.ReaderPaperText
import com.example.ui.theme.ReaderSepiaBg
import com.example.ui.theme.ReaderSepiaText
import com.example.ui.theme.ReaderWarmOverlay
import com.example.ui.viewmodel.ReaderDisplayMode
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    bookId: Long,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var showHud by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showTypographyOrDirectionSheet by remember { mutableStateOf(false) }
    var showBrightnessSheet by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    // Real Window Keep Screen On & Screen Brightness management
    val context = LocalContext.current
    DisposableEffect(prefs.keepScreenOn, prefs.readerBrightness) {
        val window = (context as? Activity)?.window
        val initialBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window != null) {
            if (prefs.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val lp = window.attributes
            lp.screenBrightness = if (prefs.readerBrightness >= 0f) {
                prefs.readerBrightness.coerceIn(0.05f, 1f)
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = lp
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val lp = window.attributes
                lp.screenBrightness = initialBrightness
                window.attributes = lp
            }
        }
    }

    val canvasBackgroundColor = when {
        state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY -> when (prefs.epubTheme) {
            EpubReaderTheme.PAPER -> ReaderPaperBg
            EpubReaderTheme.SEPIA -> ReaderSepiaBg
            EpubReaderTheme.NIGHT -> ReaderNightBg
        }
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBackgroundColor)
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "正在解析阅读内容…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = state.errorMessage ?: "打开失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onBack) {
                        Text("返回书架")
                    }
                }
            }

            state.displayMode == ReaderDisplayMode.COMIC -> {
                ComicReaderCanvas(
                    pages = state.comicPages,
                    currentPage = state.currentComicPage,
                    direction = prefs.comicReadingDirection,
                    trimWhiteMargins = prefs.trimWhiteMargins,
                    loadBitmap = { path -> viewModel.loadPageBitmap(path, prefs.trimWhiteMargins) },
                    onPageChanged = { viewModel.onComicPageChanged(it) },
                    onToggleHud = { showHud = !showHud },
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                EpubReaderCanvas(
                    state = state,
                    prefs = prefs,
                    loadBitmap = { path -> viewModel.loadPageBitmap(path, trimWhiteMargins = false) },
                    onSelectChapter = { viewModel.selectEpubChapter(it) },
                    onToggleHud = { showHud = !showHud },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Warmth eye-care overlay (when warmthLevel > 0)
        if (prefs.warmthLevel > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ReaderWarmOverlay.copy(alpha = prefs.warmthLevel * 0.28f))
            )
        }

        // Page counter pill at bottom-right when HUD hidden in comic mode
        if (!showHud && !state.isLoading && state.errorMessage == null) {
            val total = if (state.displayMode == ReaderDisplayMode.COMIC) {
                state.comicPages.size.coerceAtLeast(1)
            } else {
                (state.epubStructure?.chapters?.size ?: 1).coerceAtLeast(1)
            }
            val current = if (state.displayMode == ReaderDisplayMode.COMIC) {
                state.currentComicPage + 1
            } else {
                state.currentChapterIndex + 1
            }
            Surface(
                shape = CircleShape,
                color = Color(0xB81B1B1E),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "$current / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Top Floating HUD
        AnimatedVisibility(
            visible = showHud && !state.isLoading && state.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val book = state.book
            val isBookmarked = state.bookmarks.any {
                val activeUnit = if (state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY) {
                    state.currentChapterIndex
                } else {
                    state.currentComicPage
                }
                it.unitIndex == activeUnit
            }
            val chapterBadge = if (state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY) {
                "第 ${state.currentChapterIndex + 1} 章"
            } else {
                "第 ${state.currentComicPage + 1} 页"
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回书架"
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = chapterBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = book?.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { viewModel.toggleCurrentBookmark() },
                        modifier = Modifier.testTag("reader_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isBookmarked) "取消书签" else "添加书签",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "目录与书签")
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("章节 / 页面目录") },
                                onClick = {
                                    overflowExpanded = false
                                    showTocSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("书签列表 (${state.bookmarks.size})") },
                                onClick = {
                                    overflowExpanded = false
                                    showBookmarksSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating HUD Card matching Stitch mockup
        AnimatedVisibility(
            visible = showHud && !state.isLoading && state.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val isComicMode = state.displayMode == ReaderDisplayMode.COMIC
            val totalUnits = if (isComicMode) {
                state.comicPages.size.coerceAtLeast(1)
            } else {
                (state.epubStructure?.chapters?.size ?: 1).coerceAtLeast(1)
            }
            val currentUnit = if (isComicMode) state.currentComicPage else state.currentChapterIndex
            val unitSuffix = if (isComicMode) "页" else "章"

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Dual Mode Switcher: 漫画/PDF 页面模式 | 电子书文本排版模式
                    val bookFormat = state.book?.format
                    val pageModeLabel = if (bookFormat == BookFormat.PDF) "PDF 翻页模式" else "漫画模式"
                    val textModeLabel = when (bookFormat) {
                        BookFormat.TXT -> "TXT 排版模式"
                        BookFormat.MD -> "Markdown 排版"
                        else -> "EPUB 排版模式"
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ModeSegmentButton(
                                selected = isComicMode,
                                icon = Icons.Outlined.AutoStories,
                                label = pageModeLabel,
                                enabled = state.comicPages.isNotEmpty(),
                                onClick = { viewModel.switchDisplayMode(ReaderDisplayMode.COMIC) },
                                modifier = Modifier.weight(1f)
                            )
                            ModeSegmentButton(
                                selected = !isComicMode,
                                icon = Icons.AutoMirrored.Outlined.MenuBook,
                                label = textModeLabel,
                                enabled = !state.epubStructure?.chapters.isNullOrEmpty(),
                                onClick = { viewModel.switchDisplayMode(ReaderDisplayMode.EPUB_TYPOGRAPHY) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Scrubber Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "阅读进度",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "第 ${currentUnit + 1} / $totalUnits $unitSuffix",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentUnit > 0) {
                                    if (isComicMode) viewModel.onComicPageChanged(currentUnit - 1)
                                    else viewModel.selectEpubChapter(currentUnit - 1)
                                }
                            },
                            enabled = currentUnit > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一$unitSuffix")
                        }

                        var sliderValue by remember(currentUnit, totalUnits) {
                            mutableFloatStateOf(currentUnit.toFloat())
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                val target = sliderValue.toInt().coerceIn(0, totalUnits - 1)
                                if (isComicMode) viewModel.onComicPageChanged(target)
                                else viewModel.selectEpubChapter(target)
                            },
                            valueRange = 0f..(totalUnits - 1).coerceAtLeast(1).toFloat(),
                            enabled = totalUnits > 1,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (currentUnit < totalUnits - 1) {
                                    if (isComicMode) viewModel.onComicPageChanged(currentUnit + 1)
                                    else viewModel.selectEpubChapter(currentUnit + 1)
                                }
                            },
                            enabled = currentUnit < totalUnits - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一$unitSuffix")
                        }
                    }

                    // 4 Quick Control Buttons matching Stitch design
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudQuickActionChip(
                            icon = if (isComicMode) Icons.Outlined.SwapHoriz else Icons.Outlined.TextFields,
                            label = if (isComicMode) prefs.comicReadingDirection.label else "字体排版",
                            active = false,
                            onClick = { showTypographyOrDirectionSheet = true },
                            modifier = Modifier.weight(1f)
                        )
                        HudQuickActionChip(
                            icon = if (isComicMode) Icons.Outlined.Crop else Icons.Outlined.MenuBook,
                            label = if (isComicMode) "裁剪白边" else "章节目录",
                            active = isComicMode && prefs.trimWhiteMargins,
                            onClick = {
                                if (isComicMode) viewModel.toggleTrimWhiteMargins()
                                else showTocSheet = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HudQuickActionChip(
                            icon = Icons.Outlined.LightMode,
                            label = "屏幕常亮",
                            active = prefs.keepScreenOn,
                            onClick = { viewModel.toggleKeepScreenOn() },
                            modifier = Modifier.weight(1f)
                        )
                        HudQuickActionChip(
                            icon = Icons.Outlined.Brightness6,
                            label = "亮度/色温",
                            active = prefs.readerBrightness >= 0f || prefs.warmthLevel > 0.05f,
                            onClick = { showBrightnessSheet = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // TOC / Chapters or Pages Sheet
    if (showTocSheet) {
        TocBottomSheet(
            state = state,
            onDismiss = { showTocSheet = false },
            onSelectChapter = { idx ->
                viewModel.selectEpubChapter(idx)
                showTocSheet = false
            },
            onSelectPage = { idx ->
                viewModel.onComicPageChanged(idx)
                showTocSheet = false
            }
        )
    }

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            state = state,
            onDismiss = { showBookmarksSheet = false },
            onJump = { bookmark ->
                viewModel.jumpToBookmark(bookmark)
                showBookmarksSheet = false
            },
            onDelete = { bookmark ->
                viewModel.deleteBookmark(bookmark.id)
            }
        )
    }

    // Comic Reading Direction or EPUB Typography Sheet
    if (showTypographyOrDirectionSheet) {
        if (state.displayMode == ReaderDisplayMode.COMIC) {
            ComicDirectionSheet(
                title = if (state.book?.format == BookFormat.PDF) "PDF 翻页方向" else "漫画翻页方向",
                current = prefs.comicReadingDirection,
                onSelect = {
                    viewModel.setComicDirection(it)
                    showTypographyOrDirectionSheet = false
                },
                onDismiss = { showTypographyOrDirectionSheet = false }
            )
        } else {
            EpubTypographySheet(
                prefs = prefs,
                onFontSizeChange = viewModel::setEpubFontSize,
                onLineHeightChange = viewModel::setEpubLineHeight,
                onSerifChange = viewModel::setEpubUseSerif,
                onThemeChange = viewModel::setEpubTheme,
                onDismiss = { showTypographyOrDirectionSheet = false }
            )
        }
    }

    // Brightness & Warmth Sheet
    if (showBrightnessSheet) {
        BrightnessWarmthSheet(
            brightness = prefs.readerBrightness,
            warmth = prefs.warmthLevel,
            onBrightnessChange = viewModel::setReaderBrightness,
            onWarmthChange = viewModel::setWarmthLevel,
            onDismiss = { showBrightnessSheet = false }
        )
    }
}

@Composable
private fun ComicReaderCanvas(
    pages: List<String>,
    currentPage: Int,
    direction: ComicReadingDirection,
    trimWhiteMargins: Boolean,
    loadBitmap: suspend (String) -> Bitmap?,
    onPageChanged: (Int) -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("该文档中未包含可显示的图片页", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    if (direction == ComicReadingDirection.WEBTOON_VERTICAL) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, pages.lastIndex))
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { idx -> onPageChanged(idx) }
        }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleHud
                ),
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(0.dp)
        ) {
            items(pages.size, key = { pages[it] }) { idx ->
                ComicBitmapPage(
                    entryPath = pages[idx],
                    trimWhiteMargins = trimWhiteMargins,
                    loadBitmap = loadBitmap,
                    allowZoom = false,
                    onTapCenter = onToggleHud,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        val pagerState = rememberPagerState(
            initialPage = currentPage.coerceIn(0, pages.lastIndex),
            pageCount = { pages.size }
        )
        LaunchedEffect(currentPage) {
            if (pagerState.currentPage != currentPage && currentPage in pages.indices) {
                pagerState.scrollToPage(currentPage)
            }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page -> onPageChanged(page) }
        }
        val scope = rememberCoroutineScope()
        HorizontalPager(
            state = pagerState,
            reverseLayout = direction == ComicReadingDirection.MANGA_RTL,
            beyondViewportPageCount = 1,
            modifier = modifier.fillMaxSize()
        ) { pageIdx ->
            ComicBitmapPage(
                entryPath = pages[pageIdx],
                trimWhiteMargins = trimWhiteMargins,
                loadBitmap = loadBitmap,
                allowZoom = true,
                onTapCenter = onToggleHud,
                onTapLeft = {
                    scope.launch {
                        val delta = if (direction == ComicReadingDirection.MANGA_RTL) 1 else -1
                        val target = (pageIdx + delta).coerceIn(0, pages.lastIndex)
                        pagerState.animateScrollToPage(target)
                    }
                },
                onTapRight = {
                    scope.launch {
                        val delta = if (direction == ComicReadingDirection.MANGA_RTL) -1 else 1
                        val target = (pageIdx + delta).coerceIn(0, pages.lastIndex)
                        pagerState.animateScrollToPage(target)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ComicBitmapPage(
    entryPath: String,
    trimWhiteMargins: Boolean,
    loadBitmap: suspend (String) -> Bitmap?,
    allowZoom: Boolean,
    onTapCenter: () -> Unit,
    onTapLeft: (() -> Unit)? = null,
    onTapRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, entryPath, trimWhiteMargins) {
        value = loadBitmap(entryPath)
    }

    var scale by remember(entryPath) { mutableFloatStateOf(1f) }
    var offset by remember(entryPath) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(entryPath, allowZoom) {
                detectTapGestures(
                    onDoubleTap = {
                        if (allowZoom) {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.2f
                            }
                        }
                    },
                    onTap = { tapOffset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val relX = tapOffset.x / w
                        when {
                            scale > 1.05f -> onTapCenter()
                            relX < 0.25f && onTapLeft != null -> onTapLeft()
                            relX > 0.75f && onTapRight != null -> onTapRight()
                            else -> onTapCenter()
                        }
                    }
                )
            }
            .then(
                if (allowZoom) {
                    Modifier.pointerInput(entryPath) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            offset = if (newScale > 1.02f) offset + pan else Offset.Zero
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded == null || loaded.isRecycled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        } else {
            val aspect = loaded.width.toFloat() / loaded.height.toFloat().coerceAtLeast(1f)
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = entryPath.substringAfterLast('/'),
                contentScale = if (allowZoom) ContentScale.Fit else ContentScale.FillBounds,
                modifier = Modifier
                    .then(
                        if (allowZoom) Modifier.fillMaxSize()
                        else Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect.coerceAtLeast(0.05f))
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}

@Composable
private fun EpubReaderCanvas(
    state: ReaderUiState,
    prefs: AppPreferences,
    loadBitmap: suspend (String) -> Bitmap?,
    onSelectChapter: (Int) -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chapters = state.epubStructure?.chapters.orEmpty()
    val currentChapter = chapters.getOrNull(state.currentChapterIndex)
    val textColor = when (prefs.epubTheme) {
        EpubReaderTheme.PAPER -> ReaderPaperText
        EpubReaderTheme.SEPIA -> ReaderSepiaText
        EpubReaderTheme.NIGHT -> ReaderNightText
    }
    val fontFamily = if (prefs.epubUseSerif) FontFamily.Serif else NotoSansFontFamily

    if (state.isChapterLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = 72.dp,
            bottom = 140.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleHud
            )
    ) {
        if (currentChapter != null) {
            item {
                Text(
                    text = currentChapter.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = textColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.14f))
            }
        }

        items(state.currentChapterBlocks) { block ->
            val inlineCodeBg = textColor.copy(alpha = 0.10f)
            when (block) {
                is EpubContentBlock.Heading -> {
                    val scaleDelta = when {
                        block.level <= 1 -> 6f
                        block.level == 2 -> 4f
                        block.level == 3 -> 2f
                        else -> 1f
                    }
                    Text(
                        text = formatInlineMarkdown(block.text, inlineCodeBg),
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (prefs.epubFontSizeSp + scaleDelta).sp,
                        lineHeight = ((prefs.epubFontSizeSp + scaleDelta) * 1.4f).sp,
                        color = textColor
                    )
                }
                is EpubContentBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text, inlineCodeBg),
                        fontFamily = fontFamily,
                        fontSize = prefs.epubFontSizeSp.sp,
                        lineHeight = (prefs.epubFontSizeSp * prefs.epubLineHeightMultiplier).sp,
                        color = textColor
                    )
                }
                is EpubContentBlock.Quote -> {
                    Surface(
                        color = textColor.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatInlineMarkdown(block.text, inlineCodeBg),
                            fontFamily = fontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = (prefs.epubFontSizeSp - 1f).sp,
                            lineHeight = ((prefs.epubFontSizeSp - 1f) * prefs.epubLineHeightMultiplier).sp,
                            color = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is EpubContentBlock.CodeBlock -> {
                    Surface(
                        color = textColor.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Text(
                                text = block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (prefs.epubFontSizeSp - 2f).coerceAtLeast(11f).sp,
                                lineHeight = ((prefs.epubFontSizeSp - 2f).coerceAtLeast(11f) * 1.45f).sp,
                                color = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
                EpubContentBlock.DividerBlock -> {
                    HorizontalDivider(
                        color = textColor.copy(alpha = 0.16f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                is EpubContentBlock.ImageBlock -> {
                    EpubInlineImage(
                        entryPath = block.entryPath,
                        altText = block.altText,
                        loadBitmap = loadBitmap
                    )
                }
            }
        }

        // Chapter Footer Navigation
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onSelectChapter(state.currentChapterIndex - 1) },
                    enabled = state.currentChapterIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("上一章")
                }
                Button(
                    onClick = { onSelectChapter(state.currentChapterIndex + 1) },
                    enabled = state.currentChapterIndex < chapters.lastIndex,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下一章")
                }
            }
        }
    }
}

@Composable
private fun EpubInlineImage(
    entryPath: String,
    altText: String,
    loadBitmap: suspend (String) -> Bitmap?
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, entryPath) {
        value = loadBitmap(entryPath)
    }
    val loaded = bitmap
    if (loaded != null && !loaded.isRecycled) {
        val aspect = loaded.width.toFloat() / loaded.height.toFloat().coerceAtLeast(1f)
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = altText.ifBlank { "章节插图" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.3f, 3f))
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun ModeSegmentButton(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HudQuickActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocBottomSheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSelectPage: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            val chapters = state.epubStructure?.chapters.orEmpty()
            val showChapters = state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY && chapters.isNotEmpty()
            Text(
                text = if (showChapters) "章节目录 (${chapters.size})" else "页面索引 (${state.comicPages.size})",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showChapters) {
                    items(chapters, key = { it.index }) { ch ->
                        val active = ch.index == state.currentChapterIndex
                        Surface(
                            onClick = { onSelectChapter(ch.index) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = ch.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }
                    }
                } else {
                    items(state.comicPages.size) { idx ->
                        val active = idx == state.currentComicPage
                        Surface(
                            onClick = { onSelectPage(idx) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val rawEntry = state.comicPages[idx]
                            val pageLabel = if (rawEntry.startsWith("pdf_page_")) {
                                "第 ${idx + 1} 页"
                            } else {
                                "第 ${idx + 1} 页 · ${rawEntry.substringAfterLast('/')}"
                            }
                            Text(
                                text = pageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksBottomSheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onJump: (com.example.data.local.entity.BookmarkEntity) -> Unit,
    onDelete: (com.example.data.local.entity.BookmarkEntity) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("书签 (${state.bookmarks.size})", style = MaterialTheme.typography.titleLarge)
            if (state.bookmarks.isEmpty()) {
                Text(
                    text = "暂无书签，点击顶部书签图标即可收藏当前阅读位置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.bookmarks, key = { it.id }) { bm ->
                        Surface(
                            onClick = { onJump(bm) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(bm.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onDelete(bm) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除书签")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComicDirectionSheet(
    title: String,
    current: ComicReadingDirection,
    onSelect: (ComicReadingDirection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            ComicReadingDirection.entries.forEach { dir ->
                val selected = current == dir
                Surface(
                    onClick = { onSelect(dir) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(dir.label, style = MaterialTheme.typography.titleSmall)
                            val desc = when (dir) {
                                ComicReadingDirection.MANGA_RTL -> "从右向左翻页，适配日漫分镜"
                                ComicReadingDirection.COMIC_LTR -> "从左向右翻页，适配欧美/国漫"
                                ComicReadingDirection.WEBTOON_VERTICAL -> "上下连续无缝滚动，适配条漫"
                            }
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpubTypographySheet(
    prefs: AppPreferences,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onThemeChange: (EpubReaderTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("阅读排版设置", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("字号 (${prefs.epubFontSizeSp.toInt()} sp)", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = prefs.epubFontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 13f..28f,
                    modifier = Modifier.width(200.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("行距 (${"%.2f".format(prefs.epubLineHeightMultiplier)}x)", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = prefs.epubLineHeightMultiplier,
                    onValueChange = onLineHeightChange,
                    valueRange = 1.3f..2.2f,
                    modifier = Modifier.width(200.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !prefs.epubUseSerif,
                    onClick = { onSerifChange(false) },
                    label = { Text("无衬线体 (Noto Sans)") }
                )
                FilterChip(
                    selected = prefs.epubUseSerif,
                    onClick = { onSerifChange(true) },
                    label = { Text("衬线体 (Serif)") }
                )
            }

            Text("纸张底色", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EpubReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = prefs.epubTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessWarmthSheet(
    brightness: Float,
    warmth: Float,
    onBrightnessChange: (Float) -> Unit,
    onWarmthChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("亮度与护眼色温", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onBrightnessChange(-1f) }) {
                    Text("跟随系统亮度")
                }
            }

            Text(
                text = if (brightness < 0f) "屏幕亮度：跟随系统" else "屏幕亮度：${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = if (brightness < 0f) 0.5f else brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.05f..1f
            )

            Text(
                text = "暖色护眼滤镜：${(warmth * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = warmth,
                onValueChange = onWarmthChange,
                valueRange = 0f..1f
            )
        }
    }
}

private val inlineMarkdownRegex = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)""")

private fun formatInlineMarkdown(text: String, codeBgColor: Color): AnnotatedString {
    if (!text.contains('*') && !text.contains('`')) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var lastIdx = 0
        for (match in inlineMarkdownRegex.findAll(text)) {
            if (match.range.first > lastIdx) {
                append(text.substring(lastIdx, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") && token.length > 4 -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.substring(2, token.length - 2))
                    }
                }
                token.startsWith("`") && token.endsWith("`") && token.length > 2 -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBgColor)) {
                        append(token.substring(1, token.length - 1))
                    }
                }
                token.startsWith("*") && token.endsWith("*") && token.length > 2 -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.substring(1, token.length - 1))
                    }
                }
                else -> append(token)
            }
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}
