package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TonalFolioApp
import com.example.data.local.entity.AuthorizedFolderEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookWithTags
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.SmartRuleConditionType
import com.example.data.local.entity.SmartRuleEntity
import com.example.data.local.entity.TagEntity
import com.example.data.local.entity.TagWithCount
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.BookshelfSortOrder
import com.example.data.preferences.ComicReadingDirection
import com.example.data.repository.ImportTask
import com.example.data.repository.StorageStatsProvider
import com.example.data.repository.StorageStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BookshelfFilter {
    data object All : BookshelfFilter
    data object Comics : BookshelfFilter
    data object Ebooks : BookshelfFilter
    data object Reading : BookshelfFilter
    data object Unread : BookshelfFilter
    data object Completed : BookshelfFilter
    data class ByTag(val tagId: Long, val tagName: String) : BookshelfFilter
    data class ByCategory(val category: String) : BookshelfFilter
}

data class LibraryUiState(
    val allBooks: List<BookWithTags> = emptyList(),
    val filteredBooks: List<BookWithTags> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val tagsWithCounts: List<TagWithCount> = emptyList(),
    val filteredTagGroups: Map<String, List<TagWithCount>> = emptyMap(),
    val smartRules: List<SmartRuleEntity> = emptyList(),
    val authorizedFolders: List<AuthorizedFolderEntity> = emptyList(),
    val importTasks: List<ImportTask> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val storageStatus: StorageStatus = StorageStatus(0L, 0L, 0),
    val bookshelfSearchQuery: String = "",
    val activeFilter: BookshelfFilter = BookshelfFilter.All,
    val tagSearchQuery: String = "",
    val isTagBatchMode: Boolean = false,
    val selectedBatchTagIds: Set<Long> = emptySet()
)

