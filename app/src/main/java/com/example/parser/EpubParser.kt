package com.example.parser

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class EpubChapter(
    val index: Int,
    val title: String,
    val entryPath: String
)

data class EpubBookStructure(
    val title: String,
    val author: String,
    val language: String,
    val subjects: List<String>,
    val coverEntryPath: String?,
    val chapters: List<EpubChapter>
)

sealed interface EpubContentBlock {
    data class Heading(val level: Int, val text: String) : EpubContentBlock
    data class Paragraph(val text: String) : EpubContentBlock
    data class Quote(val text: String) : EpubContentBlock
    data class CodeBlock(val language: String, val code: String) : EpubContentBlock
    data object DividerBlock : EpubContentBlock
    data class ImageBlock(val entryPath: String, val altText: String) : EpubContentBlock
}

object EpubParser {

    fun parseStructure(session: ArchiveSession): EpubBookStructure {
        val containerXml = session.findEntryIgnoreCase("META-INF/container.xml")
            ?.let { session.readEntryBytes(it)?.toString(StandardCharsets.UTF_8) }

        val opfPath = containerXml?.let { extractOpfPath(it) }
            ?: session.entryNames.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: return fallbackStructure(session)

        val opfBytes = session.readEntryBytes(opfPath) ?: return fallbackStructure(session)
        val opfXml = opfBytes.toString(StandardCharsets.UTF_8)
        val opfDir = opfPath.substringBeforeLast('/', "")

        return parseOpf(opfXml, opfDir, session)
    }

