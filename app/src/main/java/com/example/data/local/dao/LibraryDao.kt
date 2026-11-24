package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.AuthorizedFolderEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.BookWithTags
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.SmartRuleEntity
import com.example.data.local.entity.TagEntity
import com.example.data.local.entity.TagWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Transaction
    @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadAt > 0 THEN lastReadAt ELSE addedAt END DESC")
    fun observeAllBooksWithTags(): Flow<List<BookWithTags>>

    @Transaction
    @Query("SELECT * FROM books")
    suspend fun getAllBooksWithTagsOnce(): List<BookWithTags>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBookWithTags(bookId: Long): Flow<BookWithTags?>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookWithTagsOnce(bookId: Long): BookWithTags?

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun findBookByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun findBookBySourceUri(sourceUri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET currentUnit = :unit, currentOffset = :offset, readingStatus = :status, lastReadAt = :lastReadAt WHERE id = :bookId")
    suspend fun updateReadingProgress(
        bookId: Long,
        unit: Int,
        offset: Int,
        status: ReadingStatus,
        lastReadAt: Long
    )

    @Query("UPDATE books SET title = :title, author = :author, categoryGroup = :categoryGroup WHERE id = :bookId")
    suspend fun updateBookMetadata(bookId: Long, title: String, author: String, categoryGroup: String)

    @Query("UPDATE books SET categoryGroup = :categoryGroup WHERE id = :bookId")
    suspend fun updateBookCategory(bookId: Long, categoryGroup: String)

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun updateBookFavorite(bookId: Long, isFavorite: Boolean)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    // Tags
    @Query(
        """
        SELECT tags.*, COUNT(book_tag_cross_ref.bookId) AS bookCount
        FROM tags
        LEFT JOIN book_tag_cross_ref ON tags.id = book_tag_cross_ref.tagId
        GROUP BY tags.id
        ORDER BY tags.categoryGroup ASC, bookCount DESC, tags.name ASC
        """
    )
    fun observeTagsWithCounts(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("UPDATE tags SET categoryGroup = :categoryGroup WHERE id IN (:tagIds)")
    suspend fun updateTagsCategoryGroup(tagIds: List<Long>, categoryGroup: String)

    @Query("DELETE FROM tags WHERE id IN (:tagIds)")
    suspend fun deleteTagsByIds(tagIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookTagCrossRef(crossRef: BookTagCrossRef)

    @Query("DELETE FROM book_tag_cross_ref WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun deleteBookTagCrossRef(bookId: Long, tagId: Long)

    @Query("DELETE FROM book_tag_cross_ref WHERE bookId = :bookId")
    suspend fun clearTagsForBook(bookId: Long)

    @Query("SELECT bookId FROM book_tag_cross_ref WHERE tagId IN (:tagIds)")
    suspend fun getBookIdsForTags(tagIds: List<Long>): List<Long>

    @Transaction
    suspend fun mergeTags(sourceTagIds: List<Long>, targetTagId: Long) {
        val toMerge = sourceTagIds.filter { it != targetTagId }
        if (toMerge.isEmpty()) return
        val affectedBookIds = getBookIdsForTags(toMerge).distinct()
        affectedBookIds.forEach { bookId ->
            insertBookTagCrossRef(BookTagCrossRef(bookId = bookId, tagId = targetTagId))
        }
        deleteTagsByIds(toMerge)
    }

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY unitIndex ASC, createdAt DESC")
    fun observeBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND unitIndex = :unitIndex")
    suspend fun deleteBookmarkAtUnit(bookId: Long, unitIndex: Int)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: Long)

    // Smart rules
    @Query("SELECT * FROM smart_rules ORDER BY priority ASC, id ASC")
    fun observeSmartRules(): Flow<List<SmartRuleEntity>>

    @Query("SELECT * FROM smart_rules WHERE isEnabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun getEnabledSmartRulesOnce(): List<SmartRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmartRule(rule: SmartRuleEntity): Long

    @Update
    suspend fun updateSmartRule(rule: SmartRuleEntity)

    @Query("UPDATE smart_rules SET isEnabled = :enabled WHERE id = :ruleId")
    suspend fun setSmartRuleEnabled(ruleId: Long, enabled: Boolean)

    @Query("DELETE FROM smart_rules WHERE id = :ruleId")
    suspend fun deleteSmartRule(ruleId: Long)

    // Authorized SAF folders
    @Query("SELECT * FROM authorized_folders ORDER BY lastScannedAt DESC")
    fun observeAuthorizedFolders(): Flow<List<AuthorizedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthorizedFolder(folder: AuthorizedFolderEntity): Long

    @Query("DELETE FROM authorized_folders WHERE uri = :uri")
    suspend fun deleteAuthorizedFolderByUri(uri: String)
}
