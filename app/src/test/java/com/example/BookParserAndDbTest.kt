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
import com.example.data.local.entity.TagEntity
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.ImportTaskStatus
import com.example.data.repository.LibraryRepository
import com.example.parser.ArchiveSession
import com.example.parser.BookImporterParser
import com.example.parser.EpubContentBlock
import com.example.parser.EpubParser
import com.example.parser.FilenameMetadataParser
import com.example.parser.NaturalOrderComparator
import com.example.parser.TextAndMarkdownParser
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

    private fun createTinyPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
