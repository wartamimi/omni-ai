package com.omniai.feature.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.repository.PromptRepository
import com.omniai.core.model.PromptCategory
import com.omniai.core.model.PromptTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptsViewModel @Inject constructor(
    private val prompts: PromptRepository,
    private val engine: ChatEngine,
    private val exporter: DataExporter,
) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow<PromptCategory?>(null)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val visible = combine(prompts.observeAll(), query, category) { all, q, c ->
        all.filter { c == null || it.category == c }
            .filter { q.isBlank() || it.title.contains(q, true) || it.content.contains(q, true) }
            .sortedWith(compareByDescending<PromptTemplate> { it.isFavorite }.thenBy { it.title.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: String?, title: String, content: String, category: PromptCategory, favorite: Boolean) = viewModelScope.launch {
        val now = Clock.System.now()
        val existing = id?.let { prompts.get(it) }
        prompts.upsert(
            existing?.copy(title = title.trim(), content = content, category = category, isFavorite = favorite, updatedAt = now)
                ?: PromptTemplate(Ids.new(), title.trim(), content, category, favorite, now, now),
        )
    }

    fun toggleFavorite(p: PromptTemplate) = viewModelScope.launch { prompts.upsert(p.copy(isFavorite = !p.isFavorite, updatedAt = Clock.System.now())) }
    fun delete(p: PromptTemplate) = viewModelScope.launch { prompts.delete(p.id) }

    suspend fun startChat(): String = engine.createConversation().id

    suspend fun exportJson(): String = exporter.promptsToJson(prompts.observeAll().first())

    fun importJson(raw: String) = viewModelScope.launch {
        runCatching { exporter.promptsFromJson(raw) }
            .onSuccess { list ->
                val now = Clock.System.now()
                list.forEach { prompts.upsert(it.copy(id = Ids.new(), createdAt = now, updatedAt = now)) }
                _events.emit("Imported ${list.size} prompts.")
            }
            .onFailure { _events.emit("Import failed: not a valid prompt list.") }
    }
}
