package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class BookFormat(val badgeLabel: String) {
    CBZ("CBZ"),
    ZIP("ZIP"),
    EPUB("EPUB"),
    PDF("PDF"),
    TXT("TXT"),
    MD("MD");

    val isTextFlow: Boolean
        get() = this == EPUB || this == TXT || this == MD

    val isEbook: Boolean
        get() = this == EPUB || this == PDF || this == TXT || this == MD
}

enum class ReadingStatus(val displayName: String) {
    UNREAD("未读"),
    READING("在读"),
    COMPLETED("已完结")
}

enum class SmartRuleConditionType(val label: String) {
    FORMAT_EQUALS("文件格式等于"),
    TITLE_CONTAINS("书名/文件名包含"),
    AUTHOR_CONTAINS("作者包含")
}

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["fileHash"]),
        Index(value = ["sourceUri"], unique = true)
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val format: BookFormat,
    val sourceUri: String,
    val localCopyPath: String? = null,
    val fileHash: String,
    val fileSizeBytes: Long,
    val coverPath: String? = null,
    val totalUnits: Int,
    val currentUnit: Int = 0,
    val currentOffset: Int = 0,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val categoryGroup: String = "未归类",
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L
) {
    val progressFraction: Float
        get() = when {
            readingStatus == ReadingStatus.COMPLETED -> 1f
            totalUnits <= 0 -> 0f
            readingStatus == ReadingStatus.UNREAD && currentUnit <= 0 && currentOffset <= 0 -> 0f
            format.isTextFlow -> {
                val safeUnit = currentUnit.coerceIn(0, totalUnits - 1)
                val withinUnit = (currentOffset.coerceIn(0, 1000) / 1000f)
                ((safeUnit.toFloat() + withinUnit) / totalUnits.toFloat()).coerceIn(0f, 1f)
            }
            else -> {
                val safeUnit = currentUnit.coerceIn(0, totalUnits - 1)
                ((safeUnit + 1).toFloat() / totalUnits.toFloat()).coerceIn(0f, 1f)
            }
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryGroup: String = "题材分类",
    val colorHex: Long = 0xFF4F378A,
    val isSystem: Boolean = false
)

@Entity(
    tableName = "book_tag_cross_ref",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"]), Index(value = ["bookId"])]
)
data class BookTagCrossRef(
    val bookId: Long,
    val tagId: Long
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val unitIndex: Int,
    val label: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "smart_rules")
data class SmartRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val conditionType: SmartRuleConditionType,
    val conditionValue: String,
    val targetTagName: String,
    val targetCategoryGroup: String,
    val isEnabled: Boolean = true,
    val priority: Int = 0
)

@Entity(
    tableName = "authorized_folders",
    indices = [Index(value = ["uri"], unique = true)]
)
data class AuthorizedFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val lastScannedAt: Long = System.currentTimeMillis(),
    val bookCount: Int = 0
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val colorHex: Long = 0xFF4F378A,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_collection_cross_ref",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["collectionId"]), Index(value = ["bookId"])]
)
data class BookCollectionCrossRef(
    val bookId: Long,
    val collectionId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class CollectionWithCount(
    @Embedded val collection: CollectionEntity,
    val bookCount: Int
)

data class BookWithTags(
    @Embedded val book: BookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookTagCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookCollectionCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "collectionId"
        )
    )
    val collections: List<CollectionEntity> = emptyList()
)

data class TagWithCount(
    @Embedded val tag: TagEntity,
    val bookCount: Int
)