    private fun extractOpfPath(containerXml: String): String? {
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(containerXml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                    val path = parser.getAttributeValue(null, "full-path")
                    if (!path.isNullOrBlank()) return@runCatching decodePath(path)
                }
                event = parser.next()
            }
            null
        }.getOrNull()
    }

    private fun parseOpf(opfXml: String, opfDir: String, session: ArchiveSession): EpubBookStructure {
        var title = ""
        var author = ""
        var language = ""
        val subjects = mutableListOf<String>()
        var coverIdFromMeta: String? = null
        var coverHrefFromProperties: String? = null
        var ncxHref: String? = null
        var navHref: String? = null

        val manifestById = linkedMapOf<String, ManifestItem>()
        val spineIds = mutableListOf<String>()

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(opfXml))

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val tag = parser.name.substringAfter(':').lowercase()
                    when (tag) {
                        "title" -> {
                            val text = parser.nextText().trim()
                            if (title.isBlank() && text.isNotBlank()) title = text
                        }
                        "creator" -> {
                            val text = parser.nextText().trim()
                            if (text.isNotBlank()) {
                                author = if (author.isBlank()) text else "$author, $text"
                            }
                        }
                        "language" -> {
                            val text = parser.nextText().trim()
                            if (language.isBlank()) language = text
                        }
                        "subject" -> {
                            val text = parser.nextText().trim()
                            if (text.isNotBlank()) subjects.add(text)
                        }
                        "meta" -> {
                            val nameAttr = parser.getAttributeValue(null, "name")
                            val contentAttr = parser.getAttributeValue(null, "content")
                            if (nameAttr.equals("cover", ignoreCase = true) && !contentAttr.isNullOrBlank()) {
                                coverIdFromMeta = contentAttr
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id").orEmpty()
                            val href = parser.getAttributeValue(null, "href")?.let { decodePath(it) }.orEmpty()
                            val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                            val props = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id.isNotBlank() && href.isNotBlank()) {
                                manifestById[id] = ManifestItem(id, href, mediaType, props)
                                if (props.split(' ').any { it == "cover-image" }) {
                                    coverHrefFromProperties = resolveRelativePath(opfDir, href)
                                }
                                if (props.split(' ').any { it == "nav" }) {
                                    navHref = resolveRelativePath(opfDir, href)
                                }
                                if (mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
                                    href.endsWith(".ncx", ignoreCase = true)
                                ) {
                                    ncxHref = resolveRelativePath(opfDir, href)
                                }
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) spineIds.add(idref)
                        }
                    }
                }
                event = parser.next()
            }
        }

        val tocTitles = mutableMapOf<String, String>()
        navHref?.let { path ->
            session.readEntryBytes(path)?.toString(StandardCharsets.UTF_8)?.let { navXml ->
                val navDir = path.substringBeforeLast('/', "")
                parseNavTitles(navXml, navDir, tocTitles)
            }
        }
        if (tocTitles.isEmpty() && ncxHref != null) {
            session.readEntryBytes(ncxHref!!)?.toString(StandardCharsets.UTF_8)?.let { ncxXml ->
                val ncxDir = ncxHref!!.substringBeforeLast('/', "")
                parseNcxTitles(ncxXml, ncxDir, tocTitles)
            }
        }

        val chapters = mutableListOf<EpubChapter>()
        spineIds.forEachIndexed { idx, idref ->
            val item = manifestById[idref] ?: return@forEachIndexed
            val resolved = resolveRelativePath(opfDir, item.href)
            if (session.hasEntry(resolved)) {
                val label = tocTitles[resolved]
                    ?: tocTitles[resolved.substringAfterLast('/')]
                    ?: extractHtmlTitle(session.readEntryBytes(resolved)?.toString(StandardCharsets.UTF_8))
                    ?: "第 ${chapters.size + 1} 章"
                chapters.add(
                    EpubChapter(
                        index = chapters.size,
                        title = label,
                        entryPath = resolved
                    )
                )
            }
        }

        if (chapters.isEmpty()) {
            val htmlEntries = session.entryNames
                .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
                .sortedWith(NaturalOrderComparator)
            htmlEntries.forEachIndexed { idx, path ->
                chapters.add(
                    EpubChapter(
                        index = idx,
                        title = tocTitles[path] ?: "第 ${idx + 1} 章",
                        entryPath = path
                    )
                )
            }
        }

        val coverEntryPath = coverHrefFromProperties
            ?: coverIdFromMeta?.let { id -> manifestById[id]?.let { resolveRelativePath(opfDir, it.href) } }
            ?: manifestById.values.firstOrNull {
                it.mediaType.startsWith("image/") && it.id.contains("cover", ignoreCase = true)
            }?.let { resolveRelativePath(opfDir, it.href) }
            ?: session.sortedImageEntries.firstOrNull()

        return EpubBookStructure(
            title = title,
            author = author,
            language = language,
            subjects = subjects.distinct(),
            coverEntryPath = coverEntryPath?.takeIf { session.hasEntry(it) },
            chapters = chapters
        )
    }

    private fun fallbackStructure(session: ArchiveSession): EpubBookStructure {
        val htmlEntries = session.entryNames
            .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
            .sortedWith(NaturalOrderComparator)
        val chapters = htmlEntries.mapIndexed { index, path ->
            EpubChapter(index = index, title = "第 ${index + 1} 章", entryPath = path)
        }
        return EpubBookStructure(
            title = "",
            author = "",
            language = "",
            subjects = emptyList(),
            coverEntryPath = session.sortedImageEntries.firstOrNull(),
            chapters = chapters
        )
    }

    private fun parseNcxTitles(ncxXml: String, baseDir: String, out: MutableMap<String, String>) {
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(ncxXml))
            var currentLabel = ""
            var insideNavLabel = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.substringAfter(':').lowercase()
                        when (tag) {
                            "navlabel" -> insideNavLabel = true
                            "text" -> if (insideNavLabel) currentLabel = parser.nextText().trim()
                            "content" -> {
                                val src = parser.getAttributeValue(null, "src")
                                if (!src.isNullOrBlank() && currentLabel.isNotBlank()) {
                                    val cleanSrc = decodePath(src.substringBefore('#'))
                                    val resolved = resolveRelativePath(baseDir, cleanSrc)
                                    out.putIfAbsent(resolved, currentLabel)
                                    out.putIfAbsent(resolved.substringAfterLast('/'), currentLabel)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.substringAfter(':').lowercase() == "navlabel") {
                            insideNavLabel = false
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun parseNavTitles(navXml: String, baseDir: String, out: MutableMap<String, String>) {
        val linkRegex = Regex("""<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        linkRegex.findAll(navXml).forEach { match ->
            val rawHref = decodePath(match.groupValues[1].substringBefore('#'))
            val label = stripHtmlTags(match.groupValues[2]).trim()
            if (rawHref.isNotBlank() && label.isNotBlank()) {
                val resolved = resolveRelativePath(baseDir, rawHref)
                out.putIfAbsent(resolved, label)
                out.putIfAbsent(resolved.substringAfterLast('/'), label)
            }
        }
    }

    private fun extractHtmlTitle(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val hMatch = Regex("""<h[12][^>]*>(.*?)</h[12]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val hText = hMatch?.groupValues?.get(1)?.let { stripHtmlTags(it).trim() }
        if (!hText.isNullOrBlank() && hText.length <= 80) return hText

        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val tText = titleMatch?.groupValues?.get(1)?.let { stripHtmlTags(it).trim() }
        return tText?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) && it.length <= 80 }
    }

    fun parseChapterContent(session: ArchiveSession, chapterPath: String): List<EpubContentBlock> {
        val raw = session.readEntryBytes(chapterPath)?.toString(StandardCharsets.UTF_8) ?: return emptyList()
        val chapterDir = chapterPath.substringBeforeLast('/', "")
        val bodyContent = Regex("""<body[^>]*>(.*)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(raw)?.groupValues?.get(1) ?: raw

        val blocks = mutableListOf<EpubContentBlock>()
        val tokenRegex = Regex(
            """<(h[1-6]|p|blockquote|li|div)[^>]*>(.*?)</\1>|<img\b([^>]*)/?>|<image\b([^>]*)/?>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val cleanedBody = bodyContent
            .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")

        val imgAttrRegex = Regex("""(?:src|xlink:href|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val altAttrRegex = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

        val matches = tokenRegex.findAll(cleanedBody).toList()
        for (match in matches) {
            val tag = match.groupValues[1].lowercase()
            val innerHtml = match.groupValues[2]
            val imgAttrs = match.groupValues[3].ifBlank { match.groupValues[4] }

            if (imgAttrs.isNotBlank()) {
                val src = imgAttrRegex.find(imgAttrs)?.groupValues?.get(1)
                if (!src.isNullOrBlank()) {
                    val resolved = resolveRelativePath(chapterDir, decodePath(src.substringBefore('#')))
                    if (session.hasEntry(resolved)) {
                        val alt = altAttrRegex.find(imgAttrs)?.groupValues?.get(1).orEmpty()
                        blocks.add(EpubContentBlock.ImageBlock(resolved, alt))
                    }
                }
                continue
            }

            if (tag == "div" && innerHtml.contains("<p", ignoreCase = true)) {
                // Skip container div that wraps inner <p> tags to avoid duplication; parse inner recursively
                val innerMatches = tokenRegex.findAll(innerHtml)
                innerMatches.forEach { inner ->
                    val iTag = inner.groupValues[1].lowercase()
                    val iHtml = inner.groupValues[2]
                    val iImg = inner.groupValues[3].ifBlank { inner.groupValues[4] }
                    if (iImg.isNotBlank()) {
                        val src = imgAttrRegex.find(iImg)?.groupValues?.get(1)
                        if (!src.isNullOrBlank()) {
                            val resolved = resolveRelativePath(chapterDir, decodePath(src.substringBefore('#')))
                            if (session.hasEntry(resolved)) {
                                blocks.add(EpubContentBlock.ImageBlock(resolved, ""))
                            }
                        }
                    } else {
                        appendTextBlock(iTag, iHtml, chapterDir, session, blocks, imgAttrRegex)
                    }
                }
                continue
            }

            appendTextBlock(tag, innerHtml, chapterDir, session, blocks, imgAttrRegex)
        }

        if (blocks.isEmpty()) {
            val fallbackLines = stripHtmlTags(cleanedBody)
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            fallbackLines.forEach { blocks.add(EpubContentBlock.Paragraph(it)) }
        }

        return blocks
    }

    private fun appendTextBlock(
        tag: String,
        innerHtml: String,
        chapterDir: String,
        session: ArchiveSession,
        blocks: MutableList<EpubContentBlock>,
        imgAttrRegex: Regex
    ) {
        // Extract any inline images inside <p> or <div>
        val inlineImgRegex = Regex("""<(?:img|image)\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
        inlineImgRegex.findAll(innerHtml).forEach { imgMatch ->
            val src = imgAttrRegex.find(imgMatch.groupValues[1])?.groupValues?.get(1)
            if (!src.isNullOrBlank()) {
                val resolved = resolveRelativePath(chapterDir, decodePath(src.substringBefore('#')))
                if (session.hasEntry(resolved)) {
                    blocks.add(EpubContentBlock.ImageBlock(resolved, ""))
                }
            }
        }

        val text = stripHtmlTags(innerHtml)
            .replace('\u00A0', ' ')
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
        if (text.isBlank()) return

        when {
            tag.startsWith("h") -> {
                val level = tag.drop(1).toIntOrNull() ?: 2
                blocks.add(EpubContentBlock.Heading(level, text))
            }
            tag == "blockquote" -> blocks.add(EpubContentBlock.Quote(text))
            tag == "li" -> blocks.add(EpubContentBlock.Paragraph("• $text"))
            else -> blocks.add(EpubContentBlock.Paragraph(text))
        }
    }

    private fun stripHtmlTags(html: String): String {
        val withBreaks = html.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    fun resolveRelativePath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.trimStart('/')
        val combined = if (baseDir.isBlank() || relativeHref.startsWith('/')) {
            cleanHref
        } else {
            "$baseDir/$cleanHref"
        }
        val parts = ArrayDeque<String>()
        combined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun decodePath(path: String): String {
        return runCatching { URLDecoder.decode(path, StandardCharsets.UTF_8.name()) }.getOrDefault(path)
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )
}
