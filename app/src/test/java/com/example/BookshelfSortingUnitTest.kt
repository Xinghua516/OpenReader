package com.example

import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookWithTags
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionWithCount
import com.example.data.local.entity.ReadingStatus
import com.example.data.preferences.BookshelfSortOrder
import com.example.data.preferences.CollectionSortOrder
import com.example.parser.LocalizedNaturalComparator
import com.example.ui.viewmodel.BookshelfFilter
import com.example.ui.viewmodel.BookshelfSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfSortingUnitTest {

    @Test
    fun localizedNaturalComparator_sortsNumbersAndChinesePinyin() {
        val titles = listOf("Vol.10", "Vol.2", "沙丘", "三体", "第12话", "第3话")
        val sorted = titles.sortedWith(LocalizedNaturalComparator)
        assertEquals(listOf("Vol.2", "Vol.10", "第3话", "第12话", "三体", "沙丘"), sorted)
    }

    @Test
    fun sortBooks_supportsAllCriteriaAndBothDirections() {
        val b1 = BookWithTags(
            book = BookEntity(
                id = 1L,
                title = "Vol.10",
                author = "Bob",
                format = BookFormat.CBZ,
                sourceUri = "file:///1.cbz",
                fileHash = "h1",
                fileSizeBytes = 500L,
                totalUnits = 10,
                currentUnit = 4, // 50%
                readingStatus = ReadingStatus.READING,
                addedAt = 100L,
                lastReadAt = 300L
            ),
            tags = emptyList()
        )
        val b2 = BookWithTags(
            book = BookEntity(
                id = 2L,
                title = "Vol.2",
                author = "Alice",
                format = BookFormat.CBZ,
                sourceUri = "file:///2.cbz",
                fileHash = "h2",
                fileSizeBytes = 2000L,
                totalUnits = 10,
                currentUnit = 0,
                readingStatus = ReadingStatus.UNREAD, // 0%
                addedAt = 300L,
                lastReadAt = 0L
            ),
            tags = emptyList()
        )
        val b3 = BookWithTags(
            book = BookEntity(
                id = 3L,
                title = "三体",
                author = "", // blank author always stays at bottom
                format = BookFormat.EPUB,
                sourceUri = "file:///3.epub",
                fileHash = "h3",
                fileSizeBytes = 1000L,
                totalUnits = 20,
                currentUnit = 19,
                readingStatus = ReadingStatus.COMPLETED, // 100%
                addedAt = 200L,
                lastReadAt = 500L
            ),
            tags = emptyList()
        )
        val books = listOf(b1, b2, b3)

        // Title natural asc & desc
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.TITLE_ASC, true).map { it.book.id }
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.TITLE_ASC, false).map { it.book.id }
        )

        // Author asc & desc (blank author always placed last)
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.AUTHOR_ASC, true).map { it.book.id }
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.AUTHOR_ASC, false).map { it.book.id }
        )

        // Recent read desc (500, 300, 300 -> tie break Vol.2 before Vol.10) & asc
        assertEquals(
            listOf(3L, 2L, 1L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.RECENT_READ, false).map { it.book.id }
        )
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.RECENT_READ, true).map { it.book.id }
        )

        // Added time desc & asc
        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.ADDED_DESC, false).map { it.book.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.ADDED_DESC, true).map { it.book.id }
        )

        // Reading progress desc & asc
        assertEquals(
            listOf(3L, 1L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.PROGRESS_DESC, false).map { it.book.id }
        )
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.PROGRESS_DESC, true).map { it.book.id }
        )

        // File size desc & asc
        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.FILE_SIZE_DESC, false).map { it.book.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.FILE_SIZE_DESC, true).map { it.book.id }
        )

        // Filtered query preserves sort order and direction
        val filteredComicsAsc = BookshelfSearchEngine.filterBooks(
            books = books,
            query = "Vol",
            filter = BookshelfFilter.Comics,
            sortOrder = BookshelfSortOrder.TITLE_ASC,
            sortAscending = true
        )
        assertEquals(listOf(2L, 1L), filteredComicsAsc.map { it.book.id })
    }

    @Test
    fun sortCollections_supportsAllCriteriaAndBothDirections() {
        val c1 = CollectionWithCount(CollectionEntity(id = 1L, name = "系列 10", createdAt = 100L), bookCount = 2)
        val c2 = CollectionWithCount(CollectionEntity(id = 2L, name = "系列 2", createdAt = 300L), bookCount = 8)
        val c3 = CollectionWithCount(CollectionEntity(id = 3L, name = "收藏精选", createdAt = 200L), bookCount = 5)
        val cols = listOf(c1, c2, c3)

        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.CREATED_DESC, false).map { it.collection.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.CREATED_DESC, true).map { it.collection.id }
        )
        assertEquals(
            listOf(3L, 2L, 1L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.NAME_ASC, true).map { it.collection.id }
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.NAME_ASC, false).map { it.collection.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.BOOK_COUNT_DESC, false).map { it.collection.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.BOOK_COUNT_DESC, true).map { it.collection.id }
        )
    }
}
