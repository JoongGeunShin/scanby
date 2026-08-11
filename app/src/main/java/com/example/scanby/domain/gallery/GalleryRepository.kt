package com.example.scanby.domain.gallery

interface GalleryRepository {
    suspend fun getImages(): List<GalleryImage>
}
