package com.example.captionimg

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

class FileManager(private val context: Context) {

    fun getFileName(uri: Uri): String {
        return when (uri.scheme) {
            "content" -> {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    it.moveToFirst()
                    it.getString(nameIndex) ?: "Unknown"
                } ?: "Unknown"
            }
            "file" -> {
                File(uri.path ?: "").name
            }
            else -> "Unknown"
        }
    }

    fun isImageFile(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false
        return mimeType.startsWith("image/")
    }

    fun getSupportedImageFormats(): List<String> {
        return listOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
            "image/heic",
            "image/heif"
        )
    }
}
