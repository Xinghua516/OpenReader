package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TonalFolioDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.BookFormat
import com.example.data.local.entity.BookTagCrossRef
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionWithCount
import com.example.data.local.entity.ReadingStatus
import com.example.data.local.entity.TagEntity
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.BookshelfSortOrder
import com.example.data.preferences.CollectionSortOrder
import com.example.data.repository.ImportTaskStatus
import com.example.data.repository.LibraryRepository
import com.example.parser.ArchiveSession
import com.example.parser.BookImporterParser
import com.example.parser.EpubContentBlock
import com.example.parser.EpubParser
import com.example.parser.FilenameMetadataParser
import com.example.parser.LocalizedNaturalComparator
import com.example.parser.NaturalOrderComparator
import com.example.parser.TextAndMarkdownParser
import com.example.ui.viewmodel.BookshelfSearchEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.charset.Charset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookParserAndDbTest {

    private lateinit var context: Context
    private lateinit var db: TonalFolioDatabase
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TonalFolioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDir = File(context.cacheDir, "unit_test_archives").apply { mkdirs() }
    }

    @After
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `natural order comparator sorts numbered comic pages accurately`() {
        val raw = listOf("ch1/page_10.jpg", "ch1/page_2.jpg", "ch1/page_1.jpg", "ch1/page_20.jpg")
        val sorted = raw.sortedWith(NaturalOrderComparator)
        assertEquals(
            listOf("ch1/page_1.jpg", "ch1/page_2.jpg", "ch1/page_10.jpg", "ch1/page_20.jpg"),
            sorted
        )
    }

    @Test
    fun `filename metadata parser extracts author title and tags`() {
        val meta = FilenameMetadataParser.parse("[远藤达哉] 间谍过家家 Vol.08 (webtoon).cbz", BookFormat.CBZ)
        assertEquals("远藤达哉", meta.author)
        assertTrue(meta.title.contains("间谍过家家"))
        assertTrue(meta.suggestedTags.contains("条漫"))
        assertTrue(meta.suggestedTags.contains("ZIP/CBZ"))
    }

    @Test
    fun `cbz archive parser extracts cover and pages in natural order`() = runTest {
        val cbzFile = File(tempDir, "test_comic.cbz")
        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            zos.putNextEntry(ZipEntry("__MACOSX/._page1.jpg"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()

            for (name in listOf("page_10.png", "page_2.png", "page_1.png")) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(createTinyPngBytes())
                zos.closeEntry()
            }
        }

        val parsed = BookImporterParser.parseUri(
            context = context,
            uri = Uri.fromFile(cbzFile),
            autoCacheCover = true,
            autoTagByFilename = true,
            onProgress = { _, _ -> }
        )

        assertEquals(BookFormat.CBZ, parsed.format)
        assertEquals(3, parsed.totalUnits)
        assertNotNull(parsed.coverPath)
        assertTrue(File(parsed.coverPath!!).exists())

        ArchiveSession.open(context, Uri.fromFile(cbzFile).toString(), cbzFile.absolutePath, parsed.fileHash).use { session ->
            assertEquals(listOf("page_1.png", "page_2.png", "page_10.png"), session.sortedImageEntries)
        }
    }

    @Test
    fun `epub parser extracts metadata chapters and xhtml content`() = runTest {
        val epubFile = File(tempDir, "sample_novel.epub")
        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>星海漫游指南</dc:title>
                    <dc:creator>林深</dc:creator>
                    <dc:subject>科幻</dc:subject>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                  </manifest>
                  <spine>
                    <itemref idref="ch1"/>
                  </spine>
                </package>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/images/cover.png"))
            zos.write(createTinyPngBytes())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/text/ch1.xhtml"))
            zos.write(
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>第一章 启程</title></head>
                  <body>
                    <h1>第一章 启程</h1>
                    <p>飞船穿过静默星云，舷窗外亮起淡淡的紫色微光。</p>
                    <img src="../images/cover.png" alt="插图"/>
                  </body>
                </html>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }

        val parsed = BookImporterParser.parseUri(
            context = context,
            uri = Uri.fromFile(epubFile),
            autoCacheCover = true,
            autoTagByFilename = true,
            onProgress = { _, _ -> }
        )

        assertEquals(BookFormat.EPUB, parsed.format)
        assertEquals("星海漫游指南", parsed.title)
        assertEquals("林深", parsed.author)
        assertEquals(1, parsed.totalUnits)
        assertTrue(parsed.suggestedTags.contains("科幻"))

        ArchiveSession.open(context, Uri.fromFile(epubFile).toString(), epubFile.absolutePath, parsed.fileHash).use { session ->
            val structure = EpubParser.parseStructure(session)
            assertEquals(1, structure.chapters.size)
            val blocks = EpubParser.parseChapterContent(session, structure.chapters.first().entryPath)
            assertTrue(blocks.any { it is EpubContentBlock.Heading && it.text.contains("启程") })
            assertTrue(blocks.any { it is EpubContentBlock.Paragraph && it.text.contains("静默星云") })
            assertTrue(blocks.any { it is EpubContentBlock.ImageBlock && it.entryPath == "OEBPS/images/cover.png" })
        }
    }

    @Test
    fun `room dao merges tags cleanly across books`() = runTest {
        val dao = db.libraryDao()
        val bookId = dao.insertBook(
            BookEntity(
                title = "测试漫画",
                format = BookFormat.CBZ,
                sourceUri = "file:///tmp/test.cbz",
                fileHash = "hash123",
                fileSizeBytes = 1024L,
                totalUnits = 12
            )
        )
        val tagA = dao.insertTag(TagEntity(name = "TagA"))
        val tagB = dao.insertTag(TagEntity(name = "TagB"))
        dao.insertBookTagCrossRef(BookTagCrossRef(bookId, tagA))
        dao.mergeTags(listOf(tagA), tagB)

        val loaded = dao.getBookWithTagsOnce(bookId)
        assertNotNull(loaded)
        assertEquals(listOf("TagB"), loaded!!.tags.map { it.name })
    }

    @Test
    fun `repository imports zip archive into internal storage and rejects invalid files`() = runTest {
        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)

        val zipFile = File(tempDir, "internal_import_sample.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("001.png"))
            zos.write(createTinyPngBytes())
            zos.closeEntry()
        }

        val task = repo.importUriToInternalStorageSync(Uri.fromFile(zipFile))
        assertEquals(ImportTaskStatus.COMPLETED, task.status)
        assertNotNull(task.createdBookId)

        val storedBook = db.libraryDao().getBookWithTagsOnce(task.createdBookId!!)
        assertNotNull(storedBook)
        val internalPath = storedBook!!.book.localCopyPath
        assertNotNull(internalPath)
        assertTrue(File(internalPath!!).exists())
        assertTrue(internalPath.startsWith(File(context.filesDir, "library").absolutePath))

        val invalidFile = File(tempDir, "corrupted_archive.zip").apply { writeText("not a valid zip payload") }
        val failedTask = repo.importUriToInternalStorageSync(Uri.fromFile(invalidFile))
        assertEquals(ImportTaskStatus.FAILED, failedTask.status)
    }

    @Test
    fun `txt parser supports utf8 and gbk novels and imports to internal storage`() = runTest {
        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)

        val gbkText = """
            作者：陈默
            第一章 初雪
            风起青萍之末，雪落在旧书页上。
            第二章 远航
            汽笛声穿过晨雾。
        """.trimIndent()
        val gbkFile = File(tempDir, "风起青萍.txt")
        gbkFile.writeBytes(gbkText.toByteArray(Charset.forName("GB18030")))

        val parsedDoc = TextAndMarkdownParser.parseTxt(gbkFile.readBytes(), "风起青萍")
        assertEquals("陈默", parsedDoc.author)
        assertEquals(2, parsedDoc.chapters.size)
        assertEquals("第一章 初雪", parsedDoc.chapters[0].title)
        assertEquals("第二章 远航", parsedDoc.chapters[1].title)

        val task = repo.importUriToInternalStorageSync(Uri.fromFile(gbkFile))
        assertEquals(ImportTaskStatus.COMPLETED, task.status)
        assertEquals(BookFormat.TXT, task.format)
        assertNotNull(task.createdBookId)
        val stored = db.libraryDao().getBookWithTagsOnce(task.createdBookId!!)!!
        assertEquals("陈默", stored.book.author)
        assertEquals(2, stored.book.totalUnits)
        assertTrue(stored.book.localCopyPath!!.endsWith(".txt"))
    }

    @Test
    fun `markdown parser extracts yaml metadata chapters code blocks and imports cleanly`() = runTest {
        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)

        val mdContent = """
            ---
            title: 设计中的留白
            author: 原研哉
            tags: [设计, 随笔]
            ---
            # 第一节 虚空与纸面
            留白并非什么都没有，而是**承载想象的空间**。

            > 好的版式会引导视线呼吸。

            - 减少冗余装饰
            - 保留足够边距

            ```kotlin
            fun renderPage() = println("Tonal Folio")
            ```

            ---

            # 第二节 材质的温度
            数字阅读同样需要纸感与节奏。
        """.trimIndent()
        val mdFile = File(tempDir, "design_notes.md").apply { writeText(mdContent) }

        val parsed = TextAndMarkdownParser.parseMarkdown(mdFile.readBytes(), "design_notes")
        assertEquals("设计中的留白", parsed.title)
        assertEquals("原研哉", parsed.author)
        assertEquals(2, parsed.chapters.size)
        assertTrue(parsed.chapters[0].blocks.any { it is EpubContentBlock.Quote })
        assertTrue(parsed.chapters[0].blocks.any { it is EpubContentBlock.CodeBlock && it.language == "kotlin" })
        assertTrue(parsed.chapters[0].blocks.any { it is EpubContentBlock.DividerBlock })

        val task = repo.importUriToInternalStorageSync(Uri.fromFile(mdFile))
        assertEquals(ImportTaskStatus.COMPLETED, task.status)
        assertEquals(BookFormat.MD, task.format)
        val stored = db.libraryDao().getBookWithTagsOnce(task.createdBookId!!)!!
        assertEquals("设计中的留白", stored.book.title)
        assertEquals(2, stored.book.totalUnits)
        assertTrue(stored.book.localCopyPath!!.endsWith(".md"))
    }

    @Test
    fun `format detector recognizes pdf txt markdown and rejects invalid pdf header`() = runTest {
        assertEquals(BookFormat.PDF, FilenameMetadataParser.detectFormatByName("manual.pdf", null))
        assertEquals(BookFormat.TXT, FilenameMetadataParser.detectFormatByName("novel.txt", "text/plain"))
        assertEquals(BookFormat.MD, FilenameMetadataParser.detectFormatByName("readme.markdown", "text/markdown"))

        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)
        val fakePdf = File(tempDir, "broken.pdf").apply { writeText("definitely not pdf") }
        val failedTask = repo.importUriToInternalStorageSync(Uri.fromFile(fakePdf))
        assertEquals(ImportTaskStatus.FAILED, failedTask.status)
    }

    @Test
    fun `bookshelf sorting orders books by recent and alphabetical order`() = runTest {
        val dao = db.libraryDao()
        dao.insertBook(
            BookEntity(
                title = "Zeta Comic",
                format = BookFormat.CBZ,
                sourceUri = "file:///tmp/z.cbz",
                fileHash = "hz",
                fileSizeBytes = 100L,
                totalUnits = 10,
                addedAt = 1000L,
                lastReadAt = 5000L
            )
        )
        dao.insertBook(
            BookEntity(
                title = "Alpha Novel",
                format = BookFormat.EPUB,
                sourceUri = "file:///tmp/a.epub",
                fileHash = "ha",
                fileSizeBytes = 200L,
                totalUnits = 5,
                addedAt = 2000L,
                lastReadAt = 0L
            )
        )

        val all = dao.getAllBooksWithTagsOnce()
        val recentOrder = all.sortedByDescending { maxOf(it.book.lastReadAt, it.book.addedAt) }
        val alphaOrder = all.sortedBy { it.book.title.lowercase() }

        assertEquals("Zeta Comic", recentOrder.first().book.title)
        assertEquals("Alpha Novel", alphaOrder.first().book.title)
    }

    @Test
    fun `book progress fraction accurately reflects text-flow offset and page-based progress`() {
        val unreadText = BookEntity(
            title = "未读小说",
            format = BookFormat.TXT,
            sourceUri = "file:///tmp/u.txt",
            fileHash = "u1",
            fileSizeBytes = 100L,
            totalUnits = 1,
            currentUnit = 0,
            currentOffset = 0,
            readingStatus = com.example.data.local.entity.ReadingStatus.UNREAD
        )
        assertEquals(0, unreadText.progressPercent)

        val singleChapterMidway = unreadText.copy(
            readingStatus = com.example.data.local.entity.ReadingStatus.READING,
            currentOffset = 450
        )
        assertEquals(45, singleChapterMidway.progressPercent)

        val twoChapterStartCh2 = BookEntity(
            title = "双章书",
            format = BookFormat.EPUB,
            sourceUri = "file:///tmp/e.epub",
            fileHash = "e1",
            fileSizeBytes = 100L,
            totalUnits = 2,
            currentUnit = 1,
            currentOffset = 0,
            readingStatus = com.example.data.local.entity.ReadingStatus.READING
        )
        assertEquals(50, twoChapterStartCh2.progressPercent)

        val twoChapterEndCh2 = twoChapterStartCh2.copy(currentOffset = 800)
        assertEquals(90, twoChapterEndCh2.progressPercent)

        val comicMidway = BookEntity(
            title = "条漫",
            format = BookFormat.CBZ,
            sourceUri = "file:///tmp/c.cbz",
            fileHash = "c1",
            fileSizeBytes = 100L,
            totalUnits = 10,
            currentUnit = 3,
            readingStatus = com.example.data.local.entity.ReadingStatus.READING
        )
        assertEquals(40, comicMidway.progressPercent)
    }

    @Test
    fun `repository reading progress persists chapter offset and respects completion threshold`() = runTest {
        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)
        val dao = db.libraryDao()

        val textBookId = dao.insertBook(
            BookEntity(
                title = "长篇小说",
                format = BookFormat.TXT,
                sourceUri = "file:///tmp/novel.txt",
                fileHash = "novel_hash",
                fileSizeBytes = 500L,
                totalUnits = 2
            )
        )

        // Entering final chapter at offset 0 should stay READING (50%), not prematurely COMPLETED
        repo.updateReadingProgress(
            bookId = textBookId,
            unitIndex = 1,
            offset = 0,
            totalUnits = 2,
            isTextFlow = true
        )
        var loaded = dao.getBookWithTagsOnce(textBookId)!!.book
        assertEquals(com.example.data.local.entity.ReadingStatus.READING, loaded.readingStatus)
        assertEquals(1, loaded.currentUnit)
        assertEquals(0, loaded.currentOffset)
        assertEquals(50, loaded.progressPercent)

        // Scrolling partway through final chapter updates offset and percent
        repo.updateReadingProgress(
            bookId = textBookId,
            unitIndex = 1,
            offset = 500,
            totalUnits = 2,
            isTextFlow = true
        )
        loaded = dao.getBookWithTagsOnce(textBookId)!!.book
        assertEquals(com.example.data.local.entity.ReadingStatus.READING, loaded.readingStatus)
        assertEquals(500, loaded.currentOffset)
        assertEquals(75, loaded.progressPercent)

        // Scrolling to the end (>= 960 permille) marks COMPLETED (100%)
        repo.updateReadingProgress(
            bookId = textBookId,
            unitIndex = 1,
            offset = 980,
            totalUnits = 2,
            isTextFlow = true
        )
        loaded = dao.getBookWithTagsOnce(textBookId)!!.book
        assertEquals(com.example.data.local.entity.ReadingStatus.COMPLETED, loaded.readingStatus)
        assertEquals(100, loaded.progressPercent)

        // Page-based PDF/comic opening page 1 marks READING; reaching last page marks COMPLETED
        val comicId = dao.insertBook(
            BookEntity(
                title = "画集",
                format = BookFormat.PDF,
                sourceUri = "file:///tmp/art.pdf",
                fileHash = "pdf_hash",
                fileSizeBytes = 800L,
                totalUnits = 5
            )
        )
        repo.updateReadingProgress(bookId = comicId, unitIndex = 0, offset = 0, totalUnits = 5, isTextFlow = false)
        var comic = dao.getBookWithTagsOnce(comicId)!!.book
        assertEquals(com.example.data.local.entity.ReadingStatus.READING, comic.readingStatus)
        assertEquals(20, comic.progressPercent)

        repo.updateReadingProgress(bookId = comicId, unitIndex = 4, offset = 0, totalUnits = 5, isTextFlow = false)
        comic = dao.getBookWithTagsOnce(comicId)!!.book
        assertEquals(com.example.data.local.entity.ReadingStatus.COMPLETED, comic.readingStatus)
        assertEquals(100, comic.progressPercent)
    }

    @Test
    fun `collections repository groups books and comics into custom folders and keeps books on delete`() = runTest {
        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)
        val dao = db.libraryDao()

        val comicId = dao.insertBook(
            BookEntity(
                title = "电锯人 Vol.12",
                format = BookFormat.CBZ,
                sourceUri = "file:///tmp/csm12.cbz",
                fileHash = "csm12",
                fileSizeBytes = 1024L,
                totalUnits = 180
            )
        )
        val epubId = dao.insertBook(
            BookEntity(
                title = "三体全集",
                format = BookFormat.EPUB,
                sourceUri = "file:///tmp/3body.epub",
                fileHash = "3body",
                fileSizeBytes = 2048L,
                totalUnits = 30
            )
        )

        val folderId = repo.createCollection(
            name = "周末必看",
            description = "精选长篇漫画与科幻小说",
            colorHex = 0xFF4F378A
        )
        assertTrue(folderId > 0L)

        // Duplicate name rejected
        assertEquals(-1L, repo.createCollection("周末必看"))

        // Batch assign both comic and ebook into folder
        repo.setBooksInCollection(folderId, setOf(comicId, epubId))
        val comicWithRelations = dao.getBookWithTagsOnce(comicId)!!
        assertEquals(listOf("周末必看"), comicWithRelations.collections.map { it.name })

        // Rename collection
        val created = dao.findCollectionById(folderId)!!
        assertTrue(repo.updateCollection(created.copy(name = "夜间精选")))
        val renamed = dao.findCollectionById(folderId)!!
        assertEquals("夜间精选", renamed.name)

        // Remove single book from folder
        repo.toggleBookInCollection(epubId, folderId, assign = false)
        assertEquals(listOf(comicId), dao.getBookIdsInCollection(folderId))

        // Appending books preserves existing folder members
        repo.addBooksToCollection(folderId, setOf(epubId))
        assertEquals(setOf(comicId, epubId), dao.getBookIdsInCollection(folderId).toSet())

        // Delete collection folder — books stay intact in library
        repo.deleteCollection(folderId)
        assertEquals(null, dao.findCollectionById(folderId))
        assertNotNull(dao.getBookWithTagsOnce(comicId))
        assertNotNull(dao.getBookWithTagsOnce(epubId))
    }

    @Test
    fun `room database migration 1 to 2 upgrades cleanly and preserves existing books`() = runTest {
        val dbName = "migration_test_tonal_folio.db"
        context.deleteDatabase(dbName)

        // Create a version-1 SQLite database manually with an existing book row
        val sqliteHelper = object : android.database.sqlite.SQLiteOpenHelper(context, dbName, null, 1) {
            override fun onCreate(sqLiteDatabase: android.database.sqlite.SQLiteDatabase) {
                sqLiteDatabase.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `books` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `format` TEXT NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `localCopyPath` TEXT,
                        `fileHash` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `coverPath` TEXT,
                        `totalUnits` INTEGER NOT NULL,
                        `currentUnit` INTEGER NOT NULL,
                        `currentOffset` INTEGER NOT NULL,
                        `readingStatus` TEXT NOT NULL,
                        `categoryGroup` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `lastReadAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS `index_books_fileHash` ON `books` (`fileHash`)")
                sqLiteDatabase.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_sourceUri` ON `books` (`sourceUri`)")
                sqLiteDatabase.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `categoryGroup` TEXT NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `isSystem` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                sqLiteDatabase.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                sqLiteDatabase.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_tag_cross_ref` (
                        `bookId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `tagId`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_cross_ref_tagId` ON `book_tag_cross_ref` (`tagId`)")
                sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_cross_ref_bookId` ON `book_tag_cross_ref` (`bookId`)")
                sqLiteDatabase.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `unitIndex` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
                sqLiteDatabase.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smart_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `conditionType` TEXT NOT NULL,
                        `conditionValue` TEXT NOT NULL,
                        `targetTagName` TEXT NOT NULL,
                        `targetCategoryGroup` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                sqLiteDatabase.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `authorized_folders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `lastScannedAt` INTEGER NOT NULL,
                        `bookCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                sqLiteDatabase.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_authorized_folders_uri` ON `authorized_folders` (`uri`)")
                sqLiteDatabase.execSQL(
                    """
                    INSERT INTO `books` (`id`, `title`, `author`, `format`, `sourceUri`, `localCopyPath`, `fileHash`, `fileSizeBytes`, `coverPath`, `totalUnits`, `currentUnit`, `currentOffset`, `readingStatus`, `categoryGroup`, `isFavorite`, `addedAt`, `lastReadAt`)
                    VALUES (1, '旧版保留书目', '作者甲', 'EPUB', 'file:///legacy.epub', NULL, 'legacy_hash', 1200, NULL, 10, 2, 350, 'READING', '电子书', 0, 1000, 2000)
                    """.trimIndent()
                )
            }

            override fun onUpgrade(sqLiteDatabase: android.database.sqlite.SQLiteDatabase, oldV: Int, newV: Int) {}
        }
        sqliteHelper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, TonalFolioDatabase::class.java, dbName)
            .addMigrations(TonalFolioDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val migratedDao = migratedDb.libraryDao()
            val legacyBook = migratedDao.getBookWithTagsOnce(1L)
            assertNotNull(legacyBook)
            assertEquals("旧版保留书目", legacyBook!!.book.title)

            val newColId = migratedDao.insertCollection(
                com.example.data.local.entity.CollectionEntity(name = "迁移后合集")
            )
            assertTrue(newColId > 0L)
            migratedDao.insertBookCollectionCrossRef(
                com.example.data.local.entity.BookCollectionCrossRef(bookId = 1L, collectionId = newColId)
            )
            val updatedBook = migratedDao.getBookWithTagsOnce(1L)!!
            assertEquals(listOf("迁移后合集"), updatedBook.collections.map { it.name })
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `bookshelf search engine filters books comics and collections by title author and tags`() {
        val sciFiTag = TagEntity(id = 1L, name = "科幻", categoryGroup = "题材")
        val webtoonTag = TagEntity(id = 2L, name = "条漫", categoryGroup = "载体")
        val actionTag = TagEntity(id = 3L, name = "热血", categoryGroup = "题材")

        val weekendCollection = com.example.data.local.entity.CollectionEntity(
            id = 10L,
            name = "周末必看",
            description = "精选连载书单"
        )
        val designCollection = com.example.data.local.entity.CollectionEntity(
            id = 20L,
            name = "设计灵感",
            description = "排版美学"
        )

        val comicBook = com.example.data.local.entity.BookWithTags(
            book = BookEntity(
                id = 100L,
                title = "间谍过家家 Vol.08",
                author = "远藤达哉",
                format = BookFormat.CBZ,
                sourceUri = "file:///spy.cbz",
                fileHash = "spy",
                fileSizeBytes = 500L,
                totalUnits = 120
            ),
            tags = listOf(webtoonTag, actionTag),
            collections = listOf(weekendCollection)
        )
        val sciFiEpub = com.example.data.local.entity.BookWithTags(
            book = BookEntity(
                id = 101L,
                title = "三体全集",
                author = "刘慈欣",
                format = BookFormat.EPUB,
                sourceUri = "file:///3body.epub",
                fileHash = "3b",
                fileSizeBytes = 900L,
                totalUnits = 30
            ),
            tags = listOf(sciFiTag),
            collections = listOf(weekendCollection)
        )
        val designMd = com.example.data.local.entity.BookWithTags(
            book = BookEntity(
                id = 102L,
                title = "设计中的留白",
                author = "原研哉",
                format = BookFormat.MD,
                sourceUri = "file:///design.md",
                fileHash = "md",
                fileSizeBytes = 300L,
                totalUnits = 5
            ),
            tags = emptyList(),
            collections = listOf(designCollection)
        )

        val allBooks = listOf(comicBook, sciFiEpub, designMd)
        val allCollections = listOf(
            com.example.data.local.entity.CollectionWithCount(weekendCollection, 2),
            com.example.data.local.entity.CollectionWithCount(designCollection, 1)
        )

        // 1. Filter by title
        val byTitle = com.example.ui.viewmodel.BookshelfSearchEngine.filterBooks(
            books = allBooks,
            query = "间谍",
            filter = com.example.ui.viewmodel.BookshelfFilter.All,
            sortOrder = com.example.data.preferences.BookshelfSortOrder.RECENT_READ
        )
        assertEquals(listOf(100L), byTitle.map { it.book.id })

        // 2. Filter by author + verify matching collection surfaces too
        val byAuthorBooks = com.example.ui.viewmodel.BookshelfSearchEngine.filterBooks(
            books = allBooks,
            query = "刘慈欣",
            filter = com.example.ui.viewmodel.BookshelfFilter.All,
            sortOrder = com.example.data.preferences.BookshelfSortOrder.RECENT_READ
        )
        val byAuthorCollections = com.example.ui.viewmodel.BookshelfSearchEngine.filterCollections(
            collections = allCollections,
            books = allBooks,
            query = "刘慈欣"
        )
        assertEquals(listOf(101L), byAuthorBooks.map { it.book.id })
        assertEquals(listOf(10L), byAuthorCollections.map { it.collection.id })

        // 3. Filter by tag with # prefix + multi-token author+tag
        val byTagBooks = com.example.ui.viewmodel.BookshelfSearchEngine.filterBooks(
            books = allBooks,
            query = "#条漫 远藤",
            filter = com.example.ui.viewmodel.BookshelfFilter.All,
            sortOrder = com.example.data.preferences.BookshelfSortOrder.RECENT_READ
        )
        val byTagCollections = com.example.ui.viewmodel.BookshelfSearchEngine.filterCollections(
            collections = allCollections,
            books = allBooks,
            query = "#条漫 远藤"
        )
        assertEquals(listOf(100L), byTagBooks.map { it.book.id })
        assertEquals(listOf(10L), byTagCollections.map { it.collection.id })

        // 4. Filter by collection name matches both collection and books inside it
        val byColBooks = com.example.ui.viewmodel.BookshelfSearchEngine.filterBooks(
            books = allBooks,
            query = "排版美学",
            filter = com.example.ui.viewmodel.BookshelfFilter.All,
            sortOrder = com.example.data.preferences.BookshelfSortOrder.RECENT_READ
        )
        val byColFolders = com.example.ui.viewmodel.BookshelfSearchEngine.filterCollections(
            collections = allCollections,
            books = allBooks,
            query = "排版美学"
        )
        assertTrue(byColBooks.isEmpty()) // description is on collection itself
        assertEquals(listOf(20L), byColFolders.map { it.collection.id })

        val byColNameBooks = com.example.ui.viewmodel.BookshelfSearchEngine.filterBooks(
            books = allBooks,
            query = "设计灵感",
            filter = com.example.ui.viewmodel.BookshelfFilter.All,
            sortOrder = com.example.data.preferences.BookshelfSortOrder.RECENT_READ
        )
        assertEquals(listOf(102L), byColNameBooks.map { it.book.id })
    }

    @Test
    fun `repository batch deleteBooks unimports from bookshelf without deleting original local files`() = runTest {
        val prefsRepo = AppPreferencesRepository(context)
        val repo = LibraryRepository(context, db.libraryDao(), prefsRepo)
        val dao = db.libraryDao()

        val externalDir = File(context.noBackupFilesDir, "user_local_originals").apply { mkdirs() }
        val originalExternalFile = File(externalDir, "user_comic_original.cbz").apply { writeText("original") }
        val internalLibDir = File(context.filesDir, "library").apply { mkdirs() }
        val internalCopy = File(internalLibDir, "copied.cbz").apply { writeText("copy") }

        try {
            val id1 = dao.insertBook(
                BookEntity(
                    title = "外部原文件漫画",
                    format = BookFormat.CBZ,
                    sourceUri = Uri.fromFile(originalExternalFile).toString(),
                    localCopyPath = originalExternalFile.absolutePath,
                    fileHash = "ext1",
                    fileSizeBytes = 8L,
                    totalUnits = 10
                )
            )
            val id2 = dao.insertBook(
                BookEntity(
                    title = "内部副本漫画",
                    format = BookFormat.ZIP,
                    sourceUri = "content://docs/2",
                    localCopyPath = internalCopy.absolutePath,
                    fileHash = "int2",
                    fileSizeBytes = 4L,
                    totalUnits = 5
                )
            )
            val id3 = dao.insertBook(
                BookEntity(
                    title = "保留书籍",
                    format = BookFormat.EPUB,
                    sourceUri = "content://docs/3",
                    fileHash = "keep3",
                    fileSizeBytes = 12L,
                    totalUnits = 3
                )
            )

            val b1 = dao.getBookWithTagsOnce(id1)!!.book
            val b2 = dao.getBookWithTagsOnce(id2)!!.book
            repo.deleteBooks(listOf(b1, b2))

            assertEquals(null, dao.getBookWithTagsOnce(id1))
            assertEquals(null, dao.getBookWithTagsOnce(id2))
            assertNotNull(dao.getBookWithTagsOnce(id3))

            // Original external file MUST still exist; app-internal copy is cleaned up
            assertTrue(originalExternalFile.exists())
            assertTrue(!internalCopy.exists())
        } finally {
            externalDir.deleteRecursively()
        }
    }

    @Test
    fun `localized natural comparator and bookshelf sorting support bidirectional all criteria`() {
        val titles = listOf("Vol.10", "Vol.2", "沙丘", "三体")
        val sortedTitles = titles.sortedWith(LocalizedNaturalComparator)
        assertEquals(listOf("Vol.2", "Vol.10", "三体", "沙丘"), sortedTitles)

        val b1 = com.example.data.local.entity.BookWithTags(
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
        val b2 = com.example.data.local.entity.BookWithTags(
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
        val b3 = com.example.data.local.entity.BookWithTags(
            book = BookEntity(
                id = 3L,
                title = "三体",
                author = "", // blank author should stay at bottom
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
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.TITLE_ASC, sortAscending = true).map { it.book.id }
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.TITLE_ASC, sortAscending = false).map { it.book.id }
        )

        // Author asc & desc (blank author always placed last)
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.AUTHOR_ASC, sortAscending = true).map { it.book.id }
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.AUTHOR_ASC, sortAscending = false).map { it.book.id }
        )

        // Recent read desc (500, 300, 300 -> tie break Vol.2 before Vol.10) & asc
        assertEquals(
            listOf(3L, 2L, 1L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.RECENT_READ, sortAscending = false).map { it.book.id }
        )
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.RECENT_READ, sortAscending = true).map { it.book.id }
        )

        // Added time desc & asc
        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.ADDED_DESC, sortAscending = false).map { it.book.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.ADDED_DESC, sortAscending = true).map { it.book.id }
        )

        // Reading progress desc & asc
        assertEquals(
            listOf(3L, 1L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.PROGRESS_DESC, sortAscending = false).map { it.book.id }
        )
        assertEquals(
            listOf(2L, 1L, 3L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.PROGRESS_DESC, sortAscending = true).map { it.book.id }
        )

        // File size desc & asc
        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.FILE_SIZE_DESC, sortAscending = false).map { it.book.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortBooks(books, BookshelfSortOrder.FILE_SIZE_DESC, sortAscending = true).map { it.book.id }
        )
    }

    @Test
    fun `collection sorting and preferences persist sort order and direction`() = runTest {
        val c1 = CollectionWithCount(CollectionEntity(id = 1L, name = "系列 10", createdAt = 100L), bookCount = 2)
        val c2 = CollectionWithCount(CollectionEntity(id = 2L, name = "系列 2", createdAt = 300L), bookCount = 8)
        val c3 = CollectionWithCount(CollectionEntity(id = 3L, name = "收藏精选", createdAt = 200L), bookCount = 5)
        val cols = listOf(c1, c2, c3)

        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.CREATED_DESC, sortAscending = false).map { it.collection.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.CREATED_DESC, sortAscending = true).map { it.collection.id }
        )
        assertEquals(
            listOf(3L, 2L, 1L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.NAME_ASC, sortAscending = true).map { it.collection.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.BOOK_COUNT_DESC, sortAscending = false).map { it.collection.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            BookshelfSearchEngine.sortCollections(cols, CollectionSortOrder.BOOK_COUNT_DESC, sortAscending = true).map { it.collection.id }
        )

        val prefsRepo = AppPreferencesRepository(context)
        prefsRepo.setSortOrder(BookshelfSortOrder.FILE_SIZE_DESC, ascending = true)
        prefsRepo.setCollectionSortOrder(CollectionSortOrder.BOOK_COUNT_DESC, ascending = true)
        val prefs = prefsRepo.preferencesFlow.first()
        assertEquals(BookshelfSortOrder.FILE_SIZE_DESC, prefs.sortOrder)
        assertEquals(true, prefs.sortAscending)
        assertEquals(CollectionSortOrder.BOOK_COUNT_DESC, prefs.collectionSortOrder)
        assertEquals(true, prefs.collectionSortAscending)
    }

    private fun createTinyPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
