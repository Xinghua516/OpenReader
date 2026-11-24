package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TonalFolioApp
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ComicReadingDirection
import com.example.data.preferences.EpubReaderTheme
import com.example.parser.ArchiveSession
import com.example.parser.EpubBookStructure
import com.example.parser.EpubChapter
import com.example.parser.EpubContentBlock
import com.example.parser.EpubParser
import com.example.parser.ParsedTextChapter
import com.example.parser.PdfSession
import com.example.parser.TextAndMarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ReaderDisplayMode {
    COMIC,
    EPUB_TYPOGRAPHY
}

data class ReaderUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val book: BookEntity? = null,
    val displayMode: ReaderDisplayMode = ReaderDisplayMode.COMIC,
    val comicPages: List<String> = emptyList(),
    val currentComicPage: Int = 0,
    val comicScrollSyncToken: Int = 0,
    val epubStructure: EpubBookStructure? = null,
    val currentChapterIndex: Int = 0,
    val currentChapterOffsetPermille: Int = 0,
    val chapterScrollRestoreToken: Int = 0,
    val currentChapterBlocks: List<EpubContentBlock> = emptyList(),
    val isChapterLoading: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList()
) {
    val overallProgressPercent: Int
        get() = if (displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY) {
            val total = (epubStructure?.chapters?.size ?: book?.totalUnits ?: 1).coerceAtLeast(1)
            val safeChapter = currentChapterIndex.coerceIn(0, total - 1)
            val within = currentChapterOffsetPermille.coerceIn(0, 1000) / 1000f
            (((safeChapter.toFloat() + within) / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            val total = comicPages.size.coerceAtLeast(book?.totalUnits ?: 1).coerceAtLeast(1)
            val safePage = currentComicPage.coerceIn(0, total - 1)
            (((safePage + 1).toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TonalFolioApp
    private val repo = app.libraryRepository
    private val prefsRepo = app.preferencesRepository

    val preferences: StateFlow<AppPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferences()
    )

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val sessionMutex = Mutex()
    private var activeSession: ArchiveSession? = null
    private var activePdfSession: PdfSession? = null
    private var activeTextChapters: List<ParsedTextChapter> = emptyList()
    private var currentBookId: Long = -1L
    private var bookmarksJob: Job? = null
    private var scrollSaveJob: Job? = null

    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun openBook(bookId: Long) {
        if (currentBookId == bookId &&
            (activeSession != null || activePdfSession != null || activeTextChapters.isNotEmpty())
        ) return
        currentBookId = bookId

        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            repo.observeBookmarksForBook(bookId).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val bookWithTags = repo.observeBookWithTags(bookId).first()
                    ?: throw IllegalStateException("未找到该书籍记录")
                val book = bookWithTags.book

                sessionMutex.withLock {
                    activeSession?.close()
                    activeSession = null
                    activePdfSession?.close()
                    activePdfSession = null
                    activeTextChapters = emptyList()
                }
                bitmapCache.evictAll()

                when (book.format) {
                    BookFormat.PDF -> {
                        val pdfSession = PdfSession.open(
                            context = app,
                            sourceUri = book.sourceUri,
                            localCopyPath = book.localCopyPath,
                            cacheKey = book.fileHash
                        )
                        val count = pdfSession.pageCount
                        if (count <= 0) {
                            pdfSession.close()
                            throw IllegalStateException("PDF 文件不包含可显示的页面")
                        }
                        sessionMutex.withLock {
                            activePdfSession = pdfSession
                        }
                        val pageKeys = List(count) { "pdf_page_$it" }
                        val safePage = book.currentUnit.coerceIn(0, count - 1)
                        val preserveCompleted = book.readingStatus == ReadingStatus.COMPLETED
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                book = book.copy(
                                    currentUnit = safePage,
                                    readingStatus = if (preserveCompleted) ReadingStatus.COMPLETED else ReadingStatus.READING
                                ),
                                displayMode = ReaderDisplayMode.COMIC,
                                comicPages = pageKeys,
                                currentComicPage = safePage,
                                comicScrollSyncToken = it.comicScrollSyncToken + 1,
                                epubStructure = null,
                                currentChapterIndex = 0,
                                currentChapterOffsetPermille = 0,
                                currentChapterBlocks = emptyList()
                            )
                        }
                        repo.persistReadingProgressAsync(
                            bookId = book.id,
                            unitIndex = safePage,
                            offset = 0,
                            totalUnits = count,
                            isTextFlow = false,
                            preserveCompleted = preserveCompleted
                        )
                    }

                    BookFormat.TXT, BookFormat.MD -> {
                        val parsedDoc = withContext(Dispatchers.IO) {
                            val bytes = TextAndMarkdownParser.loadBytes(
                                context = app,
                                sourceUri = book.sourceUri,
                                localCopyPath = book.localCopyPath
                            )
                            if (book.format == BookFormat.MD) {
                                TextAndMarkdownParser.parseMarkdown(bytes, book.title)
                            } else {
                                TextAndMarkdownParser.parseTxt(bytes, book.title)
                            }
                        }
                        val chapters = parsedDoc.chapters
                        sessionMutex.withLock {
                            activeTextChapters = chapters
                        }
                        val epubChapters = chapters.map {
                            EpubChapter(
                                index = it.index,
                                title = it.title,
                                entryPath = "text_chapter_${it.index}"
                            )
                        }
                        val structure = EpubBookStructure(
                            title = parsedDoc.title.ifBlank { book.title },
                            author = parsedDoc.author.ifBlank { book.author },
                            language = "zh",
                            subjects = parsedDoc.tags,
                            coverEntryPath = null,
                            chapters = epubChapters
                        )
                        val safeChapter = book.currentUnit.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                        val restoredOffset = book.currentOffset.coerceIn(0, 1000)
                        val blocks = chapters.getOrNull(safeChapter)?.blocks.orEmpty()
                        val preserveCompleted = book.readingStatus == ReadingStatus.COMPLETED
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                book = book.copy(
                                    currentUnit = safeChapter,
                                    currentOffset = restoredOffset,
                                    readingStatus = if (preserveCompleted) ReadingStatus.COMPLETED else ReadingStatus.READING
                                ),
                                displayMode = ReaderDisplayMode.EPUB_TYPOGRAPHY,
                                comicPages = emptyList(),
                                currentComicPage = 0,
                                epubStructure = structure,
                                currentChapterIndex = safeChapter,
                                currentChapterOffsetPermille = restoredOffset,
                                chapterScrollRestoreToken = it.chapterScrollRestoreToken + 1,
                                currentChapterBlocks = blocks
                            )
                        }
                        repo.persistReadingProgressAsync(
                            bookId = book.id,
                            unitIndex = safeChapter,
                            offset = restoredOffset,
                            totalUnits = chapters.size.coerceAtLeast(1),
                            isTextFlow = true,
                            preserveCompleted = preserveCompleted
                        )
                    }

                    BookFormat.CBZ, BookFormat.ZIP, BookFormat.EPUB -> {
                        val session = ArchiveSession.open(
                            context = app,
                            sourceUri = book.sourceUri,
                            localCopyPath = book.localCopyPath,
                            cacheKey = book.fileHash
                        )
                        sessionMutex.withLock {
                            activeSession = session
                        }
                        val images = session.sortedImageEntries
                        val isEpub = book.format == BookFormat.EPUB ||
                            session.findEntryIgnoreCase("META-INF/container.xml") != null

                        if (isEpub) {
                            val structure = withContext(Dispatchers.IO) {
                                EpubParser.parseStructure(session)
                            }
                            val safeChapter = book.currentUnit.coerceIn(0, (structure.chapters.size - 1).coerceAtLeast(0))
                            val restoredOffset = book.currentOffset.coerceIn(0, 1000)
                            val blocks = if (structure.chapters.isNotEmpty()) {
                                withContext(Dispatchers.IO) {
                                    EpubParser.parseChapterContent(session, structure.chapters[safeChapter].entryPath)
                                }
                            } else {
                                emptyList()
                            }
                            val initialMode = if (structure.chapters.isNotEmpty()) {
                                ReaderDisplayMode.EPUB_TYPOGRAPHY
                            } else {
                                ReaderDisplayMode.COMIC
                            }
                            val preserveCompleted = book.readingStatus == ReadingStatus.COMPLETED
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    book = book.copy(
                                        currentUnit = safeChapter,
                                        currentOffset = restoredOffset,
                                        readingStatus = if (preserveCompleted) ReadingStatus.COMPLETED else ReadingStatus.READING
                                    ),
                                    displayMode = initialMode,
                                    comicPages = images,
                                    currentComicPage = if (initialMode == ReaderDisplayMode.COMIC) {
                                        book.currentUnit.coerceIn(0, (images.size - 1).coerceAtLeast(0))
                                    } else 0,
                                    comicScrollSyncToken = it.comicScrollSyncToken + 1,
                                    epubStructure = structure,
                                    currentChapterIndex = safeChapter,
                                    currentChapterOffsetPermille = restoredOffset,
                                    chapterScrollRestoreToken = it.chapterScrollRestoreToken + 1,
                                    currentChapterBlocks = blocks
                                )
                            }
                            if (initialMode == ReaderDisplayMode.EPUB_TYPOGRAPHY) {
                                repo.persistReadingProgressAsync(
                                    bookId = book.id,
                                    unitIndex = safeChapter,
                                    offset = restoredOffset,
                                    totalUnits = structure.chapters.size.coerceAtLeast(1),
                                    isTextFlow = true,
                                    preserveCompleted = preserveCompleted
                                )
                            } else if (images.isNotEmpty()) {
                                repo.persistReadingProgressAsync(
                                    bookId = book.id,
                                    unitIndex = book.currentUnit.coerceIn(0, images.size - 1),
                                    offset = 0,
                                    totalUnits = images.size,
                                    isTextFlow = false,
                                    preserveCompleted = preserveCompleted
                                )
                            }
                        } else {
                            if (images.isEmpty()) {
                                throw IllegalStateException("未能从压缩包内解析出图片页面")
                            }
                            val safePage = book.currentUnit.coerceIn(0, images.size - 1)
                            val preserveCompleted = book.readingStatus == ReadingStatus.COMPLETED
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    book = book.copy(
                                        currentUnit = safePage,
                                        readingStatus = if (preserveCompleted) ReadingStatus.COMPLETED else ReadingStatus.READING
                                    ),
                                    displayMode = ReaderDisplayMode.COMIC,
                                    comicPages = images,
                                    currentComicPage = safePage,
                                    comicScrollSyncToken = it.comicScrollSyncToken + 1,
                                    epubStructure = null,
                                    currentChapterIndex = 0,
                                    currentChapterOffsetPermille = 0,
                                    currentChapterBlocks = emptyList()
                                )
                            }
                            repo.persistReadingProgressAsync(
                                bookId = book.id,
                                unitIndex = safePage,
                                offset = 0,
                                totalUnits = images.size,
                                isTextFlow = false,
                                preserveCompleted = preserveCompleted
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "无法打开文件，请检查存储权限或文件完整性"
                    )
                }
            }
        }
    }

    fun switchDisplayMode(mode: ReaderDisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
    }

    fun onComicPageChanged(pageIndex: Int, fromUserScroll: Boolean = false) {
        val state = _uiState.value
        val total = state.comicPages.size
        if (total == 0) return
        val clamped = pageIndex.coerceIn(0, total - 1)
        if (clamped == state.currentComicPage && fromUserScroll) return

        val isCompletedNow = clamped >= total - 1
        _uiState.update { current ->
            current.copy(
                currentComicPage = clamped,
                comicScrollSyncToken = if (fromUserScroll) current.comicScrollSyncToken else current.comicScrollSyncToken + 1,
                book = current.book?.copy(
                    currentUnit = clamped,
                    currentOffset = 0,
                    readingStatus = if (isCompletedNow) ReadingStatus.COMPLETED else ReadingStatus.READING
                )
            )
        }
        val book = state.book ?: return
        if (state.displayMode == ReaderDisplayMode.COMIC) {
            repo.persistReadingProgressAsync(
                bookId = book.id,
                unitIndex = clamped,
                offset = 0,
                totalUnits = total,
                isTextFlow = false
            )
        }
    }

    fun selectEpubChapter(chapterIndex: Int) {
        val state = _uiState.value
        val chapters = state.epubStructure?.chapters ?: return
        if (chapters.isEmpty()) return
        val clamped = chapterIndex.coerceIn(0, chapters.size - 1)
        scrollSaveJob?.cancel()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isChapterLoading = true,
                    currentChapterIndex = clamped,
                    currentChapterOffsetPermille = 0,
                    chapterScrollRestoreToken = it.chapterScrollRestoreToken + 1,
                    book = it.book?.copy(
                        currentUnit = clamped,
                        currentOffset = 0,
                        readingStatus = ReadingStatus.READING
                    )
                )
            }
            val blocks = withContext(Dispatchers.IO) {
                sessionMutex.withLock {
                    if (activeTextChapters.isNotEmpty()) {
                        activeTextChapters.getOrNull(clamped)?.blocks.orEmpty()
                    } else {
                        activeSession?.let { EpubParser.parseChapterContent(it, chapters[clamped].entryPath) }
                            ?: emptyList()
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isChapterLoading = false,
                    currentChapterBlocks = blocks
                )
            }
            state.book?.let { book ->
                repo.persistReadingProgressAsync(
                    bookId = book.id,
                    unitIndex = clamped,
                    offset = 0,
                    totalUnits = chapters.size,
                    isTextFlow = true
                )
            }
        }
    }

    fun onChapterScrollProgressChanged(offsetPermille: Int) {
        val state = _uiState.value
        if (state.displayMode != ReaderDisplayMode.EPUB_TYPOGRAPHY || state.isChapterLoading) return
        val book = state.book ?: return
        val chapters = state.epubStructure?.chapters ?: return
        if (chapters.isEmpty()) return

        val clampedOffset = offsetPermille.coerceIn(0, 1000)
        val total = chapters.size
        val chapterIdx = state.currentChapterIndex.coerceIn(0, total - 1)
        val isCompleted = chapterIdx >= total - 1 && clampedOffset >= 960

        _uiState.update { current ->
            current.copy(
                currentChapterOffsetPermille = clampedOffset,
                book = current.book?.copy(
                    currentUnit = chapterIdx,
                    currentOffset = clampedOffset,
                    readingStatus = if (isCompleted) ReadingStatus.COMPLETED else ReadingStatus.READING
                )
            )
        }

        scrollSaveJob?.cancel()
        scrollSaveJob = viewModelScope.launch {
            delay(250L)
            repo.persistReadingProgressAsync(
                bookId = book.id,
                unitIndex = chapterIdx,
                offset = clampedOffset,
                totalUnits = total,
                isTextFlow = true
            )
        }
    }

    fun scrubSingleChapterToPermille(offsetPermille: Int) {
        val state = _uiState.value
        val book = state.book ?: return
        val total = (state.epubStructure?.chapters?.size ?: 1).coerceAtLeast(1)
        val clamped = offsetPermille.coerceIn(0, 1000)
        val isCompleted = state.currentChapterIndex >= total - 1 && clamped >= 960
        scrollSaveJob?.cancel()

        _uiState.update { current ->
            current.copy(
                currentChapterOffsetPermille = clamped,
                chapterScrollRestoreToken = current.chapterScrollRestoreToken + 1,
                book = current.book?.copy(
                    currentUnit = current.currentChapterIndex,
                    currentOffset = clamped,
                    readingStatus = if (isCompleted) ReadingStatus.COMPLETED else ReadingStatus.READING
                )
            )
        }
        repo.persistReadingProgressAsync(
            bookId = book.id,
            unitIndex = state.currentChapterIndex,
            offset = clamped,
            totalUnits = total,
            isTextFlow = true
        )
    }

    fun flushPendingProgress() {
        scrollSaveJob?.cancel()
        val state = _uiState.value
        val book = state.book ?: return
        if (state.isLoading || state.errorMessage != null) return

        if (state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY) {
            val total = (state.epubStructure?.chapters?.size ?: book.totalUnits).coerceAtLeast(1)
            repo.persistReadingProgressAsync(
                bookId = book.id,
                unitIndex = state.currentChapterIndex,
                offset = state.currentChapterOffsetPermille,
                totalUnits = total,
                isTextFlow = true
            )
        } else if (state.comicPages.isNotEmpty()) {
            repo.persistReadingProgressAsync(
                bookId = book.id,
                unitIndex = state.currentComicPage,
                offset = 0,
                totalUnits = state.comicPages.size,
                isTextFlow = false
            )
        }
    }

    suspend fun loadPageBitmap(entryPath: String, trimWhiteMargins: Boolean): Bitmap? {
        val cacheKey = "${entryPath}_trim=$trimWhiteMargins"
        bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return it }

        return withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                val decoded = if (entryPath.startsWith("pdf_page_")) {
                    val pageIdx = entryPath.removePrefix("pdf_page_").toIntOrNull()
                    val pdf = activePdfSession
                    if (pageIdx != null && pdf != null) {
                        pdf.renderPage(
                            pageIndex = pageIdx,
                            maxDimension = 2200,
                            trimWhiteMargins = trimWhiteMargins
                        )
                    } else null
                } else {
                    activeSession?.decodeImageEntry(
                        entryName = entryPath,
                        maxDimension = 2200,
                        trimWhiteMargins = trimWhiteMargins
                    )
                }
                if (decoded != null) {
                    bitmapCache.put(cacheKey, decoded)
                }
                decoded
            }
        }
    }

    fun clearBitmapCache() {
        bitmapCache.evictAll()
    }

    fun toggleCurrentBookmark() {
        val state = _uiState.value
        val book = state.book ?: return
        val unitIndex: Int
        val label: String
        if (state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY &&
            !state.epubStructure?.chapters.isNullOrEmpty()
        ) {
            unitIndex = state.currentChapterIndex
            val title = state.epubStructure?.chapters?.getOrNull(unitIndex)?.title ?: "第 ${unitIndex + 1} 章"
            label = title
        } else {
            unitIndex = state.currentComicPage
            label = "第 ${unitIndex + 1} 页"
        }

        viewModelScope.launch {
            repo.toggleBookmark(book.id, unitIndex, label, state.bookmarks)
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch { repo.deleteBookmark(bookmarkId) }
    }

    fun jumpToBookmark(bookmark: BookmarkEntity) {
        val state = _uiState.value
        if (state.displayMode == ReaderDisplayMode.EPUB_TYPOGRAPHY &&
            !state.epubStructure?.chapters.isNullOrEmpty()
        ) {
            selectEpubChapter(bookmark.unitIndex)
        } else {
            onComicPageChanged(bookmark.unitIndex)
        }
    }

    // Preference actions
    fun setComicDirection(direction: ComicReadingDirection) {
        viewModelScope.launch { prefsRepo.setComicReadingDirection(direction) }
    }

    fun toggleTrimWhiteMargins() {
        viewModelScope.launch {
            val next = !preferences.value.trimWhiteMargins
            clearBitmapCache()
            prefsRepo.setTrimWhiteMargins(next)
        }
    }

    fun toggleKeepScreenOn() {
        viewModelScope.launch {
            prefsRepo.setKeepScreenOn(!preferences.value.keepScreenOn)
        }
    }

    fun setReaderBrightness(value: Float) {
        viewModelScope.launch { prefsRepo.setReaderBrightness(value) }
    }

    fun setWarmthLevel(value: Float) {
        viewModelScope.launch { prefsRepo.setWarmthLevel(value) }
    }

    fun setEpubFontSize(size: Float) {
        viewModelScope.launch { prefsRepo.setEpubFontSizeSp(size) }
    }

    fun setEpubLineHeight(multiplier: Float) {
        viewModelScope.launch { prefsRepo.setEpubLineHeightMultiplier(multiplier) }
    }

    fun setEpubUseSerif(serif: Boolean) {
        viewModelScope.launch { prefsRepo.setEpubUseSerif(serif) }
    }

    fun setEpubTheme(theme: EpubReaderTheme) {
        viewModelScope.launch { prefsRepo.setEpubTheme(theme) }
    }

    fun getCurrentChapters(): List<EpubChapter> = _uiState.value.epubStructure?.chapters.orEmpty()

    override fun onCleared() {
        flushPendingProgress()
        super.onCleared()
        bookmarksJob?.cancel()
        runCatching { activeSession?.close() }
        runCatching { activePdfSession?.close() }
        activeTextChapters = emptyList()
        bitmapCache.evictAll()
    }
}
