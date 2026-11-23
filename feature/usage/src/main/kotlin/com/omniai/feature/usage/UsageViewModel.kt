package com.omniai.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.AppSettings
import com.omniai.core.model.UsageRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class UsagePeriod(val label: String, val days: Long?) { TODAY("Today", 0), WEEK("7 days", 6), MONTH("30 days", 29), ALL("All time", null) }

data class Breakdown(val label: String, val requests: Int, val input: Long, val output: Long, val costUsd: Double, val unknownCost: Int)

data class UsageSummary(
    val requests: Int,
    val failures: Int,
    val input: Long,
    val output: Long,
    val costUsd: Double,
    /** Records without known pricing: their cost isn't included in [costUsd]. */
    val unpricedRequests: Int,
    /** Records whose token counts were estimated locally. */
    val estimatedRequests: Int,
    val manualAdjustments: Int,
    val avgLatencyMs: Long?,
    val byProvider: List<Breakdown>,
    val byModel: List<Breakdown>,
    val daily: List<Pair<LocalDate, Long>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageViewModel @Inject constructor(
    private val usage: UsageRepository,
    private val exporter: DataExporter,
    providers: ProviderRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val period = MutableStateFlow(UsagePeriod.WEEK)
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary = period.flatMapLatest { p -> usage.observeSince(since(p)).map { summarize(it, p) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun since(p: UsagePeriod): Long {
        val days = p.days ?: return 0L
        return LocalDate.now().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun summarize(records: List<UsageRecord>, p: UsagePeriod): UsageSummary {
        fun group(key: (UsageRecord) -> String) = records.groupBy(key).map { (k, rs) ->
            Breakdown(k, rs.count { !it.isManualAdjustment }, rs.sumOf { it.inputTokens.toLong() }, rs.sumOf { it.outputTokens.toLong() }, rs.sumOf { it.estimatedCostUsd ?: 0.0 }, rs.count { it.estimatedCostUsd == null })
        }.sortedByDescending { it.input + it.output }
        val zone = ZoneId.systemDefault()
        val byDay = records.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .mapValues { (_, rs) -> rs.sumOf { it.inputTokens.toLong() + it.outputTokens.toLong() } }
        val today = LocalDate.now()
        val span = (p.days ?: byDay.keys.minOrNull()?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) } ?: 0L).coerceIn(0, 59)
        val daily = (span downTo 0).map { d -> today.minusDays(d).let { it to (byDay[it] ?: 0L) } }
        val real = records.filterNot { it.isManualAdjustment }
        return UsageSummary(
            requests = real.size,
            failures = real.count { !it.success },
            input = records.sumOf { it.inputTokens.toLong() },
            output = records.sumOf { it.outputTokens.toLong() },
            costUsd = records.sumOf { it.estimatedCostUsd ?: 0.0 },
            unpricedRequests = real.count { it.estimatedCostUsd == null },
            estimatedRequests = real.count { !it.providerReported },
            manualAdjustments = records.count { it.isManualAdjustment },
            avgLatencyMs = real.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            byProvider = group { it.providerName },
            byModel = group { it.modelId },
            daily = daily,
        )
    }

    fun addAdjustment(providerId: String, modelId: String, input: Int, output: Int, costUsd: Double?) = viewModelScope.launch {
        val provider = providerList.value.firstOrNull { it.id == providerId }
        usage.record(
            UsageRecord(
                id = Ids.new(), providerId = providerId, providerName = provider?.name ?: providerId, modelId = modelId.ifBlank { "manual" },
                conversationId = null, inputTokens = input, outputTokens = output, providerReported = false, estimatedCostUsd = costUsd,
                success = true, httpStatus = null, errorKind = null, latencyMs = null, timestamp = Clock.System.now(), isManualAdjustment = true,
            ),
        )
    }

    fun reset() = viewModelScope.launch { usage.deleteAll() }
    suspend fun csv() = exporter.usageCsv()
    suspend fun json() = exporter.usageJson()
}
