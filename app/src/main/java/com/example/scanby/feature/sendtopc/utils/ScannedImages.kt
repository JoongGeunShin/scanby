package com.example.scanby.feature.sendtopc.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class ScannedImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
)

fun loadScannedImages(context: Context): List<ScannedImage> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("Pictures/scanby%")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val results = mutableListOf<ScannedImage>()
    context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
        ?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                results += ScannedImage(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameColumn),
                )
            }
        }
    return results
}
