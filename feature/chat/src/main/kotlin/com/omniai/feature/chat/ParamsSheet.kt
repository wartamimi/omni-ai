package com.omniai.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omniai.core.model.GenerationParameter
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.ReasoningEffort
import java.util.Locale

/**
 * Per-conversation generation parameters. Only parameters the provider's protocol accepts are shown;
 * unset values fall back to the provider's own defaults (OmniAI never invents defaults).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParamsSheet(
    params: GenerationParams?,
    systemPrompt: String,
    supported: Set<GenerationParameter>,
    onApply: (GenerationParams?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var p by remember { mutableStateOf(params ?: GenerationParams()) }
    var system by remember { mutableStateOf(systemPrompt) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Parameters", style = MaterialTheme.typography.titleLarge)
            Text(
                "Unchecked values are not sent, so the provider's default applies. Parameters this provider doesn't accept are hidden.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (GenerationParameter.SYSTEM_PROMPT in supported || supported.isEmpty()) {
                OutlinedTextField(system, { system = it }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
            }
            if (GenerationParameter.TEMPERATURE in supported) OptionalSlider("Temperature", p.temperature, 0f..2f, default = 1.0) { p = p.copy(temperature = it) }
            if (GenerationParameter.TOP_P in supported) OptionalSlider("Top P", p.topP, 0f..1f, default = 1.0) { p = p.copy(topP = it) }
            if (GenerationParameter.TOP_K in supported) OptionalInt("Top K", p.topK) { p = p.copy(topK = it) }
            if (GenerationParameter.MAX_OUTPUT_TOKENS in supported) OptionalInt("Max output tokens", p.maxOutputTokens) { p = p.copy(maxOutputTokens = it) }
            if (GenerationParameter.FREQUENCY_PENALTY in supported) OptionalSlider("Frequency penalty", p.frequencyPenalty, -2f..2f, default = 0.0) { p = p.copy(frequencyPenalty = it) }
            if (GenerationParameter.PRESENCE_PENALTY in supported) OptionalSlider("Presence penalty", p.presencePenalty, -2f..2f, default = 0.0) { p = p.copy(presencePenalty = it) }
            if (GenerationParameter.SEED in supported) OptionalInt("Seed", p.seed?.toInt()) { p = p.copy(seed = it?.toLong()) }
            if (GenerationParameter.STOP_SEQUENCES in supported) {
                OutlinedTextField(
                    p.stopSequences.joinToString(", "), { v -> p = p.copy(stopSequences = v.split(',').map(String::trim).filter(String::isNotEmpty).take(4)) },
                    label = { Text("Stop sequences (comma separated, max 4)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
            if (GenerationParameter.REASONING_EFFORT in supported) {
                Text("Reasoning effort (only for reasoning models)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = p.reasoningEffort == null, onClick = { p = p.copy(reasoningEffort = null) }, label = { Text("Default") })
                    ReasoningEffort.entries.forEach { e -> FilterChip(selected = p.reasoningEffort == e, onClick = { p = p.copy(reasoningEffort = e) }, label = { Text(e.wire) }) }
                }
            }
            if (GenerationParameter.JSON_MODE in supported) CheckRow("JSON mode", p.jsonMode) { p = p.copy(jsonMode = it) }
            if (GenerationParameter.STREAMING in supported) CheckRow("Stream responses", p.stream) { p = p.copy(stream = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                TextButton(onClick = { p = GenerationParams() }) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(p.takeIf { it != GenerationParams() }, system) }) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label)
    }
}

@Composable
private fun OptionalSlider(label: String, value: Double?, range: ClosedFloatingPointRange<Float>, default: Double, onChange: (Double?) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(value != null, { on -> onChange(if (on) default else null) })
            Text(label, Modifier.weight(1f))
            Text(value?.let { String.format(Locale.US, "%.2f", it) } ?: "provider default", style = MaterialTheme.typography.bodySmall)
        }
        if (value != null) Slider(
            value = value.toFloat(), onValueChange = { onChange(Math.round(it * 100) / 100.0) }, valueRange = range,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun OptionalInt(label: String, value: Int?, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(9).toIntOrNull()) },
        label = { Text(label) }, placeholder = { Text("provider default") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

