package com.example.scanby.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanby.domain.gallery.GalleryImage
import com.example.scanby.domain.gallery.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState

    fun loadImages() {
        viewModelScope.launch {
            val images = galleryRepository.getImages()
            _uiState.update { it.copy(images = images) }
        }
    }

    fun onImageTapped(id: Long) {
        _uiState.update { state ->
            if (state.isMultiSelectMode) {
                val selected = if (id in state.selectedIds) {
                    state.selectedIds - id
                } else {
                    state.selectedIds + id
                }
                state.copy(selectedIds = selected)
            } else {
                state.copy(selectedIds = setOf(id))
            }
        }
    }

    fun onImageLongPressed(id: Long) {
        _uiState.update { state ->
            state.copy(isMultiSelectMode = true, selectedIds = state.selectedIds + id)
        }
    }
}
