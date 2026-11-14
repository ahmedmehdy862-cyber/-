package com.focusstudy.poc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the task-type manager. Types are optional labels: a task keeps working with
 * the same engine whether its type is built in, custom or missing entirely.
 */
class TaskTypesViewModel(application: Application) : AndroidViewModel(application) {
    data class UiState(
        val types: List<TaskType> = emptyList(),
        val loading: Boolean = true
    )

    private val repository = TaskRepository.get(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState(types = repository.taskTypes(includeArchived = true), loading = false)
        }
    }

    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.addTaskType(clean) }
            refresh()
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.renameTaskType(id, clean) }
            refresh()
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setTaskTypeArchived(id, archived)
            refresh()
        }
    }

    fun move(id: String, up: Boolean) {
        viewModelScope.launch {
            repository.moveTaskType(id, up)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTaskType(id) }
            refresh()
        }
    }
}
