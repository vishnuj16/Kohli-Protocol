package com.vishnu.kohliprotocol.ui.motivation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.local.entity.MotivationPhotoEntity
import com.vishnu.kohliprotocol.data.repository.MotivationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MotivationViewModel(private val repository: MotivationRepository) : ViewModel() {

    val photos: StateFlow<List<MotivationPhotoEntity>?> = repository.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val failed = uris.count { uri -> runCatching { repository.addPhoto(uri) }.isFailure }
            _error.value = if (failed > 0) "$failed photo(s) could not be imported" else null
            _busy.value = false
        }
    }

    fun delete(photo: MotivationPhotoEntity) {
        viewModelScope.launch { runCatching { repository.deletePhoto(photo) } }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                MotivationViewModel(container.motivationRepository)
            }
        }
    }
}
