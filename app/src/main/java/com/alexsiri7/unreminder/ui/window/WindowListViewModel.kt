package com.alexsiri7.unreminder.ui.window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsiri7.unreminder.data.db.WindowEntity
import com.alexsiri7.unreminder.data.repository.WindowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WindowListViewModel @Inject constructor(
    private val windowRepository: WindowRepository
) : ViewModel() {

    val windows: StateFlow<List<WindowEntity>> = windowRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleActive(window: WindowEntity) {
        viewModelScope.launch {
            windowRepository.update(window.copy(active = !window.active))
        }
    }

    fun delete(window: WindowEntity) {
        viewModelScope.launch {
            windowRepository.delete(window)
        }
    }
}
