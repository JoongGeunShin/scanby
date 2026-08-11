package com.example.scanby.data.gallery

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.scanby.domain.gallery.GalleryImage
import com.example.scanby.domain.gallery.GalleryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GalleryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : GalleryRepository {

    override suspend fun getImages(): List<GalleryImage> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val results = mutableListOf<GalleryImage>()
        context.contentResolver.query(collection, projection, null, null, sortOrder)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    results += GalleryImage(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameColumn),
                        dateAdded = cursor.getLong(dateColumn),
                    )
                }
            }
        results
    }
}
