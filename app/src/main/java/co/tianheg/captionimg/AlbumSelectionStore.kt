package co.tianheg.captionimg

import android.content.Context

object AlbumSelectionStore {
    private const val PREFS_NAME = "album_sources"
    private const val KEY_BUCKET_IDS = "bucket_ids"

    fun loadSelectedBucketIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BUCKET_IDS, emptySet()) ?: emptySet()
    }

    fun saveSelectedBucketIds(context: Context, bucketIds: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_BUCKET_IDS, bucketIds).apply()
    }
}
