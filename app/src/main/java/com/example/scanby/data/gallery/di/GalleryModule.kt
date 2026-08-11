package com.example.scanby.data.gallery.di

import com.example.scanby.data.gallery.GalleryRepositoryImpl
import com.example.scanby.domain.gallery.GalleryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryModule {
    @Binds
    @Singleton
    abstract fun bindGalleryRepository(impl: GalleryRepositoryImpl): GalleryRepository
}
