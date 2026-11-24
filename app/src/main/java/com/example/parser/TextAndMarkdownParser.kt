package com.example.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class ParsedTextChapter(
    val index: Int,
    val title: String,
    val blocks: List<EpubContentBlock>
)

data class ParsedTextDocument(
    val title: String,
    val author: String,
    val tags: List<String>,
    val chapters: List<ParsedTextChapter>
)

object TextAndMarkdownParser {

    private val txtChapterHeaderRegex = Regex(
        """^\s*(?:第[零一二三四五六七八九十百千万两0-9]{1,8}[章节回卷集篇部节](?:\s+.*|：.*|:.*|$)|Chapter\s+\d+(?:\s+.*|:.*|\..*|$)|CHAPTER\s+\d+(?:\s+.*|:.*|\..*|$)|序章(?:\s+.*|$)|楔子(?:\s+.*|$)|尾声(?:\s+.*|$)|番外(?:\s+.*|$)|后记(?:\s+.*|$))""",
        RegexOption.IGNORE_CASE
    )
    private val txtAuthorLineRegex = Regex("""^\s*(?:作者|著|Author)\s*[:：]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val mdHeadingRegex = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
    private val mdHrRegex = Regex("""^\s*(?:[-*_]\s*){3,}$""")
    private val mdOrderedListRegex = Regex("""^\s*(\d+[.)])\s+(.+)$""")
    private val mdBulletListRegex = Regex("""^\s*[-*+]\s+(.+)$""")

    suspend fun loadBytes(
        context: Context,
        sourceUri: String,
        localCopyPath: String?
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!localCopyPath.isNullOrBlank()) {
            val localFile = File(localCopyPath)
            if (localFile.exists() && localFile.canRead()) {
                return@withContext localFile.readBytes()
            }
        }
        val uri = Uri.parse(sourceUri)
        if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (file.exists()) {
                return@withContext file.readBytes()
            }
        }
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取文本文件: $sourceUri")
    }

    fun isLikelyBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        // UTF-16 BOM is valid text even though it contains NUL bytes
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                return false
            }
        }
        val sampleSize = minOf(bytes.size, 2048)
        var nulCount = 0
        for (i in 0 until sampleSize) {
            if (bytes[i] == 0.toByte()) nulCount++
        }
        return nulCount > 2
    }

    fun decodeTextSmart(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            }
        }

        // Strict UTF-8 check before falling back to GB18030 / GBK
        val utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val utf8Result = runCatching {
            utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8Result != null) return utf8Result

        val gbCharset = runCatching { Charset.forName("GB18030") }
            .recoverCatching { Charset.forName("GBK") }
            .getOrDefault(StandardCharsets.UTF_8)
        return String(bytes, gbCharset)
    }

    fun parseTxt(bytes: ByteArray, fallbackTitle: String): ParsedTextDocument {
        val rawText = decodeTextSmart(bytes)
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val lines = rawText.lines()
        var detectedAuthor = ""
        var authorLineIdx = -1
        for ((idx, line) in lines.take(12).withIndex()) {
            val match = txtAuthorLineRegex.matchEntire(line)
            if (match != null) {
                detectedAuthor = match.groupValues[1].trim().take(60)
                authorLineIdx = idx
                break
            }
        }

        val chapters = mutableListOf<ParsedTextChapter>()
        var currentTitle = "正文"
        val currentBlocks = mutableListOf<EpubContentBlock>()
        var currentChars = 0

        fun flushSection(nextTitle: String) {
            if (currentBlocks.isNotEmpty()) {
                chapters.add(
                    ParsedTextChapter(
                        index = chapters.size,
                        title = currentTitle,
                        blocks = currentBlocks.toList()
                    )
                )
                currentBlocks.clear()
                currentChars = 0
            }
            currentTitle = nextTitle
        }

        var partCounter = 1
        for ((idx, rawLine) in lines.withIndex()) {
            if (idx == authorLineIdx) continue
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.length <= 64 && txtChapterHeaderRegex.matches(line)) {
                flushSection(line)
                partCounter = 1
                continue
            }

            if (currentChars >= 6_000 && currentBlocks.isNotEmpty()) {
                val baseName = currentTitle.substringBefore(" (")
                partCounter++
                flushSection("$baseName ($partCounter)")
            }
            currentBlocks.add(EpubContentBlock.Paragraph(line))
            currentChars += line.length
        }
        flushSection("")

        if (chapters.isEmpty()) {
            chapters.add(
                ParsedTextChapter(
                    index = 0,
                    title = fallbackTitle.ifBlank { "正文" },
                    blocks = listOf(EpubContentBlock.Paragraph("（文档内容为空）"))
                )
            )
        } else if (chapters.size > 1 && chapters.first().title == "正文") {
            chapters[0] = chapters[0].copy(title = "序言 / 开始")
        }

        val reindexed = chapters.mapIndexed { idx, ch -> ch.copy(index = idx) }
        return ParsedTextDocument(
            title = fallbackTitle,
            author = detectedAuthor,
            tags = emptyList(),
            chapters = reindexed
        )
    }

    fun parseMarkdown(bytes: ByteArray, fallbackTitle: String): ParsedTextDocument {
        var rawText = decodeTextSmart(bytes)
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        var title = ""
        var author = ""
        val tags = mutableListOf<String>()

        // Optional YAML front-matter
        if (rawText.startsWith("---\n")) {
            val endIdx = rawText.indexOf("\n---\n", startIndex = 4)
            if (endIdx > 0) {
                val yaml = rawText.substring(4, endIdx)
                rawText = rawText.substring(endIdx + 5)
                yaml.lines().forEach { line ->
                    val key = line.substringBefore(':', "").trim().lowercase()
                    val value = line.substringAfter(':', "").trim().trim('"', '\'')
                    when (key) {
                        "title" -> if (value.isNotBlank()) title = value
                        "author" -> if (value.isNotBlank()) author = value
                        "tags" -> {
                            value.removePrefix("[").removeSuffix("]")
                                .split(',')
                                .map { it.trim().trim('"', '\'') }
                                .filter { it.isNotBlank() }
                                .forEach { tags.add(it) }
                        }
                    }
                }
            }
        }

        val lines = rawText.lines()
        val rawBlocks = mutableListOf<Pair<Int, EpubContentBlock>>()
        var i = 0
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val joined = paragraphBuffer.joinToString(" ").trim()
                if (joined.isNotBlank()) {
                    rawBlocks.add(0 to EpubContentBlock.Paragraph(cleanMarkdownLinks(joined)))
                }
                paragraphBuffer.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Fenced code block
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushParagraph()
                val fence = if (trimmed.startsWith("```")) "```" else "~~~"
                val lang = trimmed.removePrefix(fence).trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    codeLines.add(lines[i])
                    i++
                }
                rawBlocks.add(0 to EpubContentBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                i++
                continue
            }

            if (trimmed.isEmpty()) {
                flushParagraph()
                i++
                continue
            }

            if (mdHrRegex.matches(trimmed)) {
                flushParagraph()
                rawBlocks.add(0 to EpubContentBlock.DividerBlock)
                i++
                continue
            }

            val headingMatch = mdHeadingRegex.matchEntire(trimmed)
            if (headingMatch != null) {
                flushParagraph()
                val level = headingMatch.groupValues[1].length
                val headingText = cleanMarkdownLinks(headingMatch.groupValues[2].trim())
                if (title.isBlank() && level == 1) {
                    title = headingText
                }
                rawBlocks.add(level to EpubContentBlock.Heading(level, headingText))
                i++
                continue
            }

            if (trimmed.startsWith(">")) {
                flushParagraph()
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                val quoteText = cleanMarkdownLinks(quoteLines.joinToString("\n").trim())
                if (quoteText.isNotBlank()) {
                    rawBlocks.add(0 to EpubContentBlock.Quote(quoteText))
                }
                continue
            }

            val bulletMatch = mdBulletListRegex.matchEntire(line)
            if (bulletMatch != null) {
                flushParagraph()
                val itemText = cleanMarkdownLinks(bulletMatch.groupValues[1].trim())
                rawBlocks.add(0 to EpubContentBlock.Paragraph("• $itemText"))
                i++
                continue
            }

            val orderedMatch = mdOrderedListRegex.matchEntire(line)
            if (orderedMatch != null) {
                flushParagraph()
                val prefix = orderedMatch.groupValues[1]
                val itemText = cleanMarkdownLinks(orderedMatch.groupValues[2].trim())
                rawBlocks.add(0 to EpubContentBlock.Paragraph("$prefix $itemText"))
                i++
                continue
            }

            paragraphBuffer.add(trimmed)
            i++
        }
        flushParagraph()

        // Split chapters cleanly on H1/H2 headings (or chunk by size if no headings)
        val splitLevel = when {
            rawBlocks.count { it.first == 1 } >= 2 -> 1
            rawBlocks.count { it.first in 1..2 } >= 2 -> 2
            else -> 0
        }

        val chapters = mutableListOf<ParsedTextChapter>()
        var currentTitle = title.ifBlank { fallbackTitle.ifBlank { "正文" } }
        val sectionBlocks = mutableListOf<EpubContentBlock>()
        var sectionChars = 0

        fun flushChapter(nextTitle: String) {
            if (sectionBlocks.isNotEmpty()) {
                chapters.add(
                    ParsedTextChapter(
                        index = chapters.size,
                        title = currentTitle,
                        blocks = sectionBlocks.toList()
                    )
                )
                sectionBlocks.clear()
                sectionChars = 0
            }
            currentTitle = nextTitle
        }

        var partCounter = 1
        for ((level, block) in rawBlocks) {
            if (splitLevel > 0 && level in 1..splitLevel && block is EpubContentBlock.Heading) {
                if (sectionBlocks.isNotEmpty()) {
                    flushChapter(block.text)
                    partCounter = 1
                } else {
                    currentTitle = block.text
                }
                continue
            }
            if (splitLevel == 0 && sectionChars >= 6_000 && sectionBlocks.isNotEmpty()) {
                val baseName = currentTitle.substringBefore(" (")
                partCounter++
                flushChapter("$baseName ($partCounter)")
            }
            sectionBlocks.add(block)
            sectionChars += when (block) {
                is EpubContentBlock.Heading -> block.text.length
                is EpubContentBlock.Paragraph -> block.text.length
                is EpubContentBlock.Quote -> block.text.length
                is EpubContentBlock.CodeBlock -> block.code.length
                else -> 10
            }
        }
        flushChapter("")

        if (chapters.isEmpty()) {
            chapters.add(
                ParsedTextChapter(
                    index = 0,
                    title = title.ifBlank { fallbackTitle },
                    blocks = listOf(EpubContentBlock.Paragraph("（Markdown 文档内容为空）"))
                )
            )
        }

        val reindexed = chapters.mapIndexed { idx, ch -> ch.copy(index = idx) }
        return ParsedTextDocument(
            title = title.ifBlank { fallbackTitle },
            author = author,
            tags = tags.distinct(),
            chapters = reindexed
        )
    }

    private fun cleanMarkdownLinks(text: String): String {
        return text
            .replace(Regex("""!\[([^\]]*)\]\([^)]+\)""")) { m ->
                val alt = m.groupValues[1].trim()
                if (alt.isNotBlank()) "[图片: $alt]" else "[图片]"
            }
            .replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
    }
}
