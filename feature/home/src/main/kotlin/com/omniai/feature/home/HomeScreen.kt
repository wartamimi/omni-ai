package com.omniai.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.designsystem.component.ProviderAvatar
import com.omniai.core.designsystem.component.SkeletonBlock
import com.omniai.core.designsystem.component.StatusChip
import com.omniai.core.designsystem.component.StatusTone
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.DashboardCard
import com.omniai.core.model.ModelKey
import com.omniai.core.ui.Format
import com.omniai.core.ui.ModelPickerSheet
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

/** Navigation callbacks from the dashboard to other destinations. */
data class HomeActions(
    val openConversation: (conversationId: String, initialText: String) -> Unit,
    val openConversations: () -> Unit,
    val openProviders: () -> Unit,
    val openCompare: () -> Unit,
    val openModels: () -> Unit,
    val openPrompts: () -> Unit,
    val openPersonas: () -> Unit,
    val openUsage: () -> Unit,
)

fun NavGraphBuilder.homeGraph(actions: HomeActions) {
    composable<HomeRoute> { HomeScreen(actions) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providerList.collectAsStateWithLifecycle()
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val models by viewModel.modelList.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val usage by viewModel.usageSnapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var quick by rememberSaveable { mutableStateOf("") }
    var pickingDefault by rememberSaveable { mutableStateOf(false) }
    var customizing by rememberSaveable { mutableStateOf(false) }

    BackScaffold(
        title = "OmniAI",
        onBack = null,
        actions = { IconButton(onClick = { customizing = true }) { Icon(Icons.Outlined.Dashboard, "Customize dashboard") } },
    ) { padding ->
        val s = settings
        if (s == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { SkeletonBlock(Modifier.fillMaxWidth(), height = 120.dp) }
            return@BackScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (providers.isEmpty()) item {
                OmniCard(title = "Welcome to OmniAI") {
                    Text(
                        "Bring your own API keys and use many AI providers from one app. Keys are encrypted with the Android Keystore and sent only to the provider you configure. There's no OmniAI server in between.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Note: any key stored on a phone can be extracted by someone with full control of the device. Use keys with spending limits where your provider offers them.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = actions.openProviders, modifier = Modifier.padding(top = 12.dp)) { Text("Add your first provider") }
                }
            }
            items(s.dashboard.visibleCards(), key = { it.name }) { card ->
                when (card) {
                    DashboardCard.QUICK_CHAT -> OmniCard(title = "Quick chat") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(quick, { quick = it }, placeholder = { Text("Ask anything") }, modifier = Modifier.weight(1f), maxLines = 4)
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(onClick = {
                                val text = quick
                                scope.launch { actions.openConversation(viewModel.newChat(), text); quick = "" }
                            }, enabled = providers.any { it.enabled }) { Icon(Icons.AutoMirrored.Filled.Send, "Start chat") }
                        }
                    }
                    DashboardCard.DEFAULTS -> OmniCard(title = "Default model", onClick = { pickingDefault = true }) {
                        val p = providers.firstOrNull { it.id == s.defaultProviderId }
                        Text(if (p != null && s.defaultModelId != null) "${p.name} · ${s.defaultModelId}" else "Not set. Tap to choose.", style = MaterialTheme.typography.bodyLarge)
                    }
                    DashboardCard.PROVIDERS -> OmniCard(title = "Providers", onClick = actions.openProviders) {
                        if (providers.isEmpty()) Text("None yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        providers.take(6).forEach { p ->
                            val key = keys.firstOrNull { it.providerId == p.id && it.isActive }
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderAvatar(p.name, p.iconEmoji, size = 28.dp)
                                Text(p.name, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                when {
                                    !p.enabled -> StatusChip("Off", StatusTone.NEUTRAL)
                                    p.auth.type == com.omniai.core.model.AuthType.NONE -> StatusChip("Local", StatusTone.NEUTRAL)
                                    key == null -> StatusChip("No key", StatusTone.WARNING)
                                    key.status == CredentialStatus.VALID -> StatusChip("OK", StatusTone.SUCCESS)
                                    key.status == CredentialStatus.UNVERIFIED -> StatusChip("Not tested", StatusTone.NEUTRAL)
                                    else -> StatusChip("Error", StatusTone.DANGER)
                                }
                            }
                        }
                        if (providers.size > 6) Text("+${providers.size - 6} more", style = MaterialTheme.typography.bodySmall)
                    }
                    DashboardCard.FAVORITE_MODELS -> OmniCard(title = "Favorite models") {
                        val favs = models.filter { it.isFavorite }
                        if (favs.isEmpty()) Text("Star models in the model browser to pin them here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            favs.take(12).forEach { m ->
                                AssistChip(onClick = { scope.launch { actions.openConversation(viewModel.newChat(m.key), "") } }, label = { Text(m.displayName, maxLines = 1) })
                            }
                        }
                        TextButton(onClick = actions.openModels) { Text("Browse models") }
                    }
                    DashboardCard.RECENT_CONVERSATIONS -> OmniCard(title = "Recent conversations", trailing = { TextButton(onClick = actions.openConversations) { Text("All") } }) {
                        if (recent.isEmpty()) Text("No conversations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        recent.forEach { c ->
                            ListItem(
                                headlineContent = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(listOfNotNull(c.modelId, Format.dateTime(c.updatedAt)).joinToString(" · "), maxLines = 1) },
                                modifier = Modifier.clickable { actions.openConversation(c.id, "") },
                            )
                        }
                    }
                    DashboardCard.USAGE -> OmniCard(title = "Usage", onClick = actions.openUsage) {
                        val u = usage
                        if (u == null) SkeletonBlock(Modifier.fillMaxWidth()) else {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text("Today", style = MaterialTheme.typography.labelMedium)
                                    Text("${Format.tokens(u.todayTokens.toInt())} tokens", style = MaterialTheme.typography.titleMedium)
                                    Text("≈ ${Format.money(u.todayCostUsd, s.currency)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Column {
                                    Text("Last 7 days", style = MaterialTheme.typography.labelMedium)
                                    Text("${Format.tokens(u.weekTokens.toInt())} tokens", style = MaterialTheme.typography.titleMedium)
                                    Text("≈ ${Format.money(u.weekCostUsd, s.currency)} · ${u.weekRequests} requests", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text("Estimates only, based on known pricing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    DashboardCard.QUICK_ACTIONS -> OmniCard(title = "Quick actions") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = actions.openCompare, label = { Text("Compare models") }, leadingIcon = { Icon(Icons.Outlined.Compare, null) })
                            AssistChip(onClick = actions.openPrompts, label = { Text("Prompt library") }, leadingIcon = { Icon(Icons.Outlined.TextSnippet, null) })
                            AssistChip(onClick = actions.openPersonas, label = { Text("Personas") }, leadingIcon = { Icon(Icons.Outlined.Face, null) })
                            AssistChip(onClick = actions.openModels, label = { Text("Models") }, leadingIcon = { Icon(Icons.Outlined.Memory, null) })
                            AssistChip(onClick = actions.openUsage, label = { Text("Usage") }, leadingIcon = { Icon(Icons.Outlined.QueryStats, null) })
                        }
                    }
                }
            }
        }
    }

    if (pickingDefault) ModelPickerSheet(
        providers = providers, models = models,
        selected = settings?.let { s -> s.defaultProviderId?.let { p -> s.defaultModelId?.let { ModelKey(p, it) } } },
        onSelect = { viewModel.setDefaultModel(it); pickingDefault = false },
        onDismiss = { pickingDefault = false },
    )
    if (customizing) settings?.let { s -> CustomizeSheet(s.dashboard, viewModel, onDismiss = { customizing = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeSheet(layout: com.omniai.core.model.DashboardLayout, viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val order = layout.order + DashboardCard.entries.filterNot { it in layout.order }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text("Customize dashboard", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            order.forEachIndexed { i, card ->
                val hidden = card in layout.hidden
                ListItem(
                    headlineContent = { Text(card.label, color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(card, -1) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Move ${card.label} up") }
                            IconButton(onClick = { viewModel.move(card, 1) }, enabled = i < order.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Move ${card.label} down") }
                            IconButton(onClick = { viewModel.toggleHidden(card) }) {
                                Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (hidden) "Show ${card.label}" else "Hide ${card.label}")
                            }
                        }
                    },
                )
            }
            MetaPill("Changes are saved automatically", Modifier.padding(16.dp))
        }
    }
}
