package com.omniai.feature.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.data.ProviderManager
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.model.AIModel
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelCategory
import com.omniai.core.model.ModelKey
import com.omniai.core.model.ModelSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelFilter(
    val query: String = "",
    val providerId: String? = null,
    val favoritesOnly: Boolean = false,
    val capability: ModelCapability? = null,
    val category: ModelCategory? = null,
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val models: ModelRepository,
    providers: ProviderRepository,
    private val manager: ProviderManager,
) : ViewModel() {
    val filter = MutableStateFlow(ModelFilter(providerId = savedState.get<String>("providerId")?.takeIf { it.isNotBlank() }))
    val refreshing = MutableStateFlow(false)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visible = combine(models.observeAll(), filter) { all, f ->
        val q = f.query.trim().lowercase()
        all.asSequence()
            .filter { f.providerId == null || it.providerId == f.providerId }
            .filter { !f.favoritesOnly || it.isFavorite }
            .filter { f.capability == null || it.capabilities.supports(f.capability) }
            .filter { f.category == null || f.category in it.userCategories }
            .filter { q.isEmpty() || it.modelId.lowercase().contains(q) || it.displayName.lowercase().contains(q) || it.description.orEmpty().lowercase().contains(q) }
            .sortedWith(compareByDescending<AIModel> { it.isFavorite }.thenByDescending { it.lastUsedAt ?: 0 }.thenBy { it.displayName.lowercase() })
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFavorite(m: AIModel, fav: Boolean) = viewModelScope.launch { models.setFavorite(m.key, fav) }
    fun save(m: AIModel) = viewModelScope.launch { models.upsert(m) }
    fun delete(m: AIModel) = viewModelScope.launch { models.delete(m.key) }

    fun addManual(providerId: String, modelId: String, displayName: String) = viewModelScope.launch {
        val id = modelId.trim()
        if (id.isEmpty()) return@launch
        if (models.get(ModelKey(providerId, id)) != null) { _events.emit("That model is already in the list."); return@launch }
        models.upsert(AIModel(providerId, id, displayName.trim().ifBlank { id }, source = ModelSource.MANUAL))
        _events.emit("Added $id.")
    }

    fun refresh() {
        val ids = filter.value.providerId?.let(::listOf) ?: providerList.value.filter { it.enabled && it.modelsPath != null }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            refreshing.value = true
            val failures = mutableListOf<String>()
            var total = 0
            ids.forEach { id ->
                manager.refreshModels(id).onSuccess { total += it }.onFailure { failures += (providerList.value.firstOrNull { p -> p.id == id }?.name ?: id) }
            }
            refreshing.value = false
            _events.emit(if (failures.isEmpty()) "Refreshed: $total models." else "Refreshed $total models. Failed: ${failures.joinToString()}")
        }
    }
}
