package com.omniai.feature.usage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.KeyValueRow
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.designsystem.component.SkeletonBlock
import com.omniai.core.model.CurrencySettings
import com.omniai.core.model.ProviderConfig
import com.omniai.core.ui.Format
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object UsageRoute

fun NavGraphBuilder.usageGraph(onBack: () -> Unit) {
    composable<UsageRoute> { UsageScreen(onBack) }
}

@Composable
fun UsageScreen(onBack: () -> Unit, viewModel: UsageViewModel = hiltViewModel()) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providerList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var exportMenu by remember { mutableStateOf(false) }
    var exportCsv by remember { mutableStateOf(true) }
    var confirmReset by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    val currency = settings.currency

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) scope.launch {
            val text = if (exportCsv) viewModel.csv() else viewModel.json()
            snackbar.showSnackbar(if (Platform.writeText(context, uri, text)) "Exported." else "Export failed.")
        }
    }

    BackScaffold(
        title = "Usage & cost",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { adjusting = true }, enabled = providers.isNotEmpty()) { Icon(Icons.Outlined.Add, "Add manual adjustment") }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Outlined.FileDownload, "Export usage") }
                DropdownMenu(exportMenu, { exportMenu = false }) {
                    DropdownMenuItem(text = { Text("Export CSV") }, onClick = { exportMenu = false; exportCsv = true; exportLauncher.launch("omniai-usage.csv") })
                    DropdownMenuItem(text = { Text("Export JSON") }, onClick = { exportMenu = false; exportCsv = false; exportLauncher.launch("omniai-usage.json") })
                }
            }
            IconButton(onClick = { confirmReset = true }) { Icon(Icons.Outlined.DeleteSweep, "Reset usage data") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(UsagePeriod.entries) { p -> FilterChip(p == period, { viewModel.period.value = p }, label = { Text(p.label) }) }
                }
            }
            val s = summary
            if (s == null) {
                item { SkeletonBlock(Modifier.fillMaxWidth(), height = 160.dp) }
                return@LazyColumn
            }
            item {
                OmniCard(title = "Totals") {
                    KeyValueRow("Requests", "${s.requests}" + if (s.failures > 0) " (${s.failures} failed)" else "")
                    KeyValueRow("Input tokens", Format.tokens(s.input.toIntSafe()))
                    KeyValueRow("Output tokens", Format.tokens(s.output.toIntSafe()))
                    KeyValueRow("Estimated cost", Format.money(s.costUsd, currency))
                    if (currency.display != com.omniai.core.model.CurrencyCode.USD) KeyValueRow("Estimated cost (USD)", Format.money(s.costUsd, CurrencySettings()))
                    s.avgLatencyMs?.let { KeyValueRow("Average latency", Format.latency(it)) }
                    Text(
                        buildList {
                            add("Costs are estimates from model pricing (reported by the provider API or entered by you), not your provider's bill.")
                            if (s.unpricedRequests > 0) add("${s.unpricedRequests} request(s) have no known price and are not counted in cost.")
                            if (s.estimatedRequests > 0) add("${s.estimatedRequests} request(s) used locally estimated token counts.")
                            if (s.manualAdjustments > 0) add("Includes ${s.manualAdjustments} manual adjustment(s).")
                            if (currency.display != com.omniai.core.model.CurrencyCode.USD && currency.rateFor(currency.display) == null) add("No ${currency.display} rate set, so amounts are shown in USD. Set a rate in Settings › Currency.")
                        }.joinToString(" "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                OmniCard(title = "Tokens per day") {
                    DailyChart(s.daily.map { it.second }, Modifier.fillMaxWidth().height(140.dp).semantics {
                        contentDescription = "Daily token usage chart, peak ${Format.tokens(s.daily.maxOfOrNull { it.second }?.toIntSafe())} tokens"
                    })
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.daily.firstOrNull()?.first?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall)
                        Text(s.daily.lastOrNull()?.first?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (s.byProvider.isNotEmpty()) item { BreakdownCard("By provider", s.byProvider, currency) }
            if (s.byModel.isNotEmpty()) item { BreakdownCard("By model", s.byModel.take(15), currency) }
        }
    }

    if (confirmReset) ConfirmDialog("Reset usage data?", "All usage records on this device are deleted. Export first if you want to keep them.", "Reset", viewModel::reset, { confirmReset = false })
    if (adjusting) AdjustmentDialog(providers, onDismiss = { adjusting = false }) { p, m, i, o, c -> viewModel.addAdjustment(p, m, i, o, c) }
}

private fun Long.toIntSafe(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun DailyChart(values: List<Long>, modifier: Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slot = size.width / values.size
        val w = (slot * 0.7f).coerceAtLeast(2f)
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - w) / 2
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 3))
            val h = size.height * (v.toFloat() / max)
            if (v > 0) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 3))
        }
    }
}

@Composable
private fun BreakdownCard(title: String, rows: List<Breakdown>, currency: CurrencySettings) {
    val maxTokens = rows.maxOf { it.input + it.output }.coerceAtLeast(1)
    OmniCard(title = title) {
        rows.forEach { r ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (r.unknownCost == r.requests && r.costUsd == 0.0) "price unknown" else Format.money(r.costUsd, currency), style = MaterialTheme.typography.bodyMedium)
                }
                val fraction = (r.input + r.output).toFloat() / maxTokens
                val color = MaterialTheme.colorScheme.primary
                val track = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp)) {
                    drawRoundRect(track, cornerRadius = CornerRadius(6f))
                    drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(6f))
                }
                Text("${r.requests} req · ${Format.tokens(r.input.toIntSafe())} in · ${Format.tokens(r.output.toIntSafe())} out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdjustmentDialog(providers: List<ProviderConfig>, onDismiss: () -> Unit, onAdd: (String, String, Int, Int, Double?) -> Unit) {
    var providerId by remember { mutableStateOf(providers.first().id) }
    var model by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual adjustment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Record usage made outside OmniAI or correct totals. It's labeled as a manual entry.", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(providers, key = { it.id }) { p -> FilterChip(providerId == p.id, { providerId = p.id }, label = { Text(p.name) }) } }
                OutlinedTextField(model, { model = it }, label = { Text("Model (optional)") }, singleLine = true)
                OutlinedTextField(input, { input = it.filter(Char::isDigit).take(10) }, label = { Text("Input tokens") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(output, { output = it.filter(Char::isDigit).take(10) }, label = { Text("Output tokens") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(cost, { cost = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("Cost in USD (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(providerId, model.trim(), input.toIntOrNull() ?: 0, output.toIntOrNull() ?: 0, cost.toDoubleOrNull()); onDismiss() },
                enabled = (input.toIntOrNull() ?: 0) > 0 || (output.toIntOrNull() ?: 0) > 0 || cost.toDoubleOrNull() != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
