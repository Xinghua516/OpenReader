package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.entity.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class ParsedBookMetadata(
    val title: String,
    val author: String,
    val format: BookFormat,
    val fileHash: String,
    val fileSizeBytes: Long,
    val coverPath: String?,
    val localCopyPath: String?,
    val totalUnits: Int,
    val rawFileName: String,
    val suggestedTags: List<String>,
    val statusDetail: String
)

object BookImporterParser {

    fun queryDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "unknown"
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported_book"
    }

    suspend fun parseUri(
        context: Context,
        uri: Uri,
        autoCacheCover: Boolean,
        autoTagByFilename: Boolean,
        copyToInternalStorage: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): ParsedBookMetadata = withContext(Dispatchers.IO) {
        val rawFileName = queryDisplayName(context, uri)
        val mimeType = context.contentResolver.getType(uri)
        val detectedFormat = FilenameMetadataParser.detectFormatByName(rawFileName, mimeType)

        val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
        var stagingFile: File? = null

        val (fileHash, fileSizeBytes) = if (copyToInternalStorage) {
            onProgress(0.12f, "正在导入至应用内部存储…")
            val tmpFile = File(libraryDir, "staging_${UUID.randomUUID()}.tmp")
            stagingFile = tmpFile
            try {
                copyStreamWithSha256(context, uri, rawFileName, tmpFile)
            } catch (e: Exception) {
                tmpFile.delete()
                throw e
            }
        } else {
            onProgress(0.15f, "正在校验文件哈希…")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                FilenameMetadataParser.computeSha256AndSize(stream)
            } ?: throw IllegalStateException("无法打开文件: $rawFileName")
        }

        try {
            if (fileSizeBytes < 1L) {
                throw IllegalStateException("文件内容为空或无效: $rawFileName")
            }

            // Detect magic headers if staging file is available or format wasn't recognized strictly by suffix
            val resolvedInitialFormat = resolveFormatWithHeader(detectedFormat, stagingFile, rawFileName)

            when (resolvedInitialFormat) {
                BookFormat.PDF -> {
                    if (stagingFile != null && !PdfSession.hasPdfMagicHeader(stagingFile)) {
                        throw IllegalStateException("无效的 PDF 文件: $rawFileName")
                    }
                    val persistedInternalPath = persistStagingFile(stagingFile, libraryDir, fileHash, BookFormat.PDF)
                    stagingFile = null

                    onProgress(0.45f, "正在解析 PDF 页面结构…")
                    PdfSession.open(
                        context = context,
                        sourceUri = uri.toString(),
                        localCopyPath = persistedInternalPath,
                        cacheKey = fileHash
                    ).use { pdf ->
                        val pageCount = pdf.pageCount
                        if (pageCount <= 0) {
                            throw IllegalStateException("PDF 文件不包含有效页面: $rawFileName")
                        }
                        var coverPath: String? = null
                        if (autoCacheCover) {
                            onProgress(0.82f, "正在渲染 PDF 首页封面…")
                            val bmp = pdf.renderPage(0, maxDimension = 540, trimWhiteMargins = false)
                            if (bmp != null) {
                                coverPath = saveBitmapThumbnail(context, bmp, fileHash)
                            }
                        }

                        val filenameMeta = FilenameMetadataParser.parse(rawFileName, BookFormat.PDF)
                        val tags = if (autoTagByFilename) filenameMeta.suggestedTags else emptyList()
                        val sizeText = formatSizeText(fileSizeBytes)
                        val storageLabel = if (persistedInternalPath != null) "已存入内部存储 · " else ""
                        val detail = "${storageLabel}PDF 共 $pageCount 页解析完成 · $sizeText"
                        onProgress(1f, detail)

                        ParsedBookMetadata(
                            title = filenameMeta.title,
                            author = filenameMeta.author,
                            format = BookFormat.PDF,
                            fileHash = fileHash,
                            fileSizeBytes = fileSizeBytes,
                            coverPath = coverPath,
                            localCopyPath = persistedInternalPath,
                            totalUnits = pageCount,
                            rawFileName = rawFileName,
                            suggestedTags = tags,
                            statusDetail = detail
                        )
                    }
                }

                BookFormat.TXT, BookFormat.MD -> {
                    val persistedInternalPath = persistStagingFile(stagingFile, libraryDir, fileHash, resolvedInitialFormat)
                    stagingFile = null

                    onProgress(0.45f, "正在识别文本编码与章节目录…")
                    val bytes = TextAndMarkdownParser.loadBytes(
                        context = context,
                        sourceUri = uri.toString(),
                        localCopyPath = persistedInternalPath
                    )
                    if (TextAndMarkdownParser.isLikelyBinary(bytes)) {
                        persistedInternalPath?.let { File(it).delete() }
                        throw IllegalStateException("文件包含二进制内容，无法作为 ${resolvedInitialFormat.badgeLabel} 文本读取")
                    }

                    val filenameMeta = FilenameMetadataParser.parse(rawFileName, resolvedInitialFormat)
                    val parsedDoc = if (resolvedInitialFormat == BookFormat.MD) {
                        TextAndMarkdownParser.parseMarkdown(bytes, filenameMeta.title)
                    } else {
                        TextAndMarkdownParser.parseTxt(bytes, filenameMeta.title)
                    }

                    val title = parsedDoc.title.ifBlank { filenameMeta.title }
                    val author = parsedDoc.author.ifBlank { filenameMeta.author }
                    val tags = mutableListOf<String>()
                    if (autoTagByFilename) {
                        tags.addAll(filenameMeta.suggestedTags)
                        tags.addAll(parsedDoc.tags.take(4))
                    }

                    val sizeText = formatSizeText(fileSizeBytes)
                    val storageLabel = if (persistedInternalPath != null) "已存入内部存储 · " else ""
                    val unitCount = parsedDoc.chapters.size.coerceAtLeast(1)
                    val detail = "${storageLabel}${resolvedInitialFormat.badgeLabel} 共 $unitCount 节解析完成 · $sizeText"
                    onProgress(1f, detail)

                    ParsedBookMetadata(
                        title = title,
                        author = author,
                        format = resolvedInitialFormat,
                        fileHash = fileHash,
                        fileSizeBytes = fileSizeBytes,
                        coverPath = null,
                        localCopyPath = persistedInternalPath,
                        totalUnits = unitCount,
                        rawFileName = rawFileName,
                        suggestedTags = tags.distinct(),
                        statusDetail = detail
                    )
                }

                BookFormat.CBZ, BookFormat.ZIP, BookFormat.EPUB -> {
                    if (fileSizeBytes < 4L || (stagingFile != null && !hasZipMagicHeader(stagingFile))) {
                        throw IllegalStateException("仅支持有效的 .zip、.cbz、.epub、.pdf、.txt 或 .md 文件")
                    }

                    onProgress(0.35f, "正在解析压缩结构…")
                    ArchiveSession.open(
                        context = context,
                        sourceUri = uri.toString(),
                        localCopyPath = stagingFile?.absolutePath,
                        cacheKey = fileHash
                    ).use { session ->
                        val isEpubArchive = resolvedInitialFormat == BookFormat.EPUB ||
                            session.findEntryIgnoreCase("META-INF/container.xml") != null ||
                            session.entryNames.any { it.endsWith(".opf", ignoreCase = true) }

                        val finalFormat = if (isEpubArchive) {
                            BookFormat.EPUB
                        } else {
                            resolvedInitialFormat
                        }

                        val persistedInternalPath = persistStagingFile(stagingFile, libraryDir, fileHash, finalFormat)
                        stagingFile = null

                        val filenameMeta = FilenameMetadataParser.parse(rawFileName, finalFormat)

                        if (finalFormat == BookFormat.EPUB) {
                            onProgress(0.60f, "正在解析 EPUB 元数据与目录…")
                            val structure = EpubParser.parseStructure(session)
                            val title = structure.title.ifBlank { filenameMeta.title }
                            val author = structure.author.ifBlank { filenameMeta.author }

                            var coverPath: String? = null
                            if (autoCacheCover && structure.coverEntryPath != null) {
                                onProgress(0.82f, "正在生成 EPUB 封面缩略图…")
                                coverPath = saveCoverThumbnail(context, session, structure.coverEntryPath, fileHash)
                            }

                            val tags = mutableListOf<String>()
                            if (autoTagByFilename) {
                                tags.addAll(filenameMeta.suggestedTags)
                                tags.addAll(structure.subjects.take(4))
                            }

                            val sizeText = formatSizeText(fileSizeBytes)
                            val storageLabel = if (persistedInternalPath != null) "已存入内部存储 · " else ""
                            val detail = "${storageLabel}EPUB 元数据与 ${structure.chapters.size} 章节解析完成 · $sizeText"
                            onProgress(1f, detail)

                            ParsedBookMetadata(
                                title = title,
                                author = author,
                                format = BookFormat.EPUB,
                                fileHash = fileHash,
                                fileSizeBytes = fileSizeBytes,
                                coverPath = coverPath,
                                localCopyPath = persistedInternalPath,
                                totalUnits = structure.chapters.size.coerceAtLeast(1),
                                rawFileName = rawFileName,
                                suggestedTags = tags.distinct(),
                                statusDetail = detail
                            )
                        } else {
                            onProgress(0.60f, "${finalFormat.badgeLabel} 图片序列校验中…")
                            val pages = session.sortedImageEntries
                            if (pages.isEmpty()) {
                                throw IllegalStateException("压缩包中未检测到支持的漫画图片 (.jpg/.png/.webp)")
                            }

                            var coverPath: String? = null
                            if (autoCacheCover) {
                                onProgress(0.85f, "正在提取封面缩略图 · ${pages.size} 页")
                                coverPath = saveCoverThumbnail(context, session, pages.first(), fileHash)
                            }

                            val tags = if (autoTagByFilename) filenameMeta.suggestedTags else emptyList()
                            val storageLabel = if (persistedInternalPath != null) "已存入内部存储 · " else ""
                            val detail = "${storageLabel}${finalFormat.badgeLabel} 图片结构校验完成 · ${pages.size} 页"
                            onProgress(1f, detail)

                            ParsedBookMetadata(
                                title = filenameMeta.title,
                                author = filenameMeta.author,
                                format = finalFormat,
                                fileHash = fileHash,
                                fileSizeBytes = fileSizeBytes,
                                coverPath = coverPath,
                                localCopyPath = persistedInternalPath,
                                totalUnits = pages.size,
                                rawFileName = rawFileName,
                                suggestedTags = tags,
                                statusDetail = detail
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            stagingFile?.delete()
            throw e
        }
    }

    private fun resolveFormatWithHeader(
        detectedFormat: BookFormat?,
        stagingFile: File?,
        rawFileName: String
    ): BookFormat {
        if (detectedFormat != null) return detectedFormat
        if (stagingFile != null) {
            if (PdfSession.hasPdfMagicHeader(stagingFile)) return BookFormat.PDF
            if (hasZipMagicHeader(stagingFile)) return BookFormat.ZIP
        }
        throw IllegalStateException("不支持的文件格式：$rawFileName（支持 ZIP / CBZ / EPUB / PDF / TXT / MD）")
    }

    private fun persistStagingFile(
        stagingFile: File?,
        libraryDir: File,
        fileHash: String,
        format: BookFormat
    ): String? {
        if (stagingFile == null) return null
        val targetFile = File(libraryDir, "$fileHash.${format.name.lowercase()}")
        if (targetFile.exists() && targetFile.length() > 0L) {
            stagingFile.delete()
        } else {
            if (!stagingFile.renameTo(targetFile)) {
                stagingFile.copyTo(targetFile, overwrite = true)
                stagingFile.delete()
            }
        }
        return targetFile.absolutePath
    }

    private fun formatSizeText(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            "%.1f MB".format(bytes / (1024f * 1024f))
        } else {
            "%.1f KB".format((bytes / 1024f).coerceAtLeast(0.1f))
        }
    }

    private fun copyStreamWithSha256(
        context: Context,
        uri: Uri,
        rawFileName: String,
        targetFile: File
    ): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var totalBytes = 0L

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalBytes += read
                }
            }
        } ?: throw IllegalStateException("无法打开文件: $rawFileName")

        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex to totalBytes
    }

    private fun hasZipMagicHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { input ->
                val b0 = input.read()
                val b1 = input.read()
                b0 == 0x50 && b1 == 0x4B // 'P''K'
            }
        }.getOrDefault(false)
    }

    private fun saveCoverThumbnail(
        context: Context,
        session: ArchiveSession,
        entryPath: String,
        fileHash: String
    ): String? {
        val bitmap = session.decodeImageEntry(entryPath, maxDimension = 540, trimWhiteMargins = false)
            ?: return null
        return saveBitmapThumbnail(context, bitmap, fileHash)
    }

    private fun saveBitmapThumbnail(
        context: Context,
        bitmap: Bitmap,
        fileHash: String
    ): String {
        return try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val outFile = File(coversDir, "$fileHash.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, fos)
            }
            outFile.absolutePath
        } finally {
            bitmap.recycle()
        }
    }
}
