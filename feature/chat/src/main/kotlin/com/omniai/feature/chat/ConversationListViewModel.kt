package com.omniai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.ConversationSearchHit
import com.omniai.core.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversations: ConversationRepository,
    private val engine: ChatEngine,
    private val exporter: DataExporter,
) : ViewModel() {
    val query = MutableStateFlow("")
    val showArchived = MutableStateFlow(false)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val conversationList = showArchived.flatMapLatest { conversations.observeConversations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val searchResults = query.debounce(250).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList<ConversationSearchHit>()) else conversations.search(q.trim())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun newConversation(personaId: String? = null): String = engine.createConversation(personaId = personaId).id

    fun togglePin(c: Conversation) = viewModelScope.launch { conversations.upsert(c.copy(isPinned = !c.isPinned)) }
    fun toggleArchive(c: Conversation) = viewModelScope.launch { conversations.upsert(c.copy(isArchived = !c.isArchived)) }
    fun rename(c: Conversation, title: String) = viewModelScope.launch { if (title.isNotBlank()) conversations.upsert(c.copy(title = title.trim())) }
    fun delete(c: Conversation) = viewModelScope.launch { engine.stop(c.id); conversations.delete(c.id); _events.emit("Conversation deleted.") }
    fun duplicate(c: Conversation) = viewModelScope.launch { conversations.duplicate(c.id, "${c.title} (copy)")?.let { _events.emit("Duplicated.") } }

    suspend fun exportJson(id: String) = exporter.exportConversationJson(id)
    suspend fun exportMarkdown(id: String) = exporter.exportConversationMarkdown(id)

    fun importJson(raw: String) = viewModelScope.launch {
        runCatching { exporter.importConversationJson(raw) }
            .onSuccess { _events.emit("Imported \"${it.title}\".") }
            .onFailure { _events.emit("Import failed: not a valid OmniAI conversation file.") }
    }
}
