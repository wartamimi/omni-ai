package com.omniai.feature.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.EmptyState
import com.omniai.core.designsystem.component.KeyValueRow
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.theme.MonoStyle
import com.omniai.core.model.AIModel
import com.omniai.core.model.CapabilityState
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelCategory
import com.omniai.core.model.ModelPricing
import com.omniai.core.model.ModelSource
import com.omniai.core.model.PricingSource
import com.omniai.core.model.ProviderConfig
import com.omniai.core.ui.Format
import kotlinx.serialization.Serializable

@Serializable data class ModelsRoute(val providerId: String = "")

fun NavGraphBuilder.modelsGraph(onBack: () -> Unit) {
    composable<ModelsRoute> { ModelsScreen(onBack) }
}

@Composable
fun ModelsScreen(onBack: () -> Unit, viewModel: ModelsViewModel = hiltViewModel()) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val models by viewModel.visible.collectAsStateWithLifecycle()
    val providers by viewModel.providerList.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<AIModel?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    BackScaffold(
        title = "Models",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            else IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, "Refresh model lists") }
        },
        floatingActionButton = { if (providers.isNotEmpty()) FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, "Add model ID manually") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    filter.query, { q -> viewModel.filter.value = filter.copy(query = q) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search models") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(filter.favoritesOnly, { viewModel.filter.value = filter.copy(favoritesOnly = !filter.favoritesOnly) }, label = { Text("★ Favorites") }) }
                    items(providers, key = { it.id }) { p ->
                        FilterChip(filter.providerId == p.id, { viewModel.filter.value = filter.copy(providerId = if (filter.providerId == p.id) null else p.id) }, label = { Text(p.name) })
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(ModelCapability.VISION, ModelCapability.TOOL_CALLING, ModelCapability.REASONING, ModelCapability.STRUCTURED_OUTPUT)) { c ->
                        FilterChip(filter.capability == c, { viewModel.filter.value = filter.copy(capability = if (filter.capability == c) null else c) }, label = { Text(c.label) })
                    }
                    items(ModelCategory.entries) { c ->
                        FilterChip(filter.category == c, { viewModel.filter.value = filter.copy(category = if (filter.category == c) null else c) }, label = { Text(c.label) })
                    }
                }
            }
            val list = models ?: return@LazyColumn
            if (list.isEmpty()) item {
                EmptyState(
                    Icons.Outlined.Memory, "No models",
                    if (providers.isEmpty()) "Add a provider first." else "Refresh model lists, change filters, or add a model ID by hand with +.",
                )
            }
            items(list, key = { it.providerId + "/" + it.modelId }) { m ->
                val provider = providers.firstOrNull { it.id == m.providerId }
                ModelRow(m, provider, onClick = { editing = m }, onFavorite = { viewModel.setFavorite(m, !m.isFavorite) })
            }
        }
    }
    editing?.let { m -> ModelDetailSheet(m, onDismiss = { editing = null }, onSave = { viewModel.save(it); editing = null }, onDelete = { viewModel.delete(m); editing = null }) }
    if (adding) AddModelDialog(providers, filter.providerId, onDismiss = { adding = false }) { p, id, name -> viewModel.addManual(p, id, name) }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(m: AIModel, provider: ProviderConfig?, onClick: () -> Unit, onFavorite: () -> Unit) {
    ListItem(
        headlineContent = { Text(m.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        overlineContent = { Text(provider?.name ?: m.providerId) },
        supportingContent = {
            Column {
                if (m.displayName != m.modelId) Text(m.modelId, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Format.context(m.contextWindow)?.let { MetaPill(it) }
                    if (m.pricing.isKnown) MetaPill("${Format.pricePerMillion(m.pricing.inputPerMillion)} / ${Format.pricePerMillion(m.pricing.outputPerMillion)} per 1M")
                    m.capabilities.states.filterValues { it == CapabilityState.SUPPORTED }.keys.take(3).forEach { MetaPill(it.label) }
                    if (m.source == ModelSource.MANUAL) MetaPill("Manual")
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onFavorite) {
                Icon(if (m.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline, if (m.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (m.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelDetailSheet(m: AIModel, onDismiss: () -> Unit, onSave: (AIModel) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(m.displayName) }
    var inPrice by remember { mutableStateOf(m.pricing.inputPerMillion?.toString().orEmpty()) }
    var outPrice by remember { mutableStateOf(m.pricing.outputPerMillion?.toString().orEmpty()) }
    var ctx by remember { mutableStateOf(m.contextWindow?.toString().orEmpty()) }
    var categories by remember { mutableStateOf(m.userCategories) }
    var caps by remember { mutableStateOf(m.capabilities) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(m.modelId, style = MaterialTheme.typography.titleMedium.merge(MonoStyle))
            m.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            KeyValueRow("Source", if (m.source == ModelSource.MANUAL) "Added by you" else "From provider's model list")
            KeyValueRow("Pricing source", m.pricing.source.label)
            m.maxOutputTokens?.let { KeyValueRow("Max output", Format.tokens(it)) }
            m.lastUsedAt?.let { KeyValueRow("Last used", Format.dateTime(it)) }
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ctx, { ctx = it.filter(Char::isDigit).take(9) }, label = { Text("Context window (tokens)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Pricing in USD per 1M tokens. Check the provider's pricing page; leave empty if unknown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(inPrice, { inPrice = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("Input") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(outPrice, { outPrice = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("Output") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Text("Capabilities", style = MaterialTheme.typography.titleSmall)
            Text("Tap to cycle: unknown → supported → not supported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModelCapability.entries.forEach { c ->
                    val st = caps.state(c)
                    FilterChip(
                        selected = st == CapabilityState.SUPPORTED,
                        onClick = { caps = caps.with(c, when (st) { CapabilityState.UNKNOWN -> CapabilityState.SUPPORTED; CapabilityState.SUPPORTED -> CapabilityState.UNSUPPORTED; CapabilityState.UNSUPPORTED -> CapabilityState.UNKNOWN }) },
                        label = { Text(c.label + when (st) { CapabilityState.UNSUPPORTED -> " ✕"; CapabilityState.UNKNOWN -> " ?"; else -> "" }) },
                    )
                }
            }
            Text("Categories (used by the model router)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModelCategory.entries.forEach { c ->
                    FilterChip(c in categories, { categories = if (c in categories) categories - c else categories + c }, label = { Text(c.label) })
                }
            }
            Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val ip = inPrice.toDoubleOrNull(); val op = outPrice.toDoubleOrNull()
                    val pricing = when {
                        ip == m.pricing.inputPerMillion && op == m.pricing.outputPerMillion -> m.pricing
                        ip == null && op == null -> ModelPricing.Unknown
                        else -> ModelPricing(ip, op, PricingSource.USER)
                    }
                    onSave(m.copy(displayName = name.ifBlank { m.modelId }, contextWindow = ctx.toIntOrNull(), pricing = pricing, userCategories = categories, capabilities = caps))
                }) { Text("Save") }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Remove ${m.modelId}?",
        if (m.source == ModelSource.MANUAL) "This manual entry is removed." else "It will come back the next time the model list is refreshed.",
        "Remove", onDelete, { confirmDelete = false },
    )
}

@Composable
private fun AddModelDialog(providers: List<ProviderConfig>, preselected: String?, onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var providerId by remember { mutableStateOf(preselected ?: providers.first().id) }
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add model ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(providers, key = { it.id }) { p -> FilterChip(providerId == p.id, { providerId = p.id }, label = { Text(p.name) }) }
                }
                OutlinedTextField(id, { id = it.trim() }, label = { Text("Model ID (exactly as the API expects)") }, singleLine = true, textStyle = MonoStyle)
                OutlinedTextField(name, { name = it }, label = { Text("Display name (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(providerId, id, name); onDismiss() }, enabled = id.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
