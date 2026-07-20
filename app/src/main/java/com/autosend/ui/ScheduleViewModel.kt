package com.autosend.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autosend.data.MessageRepository
import com.autosend.data.MessageWithAttachments
import com.autosend.data.ScheduledMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val messages: StateFlow<List<MessageWithAttachments>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(message: ScheduledMessage, newAttachments: List<Uri>, onSaved: () -> Unit) {
        viewModelScope.launch {
            repo.save(message, newAttachments)
            onSaved()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun load(id: Long, onLoaded: (MessageWithAttachments?) -> Unit) {
        viewModelScope.launch { onLoaded(repo.getWithAttachments(id)) }
    }
}
