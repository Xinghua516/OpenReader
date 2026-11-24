package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageStatus(
    val availableBytes: Long,
    val totalBytes: Long,
    val persistedSafGrantsCount: Int,
    val internalLibraryBytes: Long = 0L
) {
    val availableGbFormatted: String
        get() {
            val gb = availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return "%.1f GB".format(gb)
        }

    val internalLibraryFormatted: String
        get() {
            val mb = internalLibraryBytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
        }
}

object StorageStatsProvider {
    fun readStorageStatus(context: Context): StorageStatus {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val avail = statFs.availableBytes
        val total = statFs.totalBytes
        val grants = runCatching { context.contentResolver.persistedUriPermissions.size }.getOrDefault(0)
        val librarySize = dirSize(File(context.filesDir, "library")) +
            dirSize(File(context.filesDir, "covers"))
        return StorageStatus(
            availableBytes = avail,
            totalBytes = total,
            persistedSafGrantsCount = grants,
            internalLibraryBytes = librarySize
        )
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
