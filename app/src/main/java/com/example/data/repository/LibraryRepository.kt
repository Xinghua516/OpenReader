package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.dao.LibraryDao
import com.example.data.local.entity.AuthorizedFolderEntity
import com.example.data.local.entity.BookCollectionCrossRef
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.BookWithTags
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionWithCount
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.SmartRuleConditionType
import com.example.data.local.entity.SmartRuleEntity
import com.example.data.local.entity.TagEntity
import com.example.data.local.entity.TagWithCount
import com.example.data.preferences.AppPreferencesRepository
import com.example.parser.BookImporterParser
import com.example.parser.FilenameMetadataParser
import com.example.ui.theme.TagPresetColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class ImportTaskStatus {
    QUEUED,
    PARSING,
    COMPLETED,
    SKIPPED_DUPLICATE,
    FAILED
}

data class ImportTask(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val format: BookFormat?,
    val status: ImportTaskStatus,
    val progress: Float = 0f,
    val detail: String = "",
    val matchedTags: List<String> = emptyList(),
    val createdBookId: Long? = null
)

class LibraryRepository(
    private val context: Context,
    private val dao: LibraryDao,
    private val prefsRepo: AppPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allBooksWithTags: Flow<List<BookWithTags>> = dao.observeAllBooksWithTags()
    val tagsWithCounts: Flow<List<TagWithCount>> = dao.observeTagsWithCounts()
    val collectionsWithCounts: Flow<List<CollectionWithCount>> = dao.observeCollectionsWithCounts()
    val smartRules: Flow<List<SmartRuleEntity>> = dao.observeSmartRules()
    val authorizedFolders: Flow<List<AuthorizedFolderEntity>> = dao.observeAuthorizedFolders()

    private val _importTasks = MutableStateFlow<List<ImportTask>>(emptyList())
    val importTasks: StateFlow<List<ImportTask>> = _importTasks.asStateFlow()

    fun observeBookWithTags(bookId: Long): Flow<BookWithTags?> = dao.observeBookWithTags(bookId)
    fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>> = dao.observeBookmarksForBook(bookId)

    fun importUris(uris: List<Uri>, persistLocalCopy: Boolean = false) {
        if (uris.isEmpty()) return
        val newTasks = uris.map { uri ->
            val name = BookImporterParser.queryDisplayName(context, uri)
            val mime = context.contentResolver.getType(uri)
            ImportTask(
                uri = uri,
                fileName = name,
                format = FilenameMetadataParser.detectFormatByName(name, mime),
                status = ImportTaskStatus.QUEUED,
                progress = 0f,
                detail = "等待解析队列处理"
            )
        }
        _importTasks.update { current -> newTasks + current }

        scope.launch {
            for (task in newTasks) {
                processImportTask(task, persistLocalCopy)
            }
        }
    }

    suspend fun importUriToInternalStorageSync(uri: Uri): ImportTask {
        val name = BookImporterParser.queryDisplayName(context, uri)
        val mime = context.contentResolver.getType(uri)
        val task = ImportTask(
            uri = uri,
            fileName = name,
            format = FilenameMetadataParser.detectFormatByName(name, mime),
            status = ImportTaskStatus.QUEUED,
            progress = 0f,
            detail = "正在导入至内部存储…"
        )
        _importTasks.update { current -> listOf(task) + current }
        processImportTask(task, persistLocalCopy = true)
        return _importTasks.value.firstOrNull { it.id == task.id } ?: task
    }

    fun retryTask(taskId: String) {
        val task = _importTasks.value.firstOrNull { it.id == taskId } ?: return
        scope.launch {
            processImportTask(task, persistLocalCopy = false)
        }
    }

    fun clearFinishedTasks() {
        _importTasks.update { list ->
            list.filter { it.status == ImportTaskStatus.QUEUED || it.status == ImportTaskStatus.PARSING }
        }
    }

    fun scanAuthorizedFolder(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        scope.launch {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val displayName = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "已授权目录" }
            val foundUris = collectSupportedDocumentsInTree(treeUri, docId)

            dao.upsertAuthorizedFolder(
                AuthorizedFolderEntity(
                    uri = treeUri.toString(),
                    displayName = displayName,
                    lastScannedAt = System.currentTimeMillis(),
                    bookCount = foundUris.size
                )
            )

            if (foundUris.isNotEmpty()) {
                importUris(foundUris, persistLocalCopy = false)
            }
        }
    }

    fun rescanAuthorizedFolder(folder: AuthorizedFolderEntity) {
        val uri = Uri.parse(folder.uri)
        scanAuthorizedFolder(uri)
    }

    suspend fun removeAuthorizedFolder(folder: AuthorizedFolderEntity) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        dao.deleteAuthorizedFolderByUri(folder.uri)
    }

    private fun collectSupportedDocumentsInTree(treeUri: Uri, rootDocId: String): List<Uri> {
        val result = mutableListOf<Uri>()
        val stack = ArrayDeque<String>()
        stack.addLast(rootDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (stack.isNotEmpty()) {
            val currentDocId = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.addLast(childId)
                        } else if (FilenameMetadataParser.detectFormatByName(name, mime) != null) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            result.add(docUri)
                        }
                    }
                }
            }
        }
        return result
    }

    private suspend fun processImportTask(task: ImportTask, persistLocalCopy: Boolean) {
        val uri = task.uri
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        updateTask(task.id) {
            it.copy(status = ImportTaskStatus.PARSING, progress = 0.08f, detail = "正在准备解析…")
        }

        val prefs = prefsRepo.preferencesFlow.first()

        try {
            val hasPersistedPerm = runCatching {
                context.contentResolver.persistedUriPermissions.any { it.uri == uri }
            }.getOrDefault(false)
            val shouldCopyInternally = persistLocalCopy || !hasPersistedPerm

            val parsed = BookImporterParser.parseUri(
                context = context,
                uri = uri,
                autoCacheCover = prefs.autoCacheCovers,
                autoTagByFilename = prefs.autoTagByFilename,
                copyToInternalStorage = shouldCopyInternally
            ) { progress, detail ->
                updateTask(task.id) {
                    it.copy(progress = progress, detail = detail)
                }
            }

            if (prefs.skipDuplicates) {
                val existing = dao.findBookByHash(parsed.fileHash)
                if (existing != null) {
                    if (existing.localCopyPath != parsed.localCopyPath && parsed.localCopyPath != null) {
                        // Keep existing book's internal copy intact; avoid deleting if paths match
                        if (existing.localCopyPath != null) {
                            File(parsed.localCopyPath).takeIf { it.exists() }?.delete()
                        }
                    }
                    updateTask(task.id) {
                        it.copy(
                            status = ImportTaskStatus.SKIPPED_DUPLICATE,
                            progress = 1f,
                            format = parsed.format,
                            detail = "已跳过重复文件（与《${existing.title}》哈希一致）",
                            createdBookId = existing.id
                        )
                    }
                    return
                }
            }

            val localCopyPath = parsed.localCopyPath

            val enabledRules = dao.getEnabledSmartRulesOnce()
            val (ruleTags, ruleCategory) = evaluateSmartRules(
                title = parsed.title,
                rawFileName = parsed.rawFileName,
                author = parsed.author,
                format = parsed.format,
                rules = enabledRules
            )

            val defaultCategory = when {
                ruleCategory != null -> ruleCategory
                parsed.format.isEbook -> "电子书"
                else -> "漫画"
            }

            val existingByUri = dao.findBookBySourceUri(uri.toString())
            val bookEntity = BookEntity(
                id = existingByUri?.id ?: 0,
                title = parsed.title,
                author = parsed.author,
                format = parsed.format,
                sourceUri = uri.toString(),
                localCopyPath = localCopyPath ?: existingByUri?.localCopyPath,
                fileHash = parsed.fileHash,
                fileSizeBytes = parsed.fileSizeBytes,
                coverPath = parsed.coverPath ?: existingByUri?.coverPath,
                totalUnits = parsed.totalUnits,
                currentUnit = existingByUri?.currentUnit ?: 0,
                currentOffset = existingByUri?.currentOffset ?: 0,
                readingStatus = existingByUri?.readingStatus ?: ReadingStatus.UNREAD,
                categoryGroup = defaultCategory,
                isFavorite = existingByUri?.isFavorite ?: false,
                addedAt = existingByUri?.addedAt ?: System.currentTimeMillis(),
                lastReadAt = existingByUri?.lastReadAt ?: 0L
            )

            val bookId = dao.insertBook(bookEntity)
            val allTagNames = (parsed.suggestedTags + ruleTags).distinct()
            attachTagsByNames(bookId, allTagNames, defaultCategory)

            updateTask(task.id) {
                it.copy(
                    status = ImportTaskStatus.COMPLETED,
                    progress = 1f,
                    format = parsed.format,
                    detail = parsed.statusDetail,
                    matchedTags = allTagNames,
                    createdBookId = bookId
                )
            }
        } catch (e: Exception) {
            updateTask(task.id) {
                it.copy(
                    status = ImportTaskStatus.FAILED,
                    progress = 0f,
                    detail = e.message ?: "解析失败，请确认文件未损坏"
                )
            }
        }
    }

    private fun updateTask(taskId: String, transform: (ImportTask) -> ImportTask) {
        _importTasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }

    private fun evaluateSmartRules(
        title: String,
        rawFileName: String,
        author: String,
        format: BookFormat,
        rules: List<SmartRuleEntity>
    ): Pair<List<String>, String?> {
        val matchedTags = mutableListOf<String>()
        var matchedCategory: String? = null

        for (rule in rules) {
            val tokens = rule.conditionValue.split(',', '，', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val matched = when (rule.conditionType) {
                SmartRuleConditionType.FORMAT_EQUALS -> {
                    tokens.any { it.equals(format.name, ignoreCase = true) }
                }
                SmartRuleConditionType.TITLE_CONTAINS -> {
                    tokens.any { token ->
                        title.contains(token, ignoreCase = true) ||
                            rawFileName.contains(token, ignoreCase = true)
                    }
                }
                SmartRuleConditionType.AUTHOR_CONTAINS -> {
                    tokens.any { token -> author.contains(token, ignoreCase = true) }
                }
            }

            if (matched) {
                if (rule.targetTagName.isNotBlank()) {
                    matchedTags.add(rule.targetTagName.trim())
                }
                if (matchedCategory == null && rule.targetCategoryGroup.isNotBlank()) {
                    matchedCategory = rule.targetCategoryGroup.trim()
                }
            }
        }
        return matchedTags.distinct() to matchedCategory
    }

    private suspend fun attachTagsByNames(
        bookId: Long,
        tagNames: List<String>,
        fallbackCategoryGroup: String
    ) {
        for (name in tagNames) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) continue
            var tag = dao.findTagByName(trimmed)
            if (tag == null) {
                val category = when (trimmed) {
                    "EPUB", "ZIP/CBZ", "PDF", "TXT", "Markdown" -> "格式与载体"
                    "已完结", "在读", "未读" -> "阅读状态"
                    else -> if (fallbackCategoryGroup.isNotBlank() && fallbackCategoryGroup != "漫画" && fallbackCategoryGroup != "电子书") {
                        "题材分类"
                    } else {
                        "题材分类"
                    }
                }
                val color = TagPresetColors[kotlin.math.abs(trimmed.hashCode()) % TagPresetColors.size]
                val newId = dao.insertTag(
                    TagEntity(
                        name = trimmed,
                        categoryGroup = category,
                        colorHex = color
                    )
                )
                tag = if (newId > 0) {
                    dao.findTagByName(trimmed)
                } else {
                    dao.findTagByName(trimmed)
                }
            }
            if (tag != null) {
                dao.insertBookTagCrossRef(BookTagCrossRef(bookId = bookId, tagId = tag.id))
            }
        }
    }

    suspend fun applySmartRulesToAllBooks(): Int = withContext(Dispatchers.IO) {
        val rules = dao.getEnabledSmartRulesOnce()
        val books = dao.getAllBooksWithTagsOnce()
        var updatedCount = 0
        for (item in books) {
            val book = item.book
            val (ruleTags, ruleCategory) = evaluateSmartRules(
                title = book.title,
                rawFileName = book.sourceUri,
                author = book.author,
                format = book.format,
                rules = rules
            )
            if (ruleTags.isNotEmpty() || (ruleCategory != null && ruleCategory != book.categoryGroup)) {
                attachTagsByNames(book.id, ruleTags, ruleCategory ?: book.categoryGroup)
                if (ruleCategory != null && ruleCategory != book.categoryGroup) {
                    dao.updateBookCategory(book.id, ruleCategory)
                }
                updatedCount++
            }
        }
        updatedCount
    }

    suspend fun updateReadingProgress(
        bookId: Long,
        unitIndex: Int,
        offset: Int,
        totalUnits: Int,
        isTextFlow: Boolean = false,
        preserveCompleted: Boolean = false
    ) {
        val safeTotal = totalUnits.coerceAtLeast(1)
        val clampedUnit = unitIndex.coerceIn(0, safeTotal - 1)
        val clampedOffset = offset.coerceIn(0, 1000)
        val reachedEnd = if (isTextFlow) {
            clampedUnit >= safeTotal - 1 && clampedOffset >= 960
        } else {
            clampedUnit >= safeTotal - 1 && (safeTotal > 1 || clampedOffset > 0 || preserveCompleted)
        }
        val status = when {
            reachedEnd -> ReadingStatus.COMPLETED
            preserveCompleted && clampedUnit >= safeTotal - 1 -> ReadingStatus.COMPLETED
            else -> ReadingStatus.READING
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            dao.updateReadingProgress(
                bookId = bookId,
                unit = clampedUnit,
                offset = clampedOffset,
                status = status,
                lastReadAt = System.currentTimeMillis()
            )
        }
    }

    fun persistReadingProgressAsync(
        bookId: Long,
        unitIndex: Int,
        offset: Int,
        totalUnits: Int,
        isTextFlow: Boolean,
        preserveCompleted: Boolean = false
    ) {
        scope.launch {
            updateReadingProgress(
                bookId = bookId,
                unitIndex = unitIndex,
                offset = offset,
                totalUnits = totalUnits,
                isTextFlow = isTextFlow,
                preserveCompleted = preserveCompleted
            )
        }
    }

    suspend fun setBookReadingStatus(book: BookEntity, status: ReadingStatus) {
        val newUnit = when (status) {
            ReadingStatus.UNREAD -> 0
            ReadingStatus.COMPLETED -> (book.totalUnits - 1).coerceAtLeast(0)
            ReadingStatus.READING -> book.currentUnit
        }
        dao.updateReadingProgress(
            bookId = book.id,
            unit = newUnit,
            offset = if (status == ReadingStatus.UNREAD) 0 else book.currentOffset,
            status = status,
            lastReadAt = System.currentTimeMillis()
        )
    }

    suspend fun updateBookMetadata(bookId: Long, title: String, author: String, categoryGroup: String) {
        dao.updateBookMetadata(bookId, title.trim(), author.trim(), categoryGroup.trim().ifBlank { "未归类" })
    }

    suspend fun toggleBookFavorite(bookId: Long, isFavorite: Boolean) {
        dao.updateBookFavorite(bookId, isFavorite)
    }

    suspend fun toggleTagForBook(bookId: Long, tagId: Long, assign: Boolean) {
        if (assign) {
            dao.insertBookTagCrossRef(BookTagCrossRef(bookId = bookId, tagId = tagId))
        } else {
            dao.deleteBookTagCrossRef(bookId = bookId, tagId = tagId)
        }
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        deleteInternalFileSafely(book.coverPath)
        deleteInternalFileSafely(book.localCopyPath)
        dao.deleteBookById(book.id)
    }

    suspend fun deleteBooks(books: List<BookEntity>) = withContext(Dispatchers.IO) {
        if (books.isEmpty()) return@withContext
        books.forEach { book ->
            deleteInternalFileSafely(book.coverPath)
            deleteInternalFileSafely(book.localCopyPath)
        }
        dao.deleteBooksByIds(books.map { it.id })
    }

    private fun deleteInternalFileSafely(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val target = File(path).canonicalFile
            val internalFilesRoot = context.filesDir.canonicalPath
            val internalCacheRoot = context.cacheDir.canonicalPath
            if (target.path.startsWith(internalFilesRoot) || target.path.startsWith(internalCacheRoot)) {
                if (target.exists()) {
                    target.delete()
                }
            }
        }
    }

    // Tag CRUD
    suspend fun createTag(name: String, categoryGroup: String, colorHex: Long): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val id = dao.insertTag(
            TagEntity(
                name = trimmed,
                categoryGroup = categoryGroup.trim().ifBlank { "题材分类" },
                colorHex = colorHex
            )
        )
        return id > 0
    }

    suspend fun updateTag(tag: TagEntity) {
        dao.updateTag(tag.copy(name = tag.name.trim(), categoryGroup = tag.categoryGroup.trim().ifBlank { "题材分类" }))
    }

    suspend fun deleteTags(tagIds: List<Long>) {
        if (tagIds.isNotEmpty()) dao.deleteTagsByIds(tagIds)
    }

    suspend fun mergeTags(sourceTagIds: List<Long>, targetTagId: Long) {
        dao.mergeTags(sourceTagIds, targetTagId)
    }

    suspend fun moveTagsToCategory(tagIds: List<Long>, categoryGroup: String) {
        if (tagIds.isNotEmpty()) {
            dao.updateTagsCategoryGroup(tagIds, categoryGroup.trim().ifBlank { "题材分类" })
        }
    }

    // Smart rule CRUD
    suspend fun saveSmartRule(rule: SmartRuleEntity) {
        dao.insertSmartRule(rule)
    }

    suspend fun setSmartRuleEnabled(ruleId: Long, enabled: Boolean) {
        dao.setSmartRuleEnabled(ruleId, enabled)
    }

    suspend fun deleteSmartRule(ruleId: Long) {
        dao.deleteSmartRule(ruleId)
    }

    // Bookmarks
    suspend fun toggleBookmark(bookId: Long, unitIndex: Int, label: String, existingBookmarks: List<BookmarkEntity>) {
        val existing = existingBookmarks.firstOrNull { it.unitIndex == unitIndex }
        if (existing != null) {
            dao.deleteBookmarkById(existing.id)
        } else {
            dao.insertBookmark(
                BookmarkEntity(
                    bookId = bookId,
                    unitIndex = unitIndex,
                    label = label
                )
            )
        }
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        dao.deleteBookmarkById(bookmarkId)
    }

    // Collections (custom named folders)
    suspend fun createCollection(
        name: String,
        description: String = "",
        colorHex: Long = TagPresetColors.first()
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val existing = dao.findCollectionByName(trimmed)
        if (existing != null) return -1L
        return dao.insertCollection(
            CollectionEntity(
                name = trimmed,
                description = description.trim(),
                colorHex = colorHex
            )
        )
    }

    suspend fun updateCollection(collection: CollectionEntity): Boolean {
        val trimmed = collection.name.trim()
        if (trimmed.isEmpty()) return false
        val conflict = dao.findCollectionByName(trimmed)
        if (conflict != null && conflict.id != collection.id) return false
        dao.updateCollection(
            collection.copy(
                name = trimmed,
                description = collection.description.trim()
            )
        )
        return true
    }

    suspend fun deleteCollection(collectionId: Long) {
        dao.deleteCollectionById(collectionId)
    }

    suspend fun toggleBookInCollection(bookId: Long, collectionId: Long, assign: Boolean) {
        if (assign) {
            dao.insertBookCollectionCrossRef(
                BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
            )
        } else {
            dao.deleteBookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
        }
    }

    suspend fun setBooksInCollection(collectionId: Long, bookIds: Set<Long>) {
        dao.setBooksInCollection(collectionId = collectionId, bookIds = bookIds)
    }

    suspend fun addBooksToCollection(collectionId: Long, bookIds: Set<Long>) {
        bookIds.forEach { bookId ->
            dao.insertBookCollectionCrossRef(
                BookCollectionCrossRef(bookId = bookId, collectionId = collectionId)
            )
        }
    }
}
