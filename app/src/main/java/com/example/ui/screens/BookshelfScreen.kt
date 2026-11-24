package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookWithTags
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionWithCount
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.TagWithCount
import com.example.data.preferences.BookshelfSortOrder
import com.example.ui.components.BookCoverArtwork
import com.example.ui.components.TonalSearchBar
import com.example.ui.components.TonalTopBar
import com.example.ui.viewmodel.BookshelfFilter
import com.example.ui.viewmodel.LibraryUiState

@Composable
fun BookshelfScreen(
    uiState: LibraryUiState,
    onSearchChange: (String) -> Unit,
    onSelectFilter: (BookshelfFilter) -> Unit,
    onToggleGridView: () -> Unit,
    onSelectSortOrder: (BookshelfSortOrder) -> Unit,
    onOpenBook: (Long) -> Unit,
    onQuickImportFiles: () -> Unit,
    onNavigateToImportCenter: () -> Unit,
    onOpenProfileOverview: () -> Unit,
    onUpdateBookMetadata: (Long, String, String, String) -> Unit,
    onToggleBookTag: (Long, Long, Boolean) -> Unit,
    onToggleBookCollection: (Long, Long, Boolean) -> Unit,
    onCreateCollection: (String, String, Long, Set<Long>) -> Unit,
    onUpdateCollection: (CollectionEntity) -> Unit,
    onDeleteCollection: (CollectionEntity) -> Unit,
    onSetBooksInCollection: (Long, Set<Long>) -> Unit,
    onAddBooksToCollection: (Long, String, Set<Long>) -> Unit,
    onSetBookStatus: (BookEntity, ReadingStatus) -> Unit,
    onToggleBookFavorite: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var managingBook by remember { mutableStateOf<BookWithTags?>(null) }
    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var createCollectionInitialBookIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var editingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var deletingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var managingBooksCollection by remember { mutableStateOf<CollectionEntity?>(null) }

    var hasUserToggledCollections by rememberSaveable { mutableStateOf(false) }
    var isCollectionsExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.collectionsWithCounts.size) {
        if (!hasUserToggledCollections) {
            isCollectionsExpanded = uiState.collectionsWithCounts.isNotEmpty()
        }
    }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchCollectionPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedBookIds = emptySet()
    }

    // Keep managingBook synced with latest state
    val activeManagingBook = managingBook?.let { current ->
        uiState.allBooks.firstOrNull { it.book.id == current.book.id }
    }
    val activeCollectionFilter = uiState.activeFilter as? BookshelfFilter.ByCollection
    val activeCollectionWithCount = activeCollectionFilter?.let { filter ->
        uiState.collectionsWithCounts.firstOrNull { it.collection.id == filter.collectionId }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TonalTopBar(
                title = "书架",
                onProfileClick = onOpenProfileOverview
            )

            // Search + Multi-select + Layout Toggle + Sort Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TonalSearchBar(
                    query = uiState.bookshelfSearchQuery,
                    onQueryChange = onSearchChange,
                    placeholder = "搜索书名、作者、合集或标签…",
                    modifier = Modifier.weight(1f),
                    testTag = "bookshelf_search_input"
                )

                if (uiState.allBooks.isNotEmpty()) {
                    Surface(
                        onClick = {
                            val next = !isMultiSelectMode
                            isMultiSelectMode = next
                            if (!next) selectedBookIds = emptySet()
                        },
                        shape = CircleShape,
                        color = if (isMultiSelectMode) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("bookshelf_multiselect_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Checklist,
                                contentDescription = if (isMultiSelectMode) "退出多选整理" else "多选加入合集",
                                tint = if (isMultiSelectMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Surface(
                    onClick = onToggleGridView,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_layout_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.preferences.isGridView) {
                                Icons.AutoMirrored.Outlined.ViewList
                            } else {
                                Icons.Outlined.GridView
                            },
                            contentDescription = "切换网格或列表视图",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    Surface(
                        onClick = { sortMenuExpanded = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("sort_menu_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "排序方式",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        BookshelfSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                trailingIcon = {
                                    if (uiState.preferences.sortOrder == order) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    onSelectSortOrder(order)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Filter Chips matching Stitch UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShelfFilterChip(
                    selected = uiState.activeFilter is BookshelfFilter.All,
                    label = "全部 (${uiState.allBooks.size})",
                    onClick = { onSelectFilter(BookshelfFilter.All) }
                )
                ShelfFilterChip(
                    selected = uiState.activeFilter is BookshelfFilter.Comics,
                    label = "漫画 ZIP/CBZ",
                    onClick = { onSelectFilter(BookshelfFilter.Comics) }
                )
                ShelfFilterChip(
                    selected = uiState.activeFilter is BookshelfFilter.Ebooks,
                    label = "电子书",
                    onClick = { onSelectFilter(BookshelfFilter.Ebooks) }
                )
                ShelfFilterChip(
                    selected = uiState.activeFilter is BookshelfFilter.Reading,
                    label = "在读",
                    onClick = { onSelectFilter(BookshelfFilter.Reading) }
                )
                ShelfFilterChip(
                    selected = uiState.activeFilter is BookshelfFilter.Unread,
                    label = "待看",
                    onClick = { onSelectFilter(BookshelfFilter.Unread) }
                )
                ShelfFilterChip(
                    selected = uiState.activeFilter is BookshelfFilter.Completed,
                    label = "已完结",
                    onClick = { onSelectFilter(BookshelfFilter.Completed) }
                )

                uiState.availableCategories.forEach { category ->
                    val isSelected = (uiState.activeFilter as? BookshelfFilter.ByCategory)?.category == category
                    ShelfFilterChip(
                        selected = isSelected,
                        label = category,
                        onClick = { onSelectFilter(BookshelfFilter.ByCategory(category)) }
                    )
                }

                uiState.tagsWithCounts.filter { it.bookCount > 0 }.forEach { tagWithCount ->
                    val isSelected = (uiState.activeFilter as? BookshelfFilter.ByTag)?.tagId == tagWithCount.tag.id
                    ShelfFilterChip(
                        selected = isSelected,
                        label = "#${tagWithCount.tag.name}",
                        onClick = {
                            onSelectFilter(BookshelfFilter.ByTag(tagWithCount.tag.id, tagWithCount.tag.name))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Library Body — Collections & QuickSort scroll together inside the grid/list!
            if (uiState.allBooks.isEmpty()) {
                EmptyBookshelfState(
                    onPickLocalFiles = onQuickImportFiles,
                    onGoImportCenter = onNavigateToImportCenter,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.preferences.isGridView) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val adaptiveCellMinWidth = when {
                        maxWidth >= 840.dp -> 156.dp
                        maxWidth >= 600.dp -> 136.dp
                        else -> 108.dp
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptiveCellMinWidth),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 104.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("bookshelf_grid")
                    ) {
                        item(key = "collections_section", span = { GridItemSpan(maxLineSpan) }) {
                            if (activeCollectionWithCount != null) {
                                ActiveCollectionHeaderCard(
                                    collectionWithCount = activeCollectionWithCount,
                                    allCollections = uiState.collectionsWithCounts,
                                    onSelectCollection = { col ->
                                        onSelectFilter(BookshelfFilter.ByCollection(col.id, col.name))
                                    },
                                    onManageBooks = { managingBooksCollection = activeCollectionWithCount.collection },
                                    onEditCollection = { editingCollection = activeCollectionWithCount.collection },
                                    onDeleteCollection = { deletingCollection = activeCollectionWithCount.collection },
                                    onExitCollection = { onSelectFilter(BookshelfFilter.All) }
                                )
                            } else {
                                CollapsibleCollectionsSection(
                                    collections = uiState.collectionsWithCounts,
                                    allBooks = uiState.allBooks,
                                    expanded = isCollectionsExpanded,
                                    onToggleExpanded = {
                                        hasUserToggledCollections = true
                                        isCollectionsExpanded = !isCollectionsExpanded
                                    },
                                    onSelectCollection = { col ->
                                        onSelectFilter(BookshelfFilter.ByCollection(col.id, col.name))
                                    },
                                    onCreateCollectionClick = {
                                        createCollectionInitialBookIds = emptySet()
                                        showCreateCollectionDialog = true
                                    },
                                    onManageBooksInCollection = { col ->
                                        managingBooksCollection = col
                                    },
                                    onEditCollectionClick = { col ->
                                        editingCollection = col
                                    },
                                    onDeleteCollectionClick = { col ->
                                        deletingCollection = col
                                    }
                                )
                            }
                        }

                        item(key = "quick_sort_bar", span = { GridItemSpan(maxLineSpan) }) {
                            BookshelfQuickSortBar(
                                totalCount = uiState.allBooks.size,
                                filteredCount = uiState.filteredBooks.size,
                                currentSort = uiState.preferences.sortOrder,
                                onSelectSortOrder = onSelectSortOrder,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.filteredBooks.isEmpty()) {
                            item(key = "empty_filtered_state", span = { GridItemSpan(maxLineSpan) }) {
                                if (activeCollectionWithCount != null) {
                                    EmptyCollectionState(
                                        collectionName = activeCollectionWithCount.collection.name,
                                        onAddBooksClick = { managingBooksCollection = activeCollectionWithCount.collection },
                                        onExitCollection = { onSelectFilter(BookshelfFilter.All) }
                                    )
                                } else {
                                    EmptyFilteredBooksState(
                                        onResetFilter = {
                                            onSearchChange("")
                                            onSelectFilter(BookshelfFilter.All)
                                        }
                                    )
                                }
                            }
                        } else {
                            items(uiState.filteredBooks, key = { it.book.id }) { item ->
                                val selected = item.book.id in selectedBookIds
                                BookGridCard(
                                    item = item,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = selected,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedBookIds = if (selected) {
                                                selectedBookIds - item.book.id
                                            } else {
                                                selectedBookIds + item.book.id
                                            }
                                        } else {
                                            onOpenBook(item.book.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (isMultiSelectMode) {
                                            selectedBookIds = if (selected) {
                                                selectedBookIds - item.book.id
                                            } else {
                                                selectedBookIds + item.book.id
                                            }
                                        } else {
                                            managingBook = item
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 104.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "collections_section") {
                        if (activeCollectionWithCount != null) {
                            ActiveCollectionHeaderCard(
                                collectionWithCount = activeCollectionWithCount,
                                allCollections = uiState.collectionsWithCounts,
                                onSelectCollection = { col ->
                                    onSelectFilter(BookshelfFilter.ByCollection(col.id, col.name))
                                },
                                onManageBooks = { managingBooksCollection = activeCollectionWithCount.collection },
                                onEditCollection = { editingCollection = activeCollectionWithCount.collection },
                                onDeleteCollection = { deletingCollection = activeCollectionWithCount.collection },
                                onExitCollection = { onSelectFilter(BookshelfFilter.All) }
                            )
                        } else {
                            CollapsibleCollectionsSection(
                                collections = uiState.collectionsWithCounts,
                                allBooks = uiState.allBooks,
                                expanded = isCollectionsExpanded,
                                onToggleExpanded = {
                                    hasUserToggledCollections = true
                                    isCollectionsExpanded = !isCollectionsExpanded
                                },
                                onSelectCollection = { col ->
                                    onSelectFilter(BookshelfFilter.ByCollection(col.id, col.name))
                                },
                                onCreateCollectionClick = {
                                    createCollectionInitialBookIds = emptySet()
                                    showCreateCollectionDialog = true
                                },
                                onManageBooksInCollection = { col ->
                                    managingBooksCollection = col
                                },
                                onEditCollectionClick = { col ->
                                    editingCollection = col
                                },
                                onDeleteCollectionClick = { col ->
                                    deletingCollection = col
                                }
                            )
                        }
                    }

                    item(key = "quick_sort_bar") {
                        BookshelfQuickSortBar(
                            totalCount = uiState.allBooks.size,
                            filteredCount = uiState.filteredBooks.size,
                            currentSort = uiState.preferences.sortOrder,
                            onSelectSortOrder = onSelectSortOrder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.filteredBooks.isEmpty()) {
                        item(key = "empty_filtered_state") {
                            if (activeCollectionWithCount != null) {
                                EmptyCollectionState(
                                    collectionName = activeCollectionWithCount.collection.name,
                                    onAddBooksClick = { managingBooksCollection = activeCollectionWithCount.collection },
                                    onExitCollection = { onSelectFilter(BookshelfFilter.All) }
                                )
                            } else {
                                EmptyFilteredBooksState(
                                    onResetFilter = {
                                        onSearchChange("")
                                        onSelectFilter(BookshelfFilter.All)
                                    }
                                )
                            }
                        }
                    } else {
                        items(uiState.filteredBooks, key = { it.book.id }) { item ->
                            val selected = item.book.id in selectedBookIds
                            BookListRowCard(
                                item = item,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = selected,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        selectedBookIds = if (selected) {
                                            selectedBookIds - item.book.id
                                        } else {
                                            selectedBookIds + item.book.id
                                        }
                                    } else {
                                        onOpenBook(item.book.id)
                                    }
                                },
                                onMoreClick = {
                                    if (isMultiSelectMode) {
                                        selectedBookIds = if (selected) {
                                            selectedBookIds - item.book.id
                                        } else {
                                            selectedBookIds + item.book.id
                                        }
                                    } else {
                                        managingBook = item
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Extended FAB (+ 导入) when not in multi-select mode
        AnimatedVisibility(
            visible = !isMultiSelectMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = onQuickImportFiles,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("导入") },
                modifier = Modifier.testTag("bookshelf_import_fab")
            )
        }

        // Multi-Select Contextual Batch Bar
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            val allFilteredIds = uiState.filteredBooks.map { it.book.id }.toSet()
            val allSelected = allFilteredIds.isNotEmpty() && allFilteredIds.all { it in selectedBookIds }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bookshelf_multiselect_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            isMultiSelectMode = false
                            selectedBookIds = emptySet()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "退出多选")
                    }
                    Text(
                        text = "已选 ${selectedBookIds.size} 本",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            selectedBookIds = if (allSelected) {
                                selectedBookIds - allFilteredIds
                            } else {
                                selectedBookIds + allFilteredIds
                            }
                        }
                    ) {
                        Text(if (allSelected) "取消全选" else "全选")
                    }
                    Button(
                        onClick = { showBatchCollectionPicker = true },
                        enabled = selectedBookIds.isNotEmpty(),
                        modifier = Modifier.testTag("batch_add_to_collection_button")
                    ) {
                        Text("加入合集")
                    }
                }
            }
        }
    }

    if (activeManagingBook != null) {
        BookActionsSheet(
            item = activeManagingBook,
            allTags = uiState.tagsWithCounts,
            allCollections = uiState.collectionsWithCounts,
            onDismiss = { managingBook = null },
            onOpenBook = {
                managingBook = null
                onOpenBook(activeManagingBook.book.id)
            },
            onUpdateMetadata = { title, author, category ->
                onUpdateBookMetadata(activeManagingBook.book.id, title, author, category)
            },
            onToggleTag = { tagId, assign ->
                onToggleBookTag(activeManagingBook.book.id, tagId, assign)
            },
            onToggleCollection = { collectionId, assign ->
                onToggleBookCollection(activeManagingBook.book.id, collectionId, assign)
            },
            onCreateCollectionForBook = {
                createCollectionInitialBookIds = setOf(activeManagingBook.book.id)
                showCreateCollectionDialog = true
            },
            onSetStatus = { status ->
                onSetBookStatus(activeManagingBook.book, status)
            },
            onToggleFavorite = {
                onToggleBookFavorite(activeManagingBook.book)
            },
            onDelete = {
                managingBook = null
                onDeleteBook(activeManagingBook.book)
            }
        )
    }

    if (showBatchCollectionPicker) {
        AddBooksToCollectionPickerSheet(
            selectedCount = selectedBookIds.size,
            collections = uiState.collectionsWithCounts,
            onDismiss = { showBatchCollectionPicker = false },
            onPickCollection = { collection ->
                onAddBooksToCollection(collection.id, collection.name, selectedBookIds)
                showBatchCollectionPicker = false
                isMultiSelectMode = false
                selectedBookIds = emptySet()
            },
            onCreateNewCollection = {
                showBatchCollectionPicker = false
                createCollectionInitialBookIds = selectedBookIds
                showCreateCollectionDialog = true
            }
        )
    }

    if (showCreateCollectionDialog) {
        CreateEditCollectionDialog(
            existing = null,
            onDismiss = {
                showCreateCollectionDialog = false
                createCollectionInitialBookIds = emptySet()
            },
            onSave = { name, desc, colorHex ->
                onCreateCollection(name, desc, colorHex, createCollectionInitialBookIds)
                hasUserToggledCollections = true
                isCollectionsExpanded = true
                showCreateCollectionDialog = false
                if (createCollectionInitialBookIds.size > 1 || isMultiSelectMode) {
                    isMultiSelectMode = false
                    selectedBookIds = emptySet()
                }
                createCollectionInitialBookIds = emptySet()
            }
        )
    }

    val activeEditingCollection = editingCollection
    if (activeEditingCollection != null) {
        CreateEditCollectionDialog(
            existing = activeEditingCollection,
            onDismiss = { editingCollection = null },
            onSave = { name, desc, colorHex ->
                onUpdateCollection(
                    activeEditingCollection.copy(
                        name = name,
                        description = desc,
                        colorHex = colorHex
                    )
                )
                editingCollection = null
            },
            onDelete = {
                editingCollection = null
                deletingCollection = activeEditingCollection
            }
        )
    }

    val activeDeletingCollection = deletingCollection
    if (activeDeletingCollection != null) {
        AlertDialog(
            onDismissRequest = { deletingCollection = null },
            title = { Text("删除合集文件夹") },
            text = { Text("确定要删除合集「${activeDeletingCollection.name}」吗？其中的书籍与漫画不会被删除，仍保留在书架中。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCollection(activeDeletingCollection)
                        deletingCollection = null
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCollection = null }) { Text("取消") }
            }
        )
    }

    val activeManagingBooksCollection = managingBooksCollection
    if (activeManagingBooksCollection != null) {
        ManageCollectionBooksSheet(
            collection = activeManagingBooksCollection,
            allBooks = uiState.allBooks,
            onDismiss = { managingBooksCollection = null },
            onSaveMembership = { bookIds ->
                onSetBooksInCollection(activeManagingBooksCollection.id, bookIds)
            }
        )
    }
}

@Composable
private fun EmptyFilteredBooksState(
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.LibraryBooks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "当前筛选下暂无书籍",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = onResetFilter) {
            Text("重置筛选条件")
        }
    }
}

@Composable
private fun BookshelfQuickSortBar(
    totalCount: Int,
    filteredCount: Int,
    currentSort: BookshelfSortOrder,
    onSelectSortOrder: (BookshelfSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecentSelected = currentSort == BookshelfSortOrder.RECENT_READ ||
        currentSort == BookshelfSortOrder.ADDED_DESC
    val isAlphaSelected = currentSort == BookshelfSortOrder.TITLE_ASC

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (filteredCount == totalCount) {
                "共 $totalCount 本"
            } else {
                "显示 $filteredCount / $totalCount 本"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortTogglePill(
                selected = isRecentSelected,
                icon = Icons.Outlined.Schedule,
                label = "最近",
                onClick = { onSelectSortOrder(BookshelfSortOrder.RECENT_READ) },
                modifier = Modifier.testTag("sort_recent_chip")
            )
            SortTogglePill(
                selected = isAlphaSelected,
                icon = Icons.Outlined.SortByAlpha,
                label = "A-Z 排序",
                onClick = { onSelectSortOrder(BookshelfSortOrder.TITLE_ASC) },
                modifier = Modifier.testTag("sort_alpha_chip")
            )
        }
    }
}

@Composable
private fun SortTogglePill(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ShelfFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridCard(
    item: BookWithTags,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val book = item.book
    val unitWord = if (book.format.isTextFlow) "章" else "页"
    val progressLabel = when (book.readingStatus) {
        ReadingStatus.COMPLETED -> "已读完"
        ReadingStatus.UNREAD -> "未读"
        ReadingStatus.READING -> "${book.progressPercent}%"
    }
    val pageLabel = when (book.readingStatus) {
        ReadingStatus.READING -> "${book.currentUnit + 1}/${book.totalUnits} $unitWord"
        else -> "${book.totalUnits} $unitWord"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(2.dp)
            .testTag("book_card_${book.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(4.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.5.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            BookCoverArtwork(
                coverPath = book.coverPath,
                title = book.title,
                format = book.format,
                cornerRadius = 4.dp,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle top-left format pill
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xB81B1B1E),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = book.format.badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            if (isMultiSelectMode) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x881B1B1E),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .border(1.5.dp, Color.White, CircleShape)
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Unobstructed reading progress bar outside the cover frame
        LinearProgressIndicator(
            progress = {
                when (book.readingStatus) {
                    ReadingStatus.UNREAD -> 0f
                    ReadingStatus.COMPLETED -> 1f
                    ReadingStatus.READING -> book.progressFraction
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (book.readingStatus == ReadingStatus.COMPLETED) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (book.readingStatus == ReadingStatus.UNREAD) FontWeight.Normal else FontWeight.SemiBold
                ),
                color = when (book.readingStatus) {
                    ReadingStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                    ReadingStatus.READING -> MaterialTheme.colorScheme.primary
                    ReadingStatus.UNREAD -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BookListRowCard(
    item: BookWithTags,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val book = item.book
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onMoreClick)
            .testTag("book_row_${book.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverArtwork(
                coverPath = book.coverPath,
                title = book.title,
                format = book.format,
                cornerRadius = 4.dp,
                modifier = Modifier
                    .width(62.dp)
                    .height(86.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = book.format.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = book.categoryGroup,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val unitWord = if (book.format.isTextFlow) "章" else "页"
                val metaLine = buildString {
                    if (book.author.isNotBlank()) append("${book.author} · ")
                    when (book.readingStatus) {
                        ReadingStatus.UNREAD -> append("${book.totalUnits} $unitWord · 未读")
                        ReadingStatus.COMPLETED -> append("${book.totalUnits} $unitWord · 已完结 · 100%")
                        ReadingStatus.READING -> {
                            append("${book.currentUnit + 1}/${book.totalUnits} $unitWord")
                            append(" · 在读 ${book.progressPercent}%")
                        }
                    }
                }
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = {
                        when (book.readingStatus) {
                            ReadingStatus.UNREAD -> 0f
                            ReadingStatus.COMPLETED -> 1f
                            ReadingStatus.READING -> book.progressFraction
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (book.readingStatus == ReadingStatus.COMPLETED) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                if (item.collections.isNotEmpty() || item.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.collections.take(2).forEach { col ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(col.colorHex).copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = Color(col.colorHex),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = col.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(col.colorHex)
                                    )
                                }
                            }
                        }
                        item.tags.take(3).forEach { tag ->
                            Surface(
                                shape = CircleShape,
                                color = Color(tag.colorHex).copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(tag.colorHex),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "书籍选项与标签管理"
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBookshelfState(
    onPickLocalFiles: () -> Unit,
    onGoImportCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.LibraryBooks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "书架空空如也",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "支持导入本地 ZIP、CBZ 漫画压缩包与 EPUB 电子书，自动解析封面、章节目录与智能标签归类。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPickLocalFiles,
                shape = CircleShape,
                modifier = Modifier.testTag("empty_pick_files_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("选择本地文件")
            }
            TextButton(onClick = onGoImportCenter) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("授权文件夹")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookActionsSheet(
    item: BookWithTags,
    allTags: List<TagWithCount>,
    allCollections: List<CollectionWithCount>,
    onDismiss: () -> Unit,
    onOpenBook: () -> Unit,
    onUpdateMetadata: (String, String, String) -> Unit,
    onToggleTag: (Long, Boolean) -> Unit,
    onToggleCollection: (Long, Boolean) -> Unit,
    onCreateCollectionForBook: () -> Unit,
    onSetStatus: (ReadingStatus) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val book = item.book
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCoverArtwork(
                    coverPath = book.coverPath,
                    title = book.title,
                    format = book.format,
                    modifier = Modifier
                        .width(56.dp)
                        .height(78.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${book.format.badgeLabel} · ${book.categoryGroup} · ${book.totalUnits} ${if (book.format.isTextFlow) "章" else "页"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (book.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenBook,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始阅读")
                }
                Surface(
                    onClick = { showEditDialog = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("编辑信息", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text("所属合集文件夹", style = MaterialTheme.typography.titleSmall)
            val assignedCollectionIds = item.collections.map { it.id }.toSet()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allCollections.forEach { colWithCount ->
                    val col = colWithCount.collection
                    val selected = col.id in assignedCollectionIds
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleCollection(col.id, !selected) },
                        label = { Text(col.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (selected) Icons.Filled.Check else Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else Color(col.colorHex),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onCreateCollectionForBook,
                    label = { Text("+ 新建合集") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Text("阅读状态", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStatus.entries.forEach { status ->
                    FilterChip(
                        selected = book.readingStatus == status,
                        onClick = { onSetStatus(status) },
                        label = { Text(status.displayName) }
                    )
                }
            }

            Text("书籍标签（点击快速关联/移除）", style = MaterialTheme.typography.titleSmall)
            val assignedIds = item.tags.map { it.id }.toSet()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allTags.forEach { tagWithCount ->
                    val tag = tagWithCount.tag
                    val selected = tag.id in assignedIds
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleTag(tag.id, !selected) },
                        label = { Text(tag.name) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("从书架移除", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showEditDialog) {
        var titleText by remember(book.id) { mutableStateOf(book.title) }
        var authorText by remember(book.id) { mutableStateOf(book.author) }
        var categoryText by remember(book.id) { mutableStateOf(book.categoryGroup) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("编辑书籍信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("书名") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = authorText,
                        onValueChange = { authorText = it },
                        label = { Text("作者") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("归类分组") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleText.isNotBlank()) {
                            onUpdateMetadata(titleText, authorText, categoryText)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认移除") },
            text = { Text("确定要从书架移除《${book.title}》吗？本地原始文件不会被删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
