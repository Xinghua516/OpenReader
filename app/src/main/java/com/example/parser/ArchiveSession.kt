package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

class ArchiveSession private constructor(
    private val zipFile: ZipFile,
    private val pfd: ParcelFileDescriptor?
) : Closeable {

    val entryNames: List<String> by lazy {
        val list = mutableListOf<String>()
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory) {
                list.add(entry.name)
            }
        }
        list
    }

    val sortedImageEntries: List<String> by lazy {
        entryNames
            .filter { FilenameMetadataParser.isImageEntry(it) }
            .sortedWith(NaturalOrderComparator)
    }

    fun hasEntry(name: String): Boolean = zipFile.getEntry(name) != null

    fun findEntryIgnoreCase(target: String): String? {
        val exact = zipFile.getEntry(target)
        if (exact != null) return exact.name
        return entryNames.firstOrNull { it.equals(target, ignoreCase = true) }
    }

    fun readEntryBytes(entryName: String): ByteArray? {
        val entry = zipFile.getEntry(entryName) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes() }
    }

    fun decodeImageEntry(
        entryName: String,
        maxDimension: Int = 2048,
        trimWhiteMargins: Boolean = false
    ): Bitmap? {
        val bytes = readEntryBytes(entryName) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        return if (trimWhiteMargins) trimBitmapMargins(bitmap) else bitmap
    }

    override fun close() {
        runCatching { zipFile.close() }
        runCatching { pfd?.close() }
    }

    companion object {
        suspend fun open(
            context: Context,
            sourceUri: String,
            localCopyPath: String?,
            cacheKey: String
        ): ArchiveSession = withContext(Dispatchers.IO) {
            // 1. Prefer existing local copy if present
            if (!localCopyPath.isNullOrBlank()) {
                val localFile = File(localCopyPath)
                if (localFile.exists() && localFile.canRead()) {
                    return@withContext ArchiveSession(openZipWithCharsetFallback(localFile), null)
                }
            }

            val uri = Uri.parse(sourceUri)
            if (uri.scheme == "file" && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists()) {
                    return@withContext ArchiveSession(openZipWithCharsetFallback(file), null)
                }
            }

            // 2. Try opening via ParcelFileDescriptor /proc/self/fd/<fd> for zero-copy SAF access
            val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (pfd != null) {
                val procFile = File("/proc/self/fd/${pfd.fd}")
                val opened = runCatching { openZipWithCharsetFallback(procFile) }.getOrNull()
                if (opened != null) {
                    return@withContext ArchiveSession(opened, pfd)
                }
                pfd.close()
            }

            // 3. Fallback: stream into disk cache for non-seekable document providers
            val cacheDir = File(context.cacheDir, "archive_cache").apply { mkdirs() }
            val cachedFile = File(cacheDir, "$cacheKey.zip")
            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cachedFile).use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                } ?: throw IllegalStateException("无法读取文件流: $sourceUri")
            }
            ArchiveSession(openZipWithCharsetFallback(cachedFile), null)
        }

        private fun openZipWithCharsetFallback(file: File): ZipFile {
            return try {
                ZipFile(file, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                val gbk = runCatching { Charset.forName("GBK") }.getOrDefault(StandardCharsets.ISO_8859_1)
                ZipFile(file, gbk)
            }
        }

        fun trimBitmapMargins(source: Bitmap): Bitmap {
            val w = source.width
            val h = source.height
            if (w < 64 || h < 64) return source

            val rowStep = (h / 40).coerceAtLeast(1)
            val colStep = (w / 40).coerceAtLeast(1)

            fun isNearWhite(pixel: Int): Boolean {
                val a = (pixel ushr 24) and 0xFF
                if (a < 20) return true
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                return r >= 242 && g >= 242 && b >= 242
            }

            var left = 0
            while (left < w / 3) {
                var allWhite = true
                var y = 0
                while (y < h) {
                    if (!isNearWhite(source.getPixel(left, y))) {
                        allWhite = false
                        break
                    }
                    y += rowStep
                }
                if (!allWhite) break
                left += 2
            }

            var right = w - 1
            while (right > (w * 2) / 3) {
                var allWhite = true
                var y = 0
                while (y < h) {
                    if (!isNearWhite(source.getPixel(right, y))) {
                        allWhite = false
                        break
                    }
                    y += rowStep
                }
                if (!allWhite) break
                right -= 2
            }

            var top = 0
            while (top < h / 3) {
                var allWhite = true
                var x = left
                while (x <= right) {
                    if (!isNearWhite(source.getPixel(x, top))) {
                        allWhite = false
                        break
                    }
                    x += colStep
                }
                if (!allWhite) break
                top += 2
            }

            var bottom = h - 1
            while (bottom > (h * 2) / 3) {
                var allWhite = true
                var x = left
                while (x <= right) {
                    if (!isNearWhite(source.getPixel(x, bottom))) {
                        allWhite = false
                        break
                    }
                    x += colStep
                }
                if (!allWhite) break
                bottom -= 2
            }

            val pad = 4
            val cropLeft = (left - pad).coerceAtLeast(0)
            val cropTop = (top - pad).coerceAtLeast(0)
            val cropRight = (right + pad).coerceAtMost(w - 1)
            val cropBottom = (bottom + pad).coerceAtMost(h - 1)
            val newW = cropRight - cropLeft + 1
            val newH = cropBottom - cropTop + 1

            return if (newW in 48 until w || newH in 48 until h) {
                Bitmap.createBitmap(source, cropLeft, cropTop, newW, newH)
            } else {
                source
            }
        }
    }
}
