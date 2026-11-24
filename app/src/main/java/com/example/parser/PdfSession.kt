package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

class PdfSession private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    val pageCount: Int
        get() = renderer.pageCount

    private val renderLock = Any()

    fun renderPage(
        pageIndex: Int,
        maxDimension: Int = 2200,
        trimWhiteMargins: Boolean = false
    ): Bitmap? {
        if (pageIndex < 0 || pageIndex >= pageCount) return null
        return synchronized(renderLock) {
            renderer.openPage(pageIndex).use { page ->
                val srcW = page.width.coerceAtLeast(1)
                val srcH = page.height.coerceAtLeast(1)
                val scale = minOf(
                    maxDimension.toFloat() / srcW.toFloat(),
                    maxDimension.toFloat() / srcH.toFloat()
                ).coerceIn(0.5f, 3.0f)
                val targetW = (srcW * scale).toInt().coerceAtLeast(1)
                val targetH = (srcH * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (trimWhiteMargins) {
                    val trimmed = ArchiveSession.trimBitmapMargins(bitmap)
                    if (trimmed !== bitmap) {
                        bitmap.recycle()
                    }
                    trimmed
                } else {
                    bitmap
                }
            }
        }
    }

    override fun close() {
        synchronized(renderLock) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    companion object {
        fun hasPdfMagicHeader(file: File): Boolean {
            if (!file.exists() || file.length() < 5L) return false
            return runCatching {
                file.inputStream().use { input ->
                    val buf = ByteArray(1024)
                    val read = input.read(buf)
                    if (read < 5) return@use false
                    val head = String(buf, 0, read, Charsets.ISO_8859_1)
                    head.contains("%PDF-")
                }
            }.getOrDefault(false)
        }

        suspend fun open(
            context: Context,
            sourceUri: String,
            localCopyPath: String?,
            cacheKey: String
        ): PdfSession = withContext(Dispatchers.IO) {
            // 1. Prefer internal local copy
            if (!localCopyPath.isNullOrBlank()) {
                val localFile = File(localCopyPath)
                if (localFile.exists() && localFile.canRead()) {
                    val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    return@withContext createSession(pfd)
                }
            }

            val uri = Uri.parse(sourceUri)
            if (uri.scheme == "file" && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists()) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    return@withContext createSession(pfd)
                }
            }

            // 2. Direct seekable SAF file descriptor
            val safPfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (safPfd != null) {
                val opened = runCatching { createSession(safPfd) }.getOrNull()
                if (opened != null) {
                    return@withContext opened
                }
                runCatching { safPfd.close() }
            }

            // 3. Fallback: stream to cache directory
            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val cachedFile = File(cacheDir, "$cacheKey.pdf")
            if (!cachedFile.exists() || cachedFile.length() == 0L) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cachedFile).use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                } ?: throw IllegalStateException("无法读取 PDF 文件: $sourceUri")
            }
            val cachedPfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            createSession(cachedPfd)
        }

        private fun createSession(pfd: ParcelFileDescriptor): PdfSession {
            return try {
                val renderer = PdfRenderer(pfd)
                PdfSession(pfd, renderer)
            } catch (e: Exception) {
                runCatching { pfd.close() }
                throw IllegalStateException("PDF 文件已损坏或受密码保护，无法解析", e)
            }
        }
    }
}
