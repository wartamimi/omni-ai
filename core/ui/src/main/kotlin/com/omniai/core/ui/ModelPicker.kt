package com.omniai.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.component.ProviderAvatar
import com.omniai.core.model.AIModel
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelKey
import com.omniai.core.model.ProviderConfig

/**
 * Bottom sheet to choose provider + model. Users can also type a model ID that isn't in the list
 * (manual entry), since many providers have no model list endpoint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfig>,
    models: List<AIModel>,
    selected: ModelKey?,
    onSelect: (ModelKey) -> Unit,
    onDismiss: () -> Unit,
    multiSelect: Set<ModelKey>? = null,
) {
    val enabled = providers.filter { it.enabled }
    var providerId by rememberSaveable { mutableStateOf(selected?.providerId ?: enabled.firstOrNull()?.id) }
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(if (multiSelect != null) "Select models" else "Choose model", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (enabled.isEmpty()) {
                Text("No enabled providers. Add one in Providers first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRowChips(enabled, providerId) { providerId = it }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search or type a model ID") },
                modifier = Modifier.fillMaxWidth(),
            )
            val list = remember(models, providerId, query) {
                models.filter { it.providerId == providerId }
                    .filter { query.isBlank() || it.modelId.contains(query, true) || it.displayName.contains(query, true) }
                    .sortedWith(compareByDescending<AIModel> { it.isFavorite }.thenByDescending { it.lastUsedAt ?: 0 }.thenBy { it.displayName.lowercase() })
            }
            val exact = list.any { it.modelId == query.trim() }
            if (query.isNotBlank() && !exact && providerId != null) {
                OutlinedButton(onClick = { onSelect(ModelKey(providerId!!, query.trim())) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Use \"${query.trim()}\" as model ID")
                }
            }
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                if (list.isEmpty()) {
                    item {
                        Text(
                            "No models saved for this provider. Refresh models in Providers, or type a model ID above.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(list, key = { it.providerId + "/" + it.modelId }) { m ->
                    val isSelected = if (multiSelect != null) m.key in multiSelect else m.key == selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(m.key) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (m.isFavorite) Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                                Text(m.displayName, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (m.displayName != m.modelId) Text(m.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                Format.context(m.contextWindow)?.let { MetaPill(it) }
                                if (m.capabilities.supports(ModelCapability.VISION)) MetaPill("Vision")
                                if (m.capabilities.supports(ModelCapability.TOOL_CALLING)) MetaPill("Tools")
                            }
                        }
                        if (isSelected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LazyRowChips(providers: List<ProviderConfig>, selected: String?, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(providers, key = { it.id }) { p ->
            FilterChip(
                selected = p.id == selected,
                onClick = { onSelect(p.id) },
                label = { Text(p.name) },
                leadingIcon = { ProviderAvatar(p.name, p.iconEmoji, size = 20.dp) },
            )
        }
    }
    Spacer(Modifier.width(0.dp))
}
