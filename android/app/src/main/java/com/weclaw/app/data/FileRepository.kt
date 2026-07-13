package com.weclaw.app.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

class FileRepository(private val context: Context) {

    /** 按日期范围查询相册照片 */
    fun queryPhotos(dateFrom: String, dateTo: String, limit: Int = 20): List<MediaItem> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
        )
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND " +
                "${MediaStore.Images.Media.DATE_TAKEN} <= ?"
        val args = arrayOf(
            dateToTimestamp(dateFrom).toString(),
            dateToTimestamp(dateTo).toString(),
        )

        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(uri, projection, selection, args, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
            while (cursor.moveToNext() && items.size < limit) {
                items.add(MediaItem(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    date = cursor.getLong(2),
                    size = cursor.getLong(3),
                    uri = Uri.withAppendedPath(uri, cursor.getLong(0).toString()),
                ))
            }
        }
        return items
    }

    /** 按关键词查询文档 */
    fun queryFiles(keyword: String = "", mimeType: String? = null, limit: Int = 20): List<MediaItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (keyword.isNotBlank()) {
            conditions.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
            args.add("%$keyword%")
        }
        if (mimeType != null) {
            conditions.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
            args.add(mimeType)
        }

        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(
            uri, projection,
            conditions.joinToString(" AND "), args.toTypedArray(),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext() && items.size < limit) {
                items.add(MediaItem(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    date = cursor.getLong(2) * 1000L,
                    size = cursor.getLong(3),
                    mimeType = cursor.getString(4),
                ))
            }
        }
        return items
    }

    /** 保存分享来的文件到缓存 */
    fun saveSharedFile(uri: Uri): File? {
        return try {
            val fileName = getFileName(uri) ?: "shared_${System.currentTimeMillis()}"
            val cacheDir = File(context.cacheDir, "shared")
            cacheDir.mkdirs()
            val outFile = File(cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile
        } catch (e: Exception) { null }
    }

    private fun getFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    private fun dateToTimestamp(date: String): Long {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).parse(date)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }
}

data class MediaItem(
    val id: Long,
    val name: String,
    val date: Long,
    val size: Long,
    val uri: Uri? = null,
    val mimeType: String? = null,
)
