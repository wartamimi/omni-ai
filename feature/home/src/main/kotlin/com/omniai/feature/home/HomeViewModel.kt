package com.omniai.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.DashboardCard
import com.omniai.core.model.ModelKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class UsageSnapshot(val todayTokens: Long, val todayCostUsd: Double, val weekTokens: Long, val weekCostUsd: Double, val weekRequests: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    providers: ProviderRepository,
    credentials: CredentialRepository,
    models: ModelRepository,
    conversations: ConversationRepository,
    usage: UsageRepository,
    private val engine: ChatEngine,
) : ViewModel() {
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val keys = credentials.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val modelList = models.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent = conversations.observeConversations(false).map { it.take(5) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val weekStart = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val usageSnapshot = usage.observeSince(weekStart).map { rs ->
        val today = rs.filter { it.timestamp >= todayStart }
        UsageSnapshot(
            today.sumOf { it.inputTokens.toLong() + it.outputTokens }, today.sumOf { it.estimatedCostUsd ?: 0.0 },
            rs.sumOf { it.inputTokens.toLong() + it.outputTokens }, rs.sumOf { it.estimatedCostUsd ?: 0.0 }, rs.count { !it.isManualAdjustment },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun newChat(key: ModelKey? = null): String = engine.createConversation(providerId = key?.providerId, modelId = key?.modelId).id

    fun setDefaultModel(key: ModelKey) = viewModelScope.launch {
        settingsRepo.update { it.copy(defaultProviderId = key.providerId, defaultModelId = key.modelId) }
    }

    fun updateLayout(transform: (com.omniai.core.model.DashboardLayout) -> com.omniai.core.model.DashboardLayout) = viewModelScope.launch {
        settingsRepo.update { it.copy(dashboard = transform(it.dashboard)) }
    }

    fun move(card: DashboardCard, delta: Int) = updateLayout { layout ->
        val order = (layout.order + DashboardCard.entries.filterNot { it in layout.order }).toMutableList()
        val i = order.indexOf(card)
        val j = (i + delta).coerceIn(0, order.lastIndex)
        if (i != j) { order.removeAt(i); order.add(j, card) }
        layout.copy(order = order)
    }

    fun toggleHidden(card: DashboardCard) = updateLayout { l -> l.copy(hidden = if (card in l.hidden) l.hidden - card else l.hidden + card) }

    fun completeOnboarding() = viewModelScope.launch { settingsRepo.update { it.copy(onboardingCompleted = true) } }

}
