package co.tianheg.captionimg

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class AlbumItem(
    val bucketId: String,
    val name: String,
    val coverUri: Uri?,
    val count: Int
)

class MediaStoreRepository(private val context: Context) {

    private val jpegMimeTypes = arrayOf("image/jpeg")

    fun getAlbums(): List<AlbumItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"

        val albums = LinkedHashMap<String, MutableAlbumAccumulator>()

        val selection = MediaStore.Images.Media.MIME_TYPE + " IN (?)"
        val selectionArgs = jpegMimeTypes

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val bucketId = cursor.getLong(bucketIdCol).toString()
                val bucketName = cursor.getString(bucketNameCol) ?: "(Unknown)"
                val imageUri = ContentUris.withAppendedId(collection, id)

                val acc = albums.getOrPut(bucketId) {
                    MutableAlbumAccumulator(name = bucketName, coverUri = imageUri, count = 0)
                }

                // Prefer the first image (newest) as cover.
                if (acc.coverUri == null) acc.coverUri = imageUri
                acc.count += 1
            }
        }

        return albums.entries
            .map { (bucketId, acc) ->
                AlbumItem(
                    bucketId = bucketId,
                    name = acc.name,
                    coverUri = acc.coverUri,
                    count = acc.count
                )
            }
            .sortedByDescending { it.count }
    }

    fun getImagesForBucket(bucketId: String): List<ImageItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = MediaStore.Images.Media.BUCKET_ID + " = ? AND " +
            MediaStore.Images.Media.MIME_TYPE + " IN (?)"
        val selectionArgs = arrayOf(bucketId) + jpegMimeTypes
        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"

        val result = ArrayList<ImageItem>(256)

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val uri = ContentUris.withAppendedId(collection, id)
                result.add(ImageItem(uri = uri, fileName = name, description = ""))
            }
        }

        return result
    }

    private data class MutableAlbumAccumulator(
        val name: String,
        var coverUri: Uri?,
        var count: Int
    )
}