private data class FilterInputs(
    val bookshelfQuery: String,
    val filter: BookshelfFilter,
    val tagQuery: String,
    val batchMode: Boolean,
    val selectedBatchTags: Set<Long>,
    val storage: StorageStatus
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TonalFolioApp
    private val repo = app.libraryRepository
    private val prefsRepo = app.preferencesRepository

    private val bookshelfSearchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow<BookshelfFilter>(BookshelfFilter.All)
    private val tagSearchQuery = MutableStateFlow("")
    private val isTagBatchMode = MutableStateFlow(false)
    private val selectedBatchTagIds = MutableStateFlow<Set<Long>>(emptySet())
    private val storageStatus = MutableStateFlow(StorageStatsProvider.readStorageStatus(application))

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val filterInputs = combine(
        bookshelfSearchQuery,
        activeFilter,
        tagSearchQuery,
        isTagBatchMode,
        selectedBatchTagIds
    ) { bq, filter, tq, batch, selected ->
        FilterInputs(bq, filter, tq, batch, selected, storageStatus.value)
    }.combine(storageStatus) { inputs, storage ->
        inputs.copy(storage = storage)
    }

    private val repoBundle = combine(
        repo.allBooksWithTags,
        repo.tagsWithCounts,
        repo.smartRules,
        repo.authorizedFolders,
        repo.importTasks
    ) { books, tags, rules, folders, tasks ->
        RepoSnapshot(books, tags, rules, folders, tasks)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        repoBundle,
        prefsRepo.preferencesFlow,
        filterInputs
    ) { snapshot, prefs, inputs ->
        val filteredBooks = applyBookFiltersAndSort(
            books = snapshot.books,
            query = inputs.bookshelfQuery,
            filter = inputs.filter,
            sortOrder = prefs.sortOrder
        )
        val categories = snapshot.books
            .map { it.book.categoryGroup }
            .filter { it.isNotBlank() && it != "未归类" }
            .distinct()
            .sorted()

        val matchingTags = if (inputs.tagQuery.isBlank()) {
            snapshot.tags
        } else {
            val q = inputs.tagQuery.trim()
            snapshot.tags.filter {
                it.tag.name.contains(q, ignoreCase = true) ||
                    it.tag.categoryGroup.contains(q, ignoreCase = true)
            }
        }
        val groupedTags = matchingTags.groupBy { it.tag.categoryGroup }

        LibraryUiState(
            allBooks = snapshot.books,
            filteredBooks = filteredBooks,
            availableCategories = categories,
            tagsWithCounts = snapshot.tags,
            filteredTagGroups = groupedTags,
            smartRules = snapshot.rules,
            authorizedFolders = snapshot.folders,
            importTasks = snapshot.tasks,
            preferences = prefs,
            storageStatus = inputs.storage,
            bookshelfSearchQuery = inputs.bookshelfQuery,
            activeFilter = inputs.filter,
            tagSearchQuery = inputs.tagQuery,
            isTagBatchMode = inputs.batchMode,
            selectedBatchTagIds = inputs.selectedBatchTags
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    private fun applyBookFiltersAndSort(
        books: List<BookWithTags>,
        query: String,
        filter: BookshelfFilter,
        sortOrder: BookshelfSortOrder
    ): List<BookWithTags> {
        val afterFilter = books.filter { item ->
            val book = item.book
            when (filter) {
                BookshelfFilter.All -> true
                BookshelfFilter.Comics -> book.format == BookFormat.CBZ || book.format == BookFormat.ZIP
                BookshelfFilter.Ebooks -> book.format.isEbook
                BookshelfFilter.Reading -> book.readingStatus == ReadingStatus.READING
                BookshelfFilter.Unread -> book.readingStatus == ReadingStatus.UNREAD
                BookshelfFilter.Completed -> book.readingStatus == ReadingStatus.COMPLETED
                is BookshelfFilter.ByTag -> item.tags.any { it.id == filter.tagId }
                is BookshelfFilter.ByCategory -> book.categoryGroup == filter.category
            }
        }

        val afterQuery = if (query.isBlank()) {
            afterFilter
        } else {
            val q = query.trim()
            afterFilter.filter { item ->
                item.book.title.contains(q, ignoreCase = true) ||
                    item.book.author.contains(q, ignoreCase = true) ||
                    item.book.categoryGroup.contains(q, ignoreCase = true) ||
                    item.tags.any { it.name.contains(q, ignoreCase = true) }
            }
        }

        return when (sortOrder) {
            BookshelfSortOrder.RECENT_READ -> afterQuery.sortedByDescending {
                maxOf(it.book.lastReadAt, it.book.addedAt)
            }
            BookshelfSortOrder.ADDED_DESC -> afterQuery.sortedByDescending { it.book.addedAt }
            BookshelfSortOrder.TITLE_ASC -> afterQuery.sortedBy { it.book.title.lowercase() }
            BookshelfSortOrder.PROGRESS_DESC -> afterQuery.sortedByDescending { it.book.progressFraction }
        }
    }

    fun refreshStorageStats() {
        storageStatus.value = StorageStatsProvider.readStorageStatus(app)
    }

    fun updateBookshelfSearch(query: String) {
        bookshelfSearchQuery.value = query
    }

    fun selectBookshelfFilter(filter: BookshelfFilter) {
        activeFilter.value = filter
    }

    fun toggleGridView() {
        viewModelScope.launch {
            prefsRepo.setIsGridView(!uiState.value.preferences.isGridView)
        }
    }

    fun setSortOrder(order: BookshelfSortOrder) {
        viewModelScope.launch { prefsRepo.setSortOrder(order) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setUseDynamicColor(enabled) }
    }

    fun setDarkModeOverride(override: Boolean?) {
        viewModelScope.launch { prefsRepo.setDarkModeOverride(override) }
    }

    fun setDefaultComicDirection(direction: ComicReadingDirection) {
        viewModelScope.launch { prefsRepo.setComicReadingDirection(direction) }
    }

    fun setDefaultKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setKeepScreenOn(enabled) }
    }

    // Import & SAF
    fun importDocumentToInternalStorage(uri: Uri) {
        repo.importUris(listOf(uri), persistLocalCopy = true)
        refreshStorageStats()
        _toastEvents.tryEmit("已开始导入至应用内部存储")
    }

    fun importFiles(uris: List<Uri>, persistLocalCopy: Boolean = true) {
        if (uris.isEmpty()) return
        repo.importUris(uris, persistLocalCopy)
        refreshStorageStats()
    }

    fun scanFolder(treeUri: Uri) {
        repo.scanAuthorizedFolder(treeUri)
        refreshStorageStats()
        _toastEvents.tryEmit("已授权目录并加入后台扫描队列")
    }

    fun rescanFolder(folder: AuthorizedFolderEntity) {
        repo.rescanAuthorizedFolder(folder)
        _toastEvents.tryEmit("正在重新扫描「${folder.displayName}」")
    }

    fun removeAuthorizedFolder(folder: AuthorizedFolderEntity) {
        viewModelScope.launch {
            repo.removeAuthorizedFolder(folder)
            refreshStorageStats()
        }
    }

    fun retryImportTask(taskId: String) = repo.retryTask(taskId)
    fun clearFinishedImportTasks() = repo.clearFinishedTasks()

    fun setAutoCacheCovers(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoCacheCovers(enabled) }
    }

    fun setAutoTagByFilename(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoTagByFilename(enabled) }
    }

    fun setSkipDuplicates(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSkipDuplicates(enabled) }
    }

    // Book management
    fun updateBookMetadata(bookId: Long, title: String, author: String, categoryGroup: String) {
        viewModelScope.launch {
            repo.updateBookMetadata(bookId, title, author, categoryGroup)
            _toastEvents.tryEmit("已更新书籍信息")
        }
    }

    fun toggleBookTag(bookId: Long, tagId: Long, assign: Boolean) {
        viewModelScope.launch {
            repo.toggleTagForBook(bookId, tagId, assign)
        }
    }

    fun setBookStatus(book: BookEntity, status: ReadingStatus) {
        viewModelScope.launch {
            repo.setBookReadingStatus(book, status)
        }
    }

    fun toggleBookFavorite(book: BookEntity) {
        viewModelScope.launch {
            repo.toggleBookFavorite(book.id, !book.isFavorite)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repo.deleteBook(book)
            refreshStorageStats()
            _toastEvents.tryEmit("已从书架移除《${book.title}》")
        }
    }

    // Tags & Categories
    fun updateTagSearch(query: String) {
        tagSearchQuery.value = query
    }

    fun toggleTagBatchMode() {
        val next = !isTagBatchMode.value
        isTagBatchMode.value = next
        if (!next) selectedBatchTagIds.value = emptySet()
    }

    fun toggleBatchTagSelection(tagId: Long) {
        selectedBatchTagIds.update { current ->
            if (tagId in current) current - tagId else current + tagId
        }
    }

    fun createTag(name: String, categoryGroup: String, colorHex: Long) {
        viewModelScope.launch {
            val created = repo.createTag(name, categoryGroup, colorHex)
            _toastEvents.tryEmit(if (created) "已创建标签「${name.trim()}」" else "标签名称已存在或无效")
        }
    }

    fun updateTag(tag: TagEntity) {
        viewModelScope.launch {
            repo.updateTag(tag)
            _toastEvents.tryEmit("已更新标签「${tag.name}」")
        }
    }

    fun deleteTags(tagIds: List<Long>) {
        viewModelScope.launch {
            repo.deleteTags(tagIds)
            selectedBatchTagIds.value = emptySet()
            isTagBatchMode.value = false
            _toastEvents.tryEmit("已删除选中标签")
        }
    }

    fun mergeSelectedTagsInto(targetTagId: Long) {
        val selected = selectedBatchTagIds.value.toList()
        if (selected.size < 2) return
        viewModelScope.launch {
            repo.mergeTags(selected, targetTagId)
            selectedBatchTagIds.value = emptySet()
            isTagBatchMode.value = false
            _toastEvents.tryEmit("已完成标签合并")
        }
    }

    fun moveSelectedTagsToCategory(categoryGroup: String) {
        val selected = selectedBatchTagIds.value.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repo.moveTagsToCategory(selected, categoryGroup)
            selectedBatchTagIds.value = emptySet()
            isTagBatchMode.value = false
            _toastEvents.tryEmit("已移动至「$categoryGroup」")
        }
    }

    // Smart rules
    fun saveSmartRule(
        existingId: Long = 0L,
        name: String,
        description: String,
        conditionType: SmartRuleConditionType,
        conditionValue: String,
        targetTagName: String,
        targetCategoryGroup: String
    ) {
        viewModelScope.launch {
            repo.saveSmartRule(
                SmartRuleEntity(
                    id = existingId,
                    name = name.trim(),
                    description = description.trim().ifBlank { "${conditionType.label}: $conditionValue" },
                    conditionType = conditionType,
                    conditionValue = conditionValue.trim(),
                    targetTagName = targetTagName.trim(),
                    targetCategoryGroup = targetCategoryGroup.trim(),
                    isEnabled = true
                )
            )
            _toastEvents.tryEmit("已保存智能归类规则")
        }
    }

    fun setSmartRuleEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repo.setSmartRuleEnabled(ruleId, enabled)
        }
    }

    fun deleteSmartRule(ruleId: Long) {
        viewModelScope.launch {
            repo.deleteSmartRule(ruleId)
        }
    }

    fun applySmartRulesNow() {
        viewModelScope.launch {
            val count = repo.applySmartRulesToAllBooks()
            _toastEvents.tryEmit(
                if (count > 0) "已按生效规则自动整理 $count 本书籍" else "所有书籍均已符合当前归类规则"
            )
        }
    }

    private data class RepoSnapshot(
        val books: List<BookWithTags>,
        val tags: List<TagWithCount>,
        val rules: List<SmartRuleEntity>,
        val folders: List<AuthorizedFolderEntity>,
        val tasks: List<ImportTask>
    )
}
