package co.tianheg.captionimg

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ExifHandler(private val context: Context) {

    fun readImageDescription(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val exif = ExifInterface(inputStream)
            inputStream.close()
            exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readAllExifData(uri: Uri): Map<String, String> {
        val exifData = mutableMapOf<String, String>()
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return exifData
            val exif = ExifInterface(inputStream)
            inputStream.close()

            // Read common EXIF tags
            listOf(
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_IMAGE_WIDTH,
                ExifInterface.TAG_IMAGE_LENGTH,
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_ARTIST
            ).forEach { tag ->
                exif.getAttribute(tag)?.let {
                    exifData[tag] = it
                }
            }

            exifData
        } catch (e: Exception) {
            e.printStackTrace()
            exifData
        }
    }

    fun updateImageDescription(uri: Uri, description: String): Boolean {
        return try {
            // For content URIs, we need to copy to a temp file, modify, and copy back
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("ExifHandler", "Failed to open input stream for URI: $uri")
                return false
            }
            
            val tempFile = File.createTempFile("temp_img", ".jpg", context.cacheDir)

            // Copy original to temp file
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Modify EXIF data in temp file
            val exif = ExifInterface(tempFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
            exif.saveAttributes()

            // Copy modified temp file back to original
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            if (outputStream == null) {
                android.util.Log.e("ExifHandler", "Failed to open output stream for URI: $uri. Check write permissions.")
                tempFile.delete()
                return false
            }
            
            tempFile.inputStream().use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            tempFile.delete()
            android.util.Log.d("ExifHandler", "Successfully updated description for URI: $uri")
            true
        } catch (e: Exception) {
            android.util.Log.e("ExifHandler", "Error updating image description", e)
            e.printStackTrace()
            false
        }
    }

    fun updateMultipleExifTags(uri: Uri, tags: Map<String, String>): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val tempFile = File.createTempFile("temp_img", null, context.cacheDir)

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val exif = ExifInterface(tempFile.absolutePath)
            tags.forEach { (tag, value) ->
                exif.setAttribute(tag, value)
            }
            exif.saveAttributes()

            tempFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }

            tempFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
