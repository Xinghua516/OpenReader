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

    private fun createTinyPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
