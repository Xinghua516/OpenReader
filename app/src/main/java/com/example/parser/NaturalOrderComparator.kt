package com.example.parser

import com.example.data.local.entity.BookFormat
import java.io.InputStream
import java.security.MessageDigest

object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val la = a.length
        val lb = b.length

        while (ia < la && ib < lb) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                var za = ia
                while (za < la && a[za] == '0') za++
                var zb = ib
                while (zb < lb && b[zb] == '0') zb++

                var ea = za
                while (ea < la && a[ea].isDigit()) ea++
                var eb = zb
                while (eb < lb && b[eb].isDigit()) eb++

                val lenA = ea - za
                val lenB = eb - zb
                if (lenA != lenB) return lenA.compareTo(lenB)

                for (k in 0 until lenA) {
                    val diff = a[za + k].compareTo(b[zb + k])
                    if (diff != 0) return diff
                }

                // Prefer fewer leading zeros if numerically equal
                val leadA = za - ia
                val leadB = zb - ib
                if (leadA != leadB) return leadA.compareTo(leadB)

                ia = ea
                ib = eb
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                ia++
                ib++
            }
        }
        return la.compareTo(lb)
    }
}

data class FilenameMetadata(
    val title: String,
    val author: String,
    val suggestedTags: List<String>
)

object FilenameMetadataParser {
    private val bracketAuthorRegex = Regex("""^[\[\(【（]([^\]\)】）]{1,40})[\]\)】）]\s*(.+)$""")
    private val trailingBracketRegex = Regex("""(.+?)\s*[\[\(【（]([^\]\)】）]{1,30})[\]\)】）]$""")
    private val extensionRegex = Regex("""\.(zip|cbz|epub|pdf|txt|md|markdown)$""", RegexOption.IGNORE_CASE)

    fun parse(rawFileName: String, format: BookFormat): FilenameMetadata {
        val base = rawFileName.substringAfterLast('/').replace(extensionRegex, "").trim()
        var author = ""
        var title = base
        val tags = mutableListOf<String>()

        bracketAuthorRegex.matchEntire(base)?.let { match ->
            val candidate = match.groupValues[1].trim()
            if (!candidate.equals("汉化", ignoreCase = true) && !candidate.contains("扫")) {
                author = candidate
            }
            title = match.groupValues[2].trim()
        } ?: trailingBracketRegex.matchEntire(base)?.let { match ->
            val tail = match.groupValues[2].trim()
            if (tail.contains("完结") || tail.contains("全")) {
                tags.add("已完结")
            }
            title = match.groupValues[1].trim()
        }

        val lower = base.lowercase()
        if (lower.contains("webtoon") || lower.contains("条漫") || lower.contains("longstrip")) {
            tags.add("条漫")
        }
        if (lower.contains("科幻") || lower.contains("sci-fi") || lower.contains("cyber")) {
            tags.add("科幻")
        }
        if (lower.contains("奇幻") || lower.contains("fantasy") || lower.contains("异世界")) {
            tags.add("奇幻")
        }
        if (lower.contains("悬疑") || lower.contains("推理") || lower.contains("mystery")) {
            tags.add("悬疑")
        }

        when (format) {
            BookFormat.EPUB -> tags.add("EPUB")
            BookFormat.CBZ, BookFormat.ZIP -> tags.add("ZIP/CBZ")
            BookFormat.PDF -> tags.add("PDF")
            BookFormat.TXT -> tags.add("TXT")
            BookFormat.MD -> tags.add("Markdown")
        }

        val cleanTitle = title.replace('_', ' ').trim().ifBlank { rawFileName }
        return FilenameMetadata(
            title = cleanTitle,
            author = author,
            suggestedTags = tags.distinct()
        )
    }

    fun detectFormatByName(fileName: String, mimeType: String?): BookFormat? {
        val lower = fileName.lowercase()
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            lower.endsWith(".epub") || mime == "application/epub+zip" -> BookFormat.EPUB
            lower.endsWith(".cbz") || mime == "application/x-cbz" || mime == "application/vnd.comicbook+zip" -> BookFormat.CBZ
            lower.endsWith(".zip") || mime == "application/zip" || mime == "application/x-zip-compressed" -> BookFormat.ZIP
            lower.endsWith(".pdf") || mime == "application/pdf" -> BookFormat.PDF
            lower.endsWith(".md") || lower.endsWith(".markdown") || mime == "text/markdown" || mime == "text/x-markdown" -> BookFormat.MD
            lower.endsWith(".txt") || mime == "text/plain" -> BookFormat.TXT
            else -> null
        }
    }

    fun isImageEntry(entryName: String): Boolean {
        val lower = entryName.lowercase()
        if (lower.startsWith("__macosx/") || lower.contains("/__macosx/") || lower.substringAfterLast('/').startsWith(".")) {
            return false
        }
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".bmp") ||
            lower.endsWith(".gif")
    }

    fun computeSha256AndSize(inputStream: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        var totalBytes = 0L
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
            totalBytes += read
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex to totalBytes
    }
}
