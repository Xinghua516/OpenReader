package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tonal_folio_prefs")

enum class ComicReadingDirection(val label: String) {
    MANGA_RTL("日漫翻页"),
    COMIC_LTR("左翻页"),
    WEBTOON_VERTICAL("条漫模式")
}

enum class EpubReaderTheme(val label: String) {
    PAPER("纸白"),
    SEPIA("暖纸"),
    NIGHT("深色")
}

enum class BookshelfSortOrder(
    val label: String,
    val shortLabel: String,
    val defaultAscending: Boolean
) {
    RECENT_READ("最近阅读", "最近阅读", false),
    ADDED_DESC("最新导入", "导入时间", false),
    TITLE_ASC("书名排序", "书名", true),
    AUTHOR_ASC("作者排序", "作者", true),
    PROGRESS_DESC("阅读进度", "进度", false),
    FILE_SIZE_DESC("文件大小", "大小", false)
}

enum class CollectionSortOrder(
    val label: String,
    val shortLabel: String,
    val defaultAscending: Boolean
) {
    CREATED_DESC("创建时间", "创建时间", false),
    NAME_ASC("名称排序", "名称", true),
    BOOK_COUNT_DESC("藏书数量", "藏书数", false)
}

data class AppPreferences(
    val autoCacheCovers: Boolean = true,
    val autoTagByFilename: Boolean = true,
    val skipDuplicates: Boolean = true,
    val isGridView: Boolean = true,
    val sortOrder: BookshelfSortOrder = BookshelfSortOrder.RECENT_READ,
    val sortAscending: Boolean = false,
    val collectionSortOrder: CollectionSortOrder = CollectionSortOrder.CREATED_DESC,
    val collectionSortAscending: Boolean = false,
    val useDynamicColor: Boolean = false,
    val darkModeOverride: Boolean? = null,
    val comicReadingDirection: ComicReadingDirection = ComicReadingDirection.MANGA_RTL,
    val trimWhiteMargins: Boolean = false,
    val keepScreenOn: Boolean = false,
    val readerBrightness: Float = -1f,
    val warmthLevel: Float = 0f,
    val epubFontSizeSp: Float = 18f,
    val epubLineHeightMultiplier: Float = 1.65f,
    val epubUseSerif: Boolean = false,
    val epubTheme: EpubReaderTheme = EpubReaderTheme.PAPER
)

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val AUTO_CACHE_COVERS = booleanPreferencesKey("auto_cache_covers")
        val AUTO_TAG_FILENAME = booleanPreferencesKey("auto_tag_filename")
        val SKIP_DUPLICATES = booleanPreferencesKey("skip_duplicates")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val COLLECTION_SORT_ORDER = stringPreferencesKey("collection_sort_order")
        val COLLECTION_SORT_ASCENDING = booleanPreferencesKey("collection_sort_ascending")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override")
        val COMIC_DIRECTION = stringPreferencesKey("comic_direction")
        val TRIM_WHITE_MARGINS = booleanPreferencesKey("trim_white_margins")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val WARMTH_LEVEL = floatPreferencesKey("warmth_level")
        val EPUB_FONT_SIZE = floatPreferencesKey("epub_font_size")
        val EPUB_LINE_HEIGHT = floatPreferencesKey("epub_line_height")
        val EPUB_USE_SERIF = booleanPreferencesKey("epub_use_serif")
        val EPUB_THEME = stringPreferencesKey("epub_theme")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val resolvedSortOrder = prefs[Keys.SORT_ORDER]?.let {
            runCatching { BookshelfSortOrder.valueOf(it) }.getOrNull()
        } ?: BookshelfSortOrder.RECENT_READ
        val resolvedCollectionSortOrder = prefs[Keys.COLLECTION_SORT_ORDER]?.let {
            runCatching { CollectionSortOrder.valueOf(it) }.getOrNull()
        } ?: CollectionSortOrder.CREATED_DESC

        AppPreferences(
            autoCacheCovers = prefs[Keys.AUTO_CACHE_COVERS] ?: true,
            autoTagByFilename = prefs[Keys.AUTO_TAG_FILENAME] ?: true,
            skipDuplicates = prefs[Keys.SKIP_DUPLICATES] ?: true,
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: true,
            sortOrder = resolvedSortOrder,
            sortAscending = prefs[Keys.SORT_ASCENDING] ?: resolvedSortOrder.defaultAscending,
            collectionSortOrder = resolvedCollectionSortOrder,
            collectionSortAscending = prefs[Keys.COLLECTION_SORT_ASCENDING] ?: resolvedCollectionSortOrder.defaultAscending,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            darkModeOverride = when (prefs[Keys.DARK_MODE_OVERRIDE]) {
                "DARK" -> true
                "LIGHT" -> false
                else -> null
            },
            comicReadingDirection = prefs[Keys.COMIC_DIRECTION]?.let {
                runCatching { ComicReadingDirection.valueOf(it) }.getOrNull()
            } ?: ComicReadingDirection.MANGA_RTL,
            trimWhiteMargins = prefs[Keys.TRIM_WHITE_MARGINS] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: false,
            readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
            warmthLevel = prefs[Keys.WARMTH_LEVEL] ?: 0f,
            epubFontSizeSp = prefs[Keys.EPUB_FONT_SIZE] ?: 18f,
            epubLineHeightMultiplier = prefs[Keys.EPUB_LINE_HEIGHT] ?: 1.65f,
            epubUseSerif = prefs[Keys.EPUB_USE_SERIF] ?: false,
            epubTheme = prefs[Keys.EPUB_THEME]?.let {
                runCatching { EpubReaderTheme.valueOf(it) }.getOrNull()
            } ?: EpubReaderTheme.PAPER
        )
    }

    suspend fun setAutoCacheCovers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CACHE_COVERS] = enabled }
    }

    suspend fun setAutoTagByFilename(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_TAG_FILENAME] = enabled }
    }

    suspend fun setSkipDuplicates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SKIP_DUPLICATES] = enabled }
    }

    suspend fun setIsGridView(grid: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_VIEW] = grid }
    }

    suspend fun setSortOrder(
        order: BookshelfSortOrder,
        ascending: Boolean = order.defaultAscending
    ) {
        context.dataStore.edit {
            it[Keys.SORT_ORDER] = order.name
            it[Keys.SORT_ASCENDING] = ascending
        }
    }

    suspend fun setSortAscending(ascending: Boolean) {
        context.dataStore.edit { it[Keys.SORT_ASCENDING] = ascending }
    }

    suspend fun setCollectionSortOrder(
        order: CollectionSortOrder,
        ascending: Boolean = order.defaultAscending
    ) {
        context.dataStore.edit {
            it[Keys.COLLECTION_SORT_ORDER] = order.name
            it[Keys.COLLECTION_SORT_ASCENDING] = ascending
        }
    }

    suspend fun setCollectionSortAscending(ascending: Boolean) {
        context.dataStore.edit { it[Keys.COLLECTION_SORT_ASCENDING] = ascending }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDarkModeOverride(override: Boolean?) {
        context.dataStore.edit {
            it[Keys.DARK_MODE_OVERRIDE] = when (override) {
                true -> "DARK"
                false -> "LIGHT"
                null -> "SYSTEM"
            }
        }
    }

    suspend fun setComicReadingDirection(direction: ComicReadingDirection) {
        context.dataStore.edit { it[Keys.COMIC_DIRECTION] = direction.name }
    }

    suspend fun setTrimWhiteMargins(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRIM_WHITE_MARGINS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setReaderBrightness(value: Float) {
        context.dataStore.edit { it[Keys.READER_BRIGHTNESS] = value }
    }

    suspend fun setWarmthLevel(value: Float) {
        context.dataStore.edit { it[Keys.WARMTH_LEVEL] = value.coerceIn(0f, 1f) }
    }

    suspend fun setEpubFontSizeSp(size: Float) {
        context.dataStore.edit { it[Keys.EPUB_FONT_SIZE] = size.coerceIn(13f, 30f) }
    }

    suspend fun setEpubLineHeightMultiplier(multiplier: Float) {
        context.dataStore.edit { it[Keys.EPUB_LINE_HEIGHT] = multiplier.coerceIn(1.2f, 2.4f) }
    }

    suspend fun setEpubUseSerif(serif: Boolean) {
        context.dataStore.edit { it[Keys.EPUB_USE_SERIF] = serif }
    }

    suspend fun setEpubTheme(theme: EpubReaderTheme) {
        context.dataStore.edit { it[Keys.EPUB_THEME] = theme.name }
    }
}
