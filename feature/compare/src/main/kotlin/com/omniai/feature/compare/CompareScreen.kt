package com.omniai.feature.compare

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.designsystem.component.StatusChip
import com.omniai.core.designsystem.component.StatusTone
import com.omniai.core.domain.compare.CompareResult
import com.omniai.core.domain.compare.CompareStatus
import com.omniai.core.ui.Format
import com.omniai.core.ui.MarkdownText
import com.omniai.core.ui.ModelPickerSheet
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object CompareRoute

fun NavGraphBuilder.compareGraph() {
    composable<CompareRoute> { CompareScreen() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(viewModel: CompareViewModel = hiltViewModel()) {
    val providers by viewModel.providerList.collectAsStateWithLifecycle()
    val models by viewModel.modelList.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val system by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var picking by rememberSaveable { mutableStateOf(false) }
    val running = results.any { it.status == CompareStatus.RUNNING || it.status == CompareStatus.WAITING }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(if (Platform.writeText(context, uri, viewModel.exportMarkdown(results))) "Exported." else "Export failed.")
        }
    }

    BackScaffold(
        title = "Compare",
        onBack = null,
        snackbarHostState = snackbar,
        actions = {
            if (results.isNotEmpty() && !running) IconButton(onClick = { exportLauncher.launch("comparison.md") }) { Icon(Icons.Outlined.FileDownload, "Export comparison") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OmniCard(title = "Models (${selected.size}/${settings.compareLimit})") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selected.forEach { key ->
                            InputChip(selected = true, onClick = { viewModel.toggle(key) }, label = { Text(key.modelId) },
                                trailingIcon = { Text("✕") })
                        }
                    }
                    TextButton(onClick = { picking = true }) { Text(if (selected.isEmpty()) "Choose models" else "Add or remove models") }
                    Text(
                        "Each model is billed separately by its provider. Comparisons run in parallel; one failure doesn't stop the others.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prompt, { viewModel.prompt.value = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp))
                    OutlinedTextField(system, { viewModel.systemPrompt.value = it }, label = { Text("System prompt (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            temperature?.toString().orEmpty(),
                            { v -> viewModel.temperature.value = v.toDoubleOrNull()?.coerceIn(0.0, 2.0) },
                            label = { Text("Temperature") }, placeholder = { Text("default") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        if (running) OutlinedButton(onClick = viewModel::stopAll) { Text("Stop all") }
                        else Button(onClick = viewModel::run, enabled = prompt.isNotBlank() && selected.isNotEmpty()) { Text("Run") }
                    }
                    if (results.isNotEmpty() && !running) TextButton(onClick = viewModel::clear) { Text("Clear results") }
                }
            }
            items(results, key = { it.target.providerId + "/" + it.target.modelId }) { r ->
                ResultCard(r, settings.renderMarkdown, onStop = { viewModel.stop(r.target) }, onCopy = { Platform.copy(context, r.text) }, money = { Format.money(it, settings.currency) })
            }
        }
    }

    if (picking) ModelPickerSheet(
        providers = providers, models = models, selected = null,
        onSelect = { viewModel.toggle(it) }, onDismiss = { picking = false }, multiSelect = selected,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(r: CompareResult, markdown: Boolean, onStop: () -> Unit, onCopy: () -> Unit, money: (Double) -> String) {
    OmniCard(
        title = r.target.modelId,
        trailing = {
            when (r.status) {
                CompareStatus.RUNNING, CompareStatus.WAITING -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    IconButton(onClick = onStop) { Icon(Icons.Outlined.Stop, "Stop ${r.target.modelId}") }
                }
                else -> IconButton(onClick = onCopy, enabled = r.text.isNotEmpty()) { Icon(Icons.Outlined.ContentCopy, "Copy answer") }
            }
        },
    ) {
        Text(r.providerName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (r.status) {
                CompareStatus.DONE -> StatusChip("Done", StatusTone.SUCCESS)
                CompareStatus.FAILED -> StatusChip("Failed", StatusTone.DANGER)
                CompareStatus.STOPPED -> StatusChip("Stopped", StatusTone.WARNING)
                CompareStatus.RUNNING -> StatusChip("Running", StatusTone.ACCENT)
                CompareStatus.WAITING -> StatusChip("Waiting", StatusTone.NEUTRAL)
            }
            r.firstTokenMs?.let { MetaPill("1st token ${Format.latency(it)}") }
            r.totalMs?.let { MetaPill("total ${Format.latency(it)}") }
            r.usage?.let { MetaPill("${Format.tokens(it.inputTokens)} in · ${Format.tokens(it.outputTokens)} out" + if (it.providerReported) "" else " (est.)") }
            r.estimatedCostUsd?.let { MetaPill("≈ ${money(it)}") }
        }
        Spacer(Modifier.size(8.dp))
        r.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (r.text.isNotEmpty()) MarkdownText(r.text, renderMarkdown = markdown)
    }
}
