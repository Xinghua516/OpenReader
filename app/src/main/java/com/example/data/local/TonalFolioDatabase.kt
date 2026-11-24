package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.LibraryDao
import com.example.data.local.entity.AuthorizedFolderEntity
import com.example.data.local.entity.BookCollectionCrossRef
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.SmartRuleConditionType
import com.example.data.local.entity.SmartRuleEntity
import com.example.data.local.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonalTypeConverters {
    @TypeConverter
    fun fromBookFormat(value: BookFormat): String = value.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun fromReadingStatus(value: ReadingStatus): String = value.name

    @TypeConverter
    fun toReadingStatus(value: String): ReadingStatus = ReadingStatus.valueOf(value)

    @TypeConverter
    fun fromRuleType(value: SmartRuleConditionType): String = value.name

    @TypeConverter
    fun toRuleType(value: String): SmartRuleConditionType = SmartRuleConditionType.valueOf(value)
}

@Database(
    entities = [
        BookEntity::class,
        TagEntity::class,
        BookTagCrossRef::class,
        BookmarkEntity::class,
        SmartRuleEntity::class,
        AuthorizedFolderEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(TonalTypeConverters::class)
abstract class TonalFolioDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: TonalFolioDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_collection_cross_ref` (
                        `bookId` INTEGER NOT NULL,
                        `collectionId` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `collectionId`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collection_cross_ref_collectionId` ON `book_collection_cross_ref` (`collectionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collection_cross_ref_bookId` ON `book_collection_cross_ref` (`bookId`)"
                )
            }
        }

        fun getInstance(context: Context): TonalFolioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): TonalFolioDatabase {
            lateinit var db: TonalFolioDatabase
            db = Room.databaseBuilder(
                appContext,
                TonalFolioDatabase::class.java,
                "tonal_folio.db"
            ).addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(supportDb: SupportSQLiteDatabase) {
                        super.onCreate(supportDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialClassificationConfig(db.libraryDao())
                        }
                    }
                }).build()
            return db
        }

        private suspend fun seedInitialClassificationConfig(dao: LibraryDao) {
            val defaultTags = listOf(
                TagEntity(name = "科幻", categoryGroup = "题材分类", colorHex = 0xFF4F378A),
                TagEntity(name = "奇幻", categoryGroup = "题材分类", colorHex = 0xFF633B48),
                TagEntity(name = "悬疑", categoryGroup = "题材分类", colorHex = 0xFF1D6391),
                TagEntity(name = "日常", categoryGroup = "题材分类", colorHex = 0xFF2D6A4F),
                TagEntity(name = "热血", categoryGroup = "题材分类", colorHex = 0xFFB3261E),
                TagEntity(name = "条漫", categoryGroup = "题材分类", colorHex = 0xFF8D5B00),
                TagEntity(name = "文字小说", categoryGroup = "题材分类", colorHex = 0xFF6750A4),
                TagEntity(name = "ZIP/CBZ", categoryGroup = "格式与载体", colorHex = 0xFF4F378A, isSystem = true),
                TagEntity(name = "EPUB", categoryGroup = "格式与载体", colorHex = 0xFF633B48, isSystem = true),
                TagEntity(name = "PDF", categoryGroup = "格式与载体", colorHex = 0xFFB3261E, isSystem = true),
                TagEntity(name = "TXT", categoryGroup = "格式与载体", colorHex = 0xFF1D6391, isSystem = true),
                TagEntity(name = "Markdown", categoryGroup = "格式与载体", colorHex = 0xFF2D6A4F, isSystem = true)
            )
            defaultTags.forEach { dao.insertTag(it) }

            val defaultRules = listOf(
                SmartRuleEntity(
                    name = "EPUB 格式 → 文字小说",
                    description = "导入时自动识别 EPUB 后缀并归类至文字小说",
                    conditionType = SmartRuleConditionType.FORMAT_EQUALS,
                    conditionValue = "EPUB",
                    targetTagName = "文字小说",
                    targetCategoryGroup = "电子书",
                    isEnabled = true,
                    priority = 1
                ),
                SmartRuleEntity(
                    name = "TXT / Markdown / PDF → 电子书",
                    description = "自动归类 TXT、Markdown 与 PDF 文档至电子书分类",
                    conditionType = SmartRuleConditionType.FORMAT_EQUALS,
                    conditionValue = "TXT,MD,PDF",
                    targetTagName = "文字小说",
                    targetCategoryGroup = "电子书",
                    isEnabled = true,
                    priority = 2
                ),
                SmartRuleEntity(
                    name = "包含 \"Webtoon\" / \"条漫\" → 条漫",
                    description = "匹配文件名或元数据关键词，自动标记条漫阅读模式",
                    conditionType = SmartRuleConditionType.TITLE_CONTAINS,
                    conditionValue = "webtoon,条漫,longstrip",
                    targetTagName = "条漫",
                    targetCategoryGroup = "条漫",
                    isEnabled = true,
                    priority = 3
                ),
                SmartRuleEntity(
                    name = "ZIP / CBZ 格式 → 漫画",
                    description = "自动归类漫画图片压缩包并添加载体标签",
                    conditionType = SmartRuleConditionType.FORMAT_EQUALS,
                    conditionValue = "ZIP,CBZ",
                    targetTagName = "ZIP/CBZ",
                    targetCategoryGroup = "漫画",
                    isEnabled = true,
                    priority = 4
                )
            )
            defaultRules.forEach { dao.insertSmartRule(it) }
        }
    }
}
