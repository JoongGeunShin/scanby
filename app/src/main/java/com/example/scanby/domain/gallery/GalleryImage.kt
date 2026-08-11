package com.example.scanby.domain.gallery

import android.net.Uri

data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
)
