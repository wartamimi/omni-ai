package com.omniai.feature.providers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.EmptyState
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.designsystem.component.ProviderAvatar
import com.omniai.core.designsystem.component.SectionHeader
import com.omniai.core.designsystem.component.SkeletonBlock
import com.omniai.core.designsystem.component.StatusChip
import com.omniai.core.designsystem.component.StatusTone
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.SupportLevel
import com.omniai.core.provider.ProviderCatalog
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch

@Composable
fun ProvidersScreen(
    onOpenEditor: (ProviderEditorRoute) -> Unit,
    onOpenModels: (String) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProviderRow?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = Platform.writeText(context, uri, viewModel.exportConfiguration())
            snackbar.showSnackbar(if (ok) "Configuration exported (API keys are never included)." else "Could not write the file.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = Platform.readText(context, uri)
            if (text == null) scope.launch { snackbar.showSnackbar("Could not read the file.") } else viewModel.importConfiguration(text)
        }
    }

    BackScaffold(
        title = "Providers",
        onBack = null,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Outlined.FileUpload, "Import configuration") }
            IconButton(onClick = { exportLauncher.launch("omniai-providers.json") }) { Icon(Icons.Outlined.FileDownload, "Export configuration") }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCatalog = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add provider") })
        },
    ) { padding ->
        val list = rows
        when {
            list == null -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { SkeletonBlock(Modifier.fillMaxWidth(), height = 72.dp) }
            }
            list.isEmpty() -> EmptyState(
                Icons.Outlined.Cloud, "No providers yet",
                "Add a provider and paste your own API key. Keys are encrypted on this device and only sent to the provider you choose.",
                Modifier.padding(padding), actionLabel = "Add provider", onAction = { showCatalog = true },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.config.id }) { row ->
                    ProviderCard(
                        row = row,
                        onClick = { onOpenEditor(ProviderEditorRoute(providerId = row.config.id)) },
                        onTest = { viewModel.test(row.config.id) },
                        onRefresh = { viewModel.refreshModels(row.config.id) },
                        onModels = { onOpenModels(row.config.id) },
                        onToggle = { viewModel.setEnabled(row.config.id, it) },
                        onDefault = { viewModel.setDefault(row.config.id) },
                        onDuplicate = { viewModel.duplicate(row.config.id) },
                        onDelete = { pendingDelete = row },
                    )
                }
            }
        }
    }

    if (showCatalog) CatalogSheet(
        onDismiss = { showCatalog = false },
        onPick = { showCatalog = false; onOpenEditor(it) },
    )
    pendingDelete?.let { row ->
        ConfirmDialog(
            title = "Delete ${row.config.name}?",
            text = "This removes the provider, its ${row.keys.size} stored key(s) and its model list from this device. Conversations are kept.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(row.config.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ProviderCard(
    row: ProviderRow,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit,
    onModels: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = row.config
    var menu by remember { mutableStateOf(false) }
    OmniCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderAvatar(c.name, c.iconEmoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (row.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Switch(checked = c.enabled, onCheckedChange = onToggle, modifier = Modifier.padding(start = 8.dp))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Test connection") }, onClick = { menu = false; onTest() }, enabled = !row.busy)
                    if (c.modelsPath != null) DropdownMenuItem(text = { Text("Refresh models") }, onClick = { menu = false; onRefresh() }, enabled = !row.busy)
                    DropdownMenuItem(text = { Text("Browse models") }, onClick = { menu = false; onModels() })
                    if (!c.isDefault) DropdownMenuItem(text = { Text("Set as default") }, onClick = { menu = false; onDefault() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val key = row.activeKey
            when {
                c.auth.type == com.omniai.core.model.AuthType.NONE -> StatusChip("No key needed", StatusTone.NEUTRAL)
                key == null -> StatusChip("No API key", StatusTone.WARNING)
                key.status == CredentialStatus.VALID -> StatusChip("Connected", StatusTone.SUCCESS)
                key.status == CredentialStatus.INVALID -> StatusChip("Invalid key", StatusTone.DANGER)
                key.status == CredentialStatus.ERROR -> StatusChip("Last test failed", StatusTone.DANGER)
                else -> StatusChip("Not tested", StatusTone.NEUTRAL)
            }
            if (c.isDefault) MetaPill("Default", emphasized = true)
            MetaPill("${row.modelCount} models")
            key?.let { MetaPill(it.maskedHint) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSheet(onDismiss: () -> Unit, onPick: (ProviderEditorRoute) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Text("Add a provider", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            ProviderCategory.entries.filter { it != ProviderCategory.CUSTOM }.forEach { category ->
                val defs = ProviderCatalog.definitions.filter { it.category == category }
                if (defs.isNotEmpty()) {
                    item { SectionHeader(category.label, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    items(defs, key = { it.id }) { def ->
                        val supported = def.supportLevel != SupportLevel.UNSUPPORTED
                        ListItem(
                            headlineContent = { Text(def.displayName) },
                            supportingContent = {
                                Text(
                                    if (supported) def.protocol.displayName else def.notes,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { ProviderAvatar(def.displayName, size = 36.dp) },
                            trailingContent = {
                                when (def.supportLevel) {
                                    SupportLevel.SUPPORTED -> {}
                                    SupportLevel.COMPATIBLE -> MetaPill("Compatible")
                                    SupportLevel.UNSUPPORTED -> MetaPill("Not supported")
                                }
                            },
                            modifier = Modifier.clickable(enabled = supported) { onPick(ProviderEditorRoute(definitionId = def.id)) },
                        )
                    }
                }
            }
            item { SectionHeader("Custom API", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(ProviderCatalog.customTemplates.size) { index ->
                val t = ProviderCatalog.customTemplates[index]
                ListItem(
                    headlineContent = { Text(t.label) },
                    supportingContent = { Text(t.protocol.displayName) },
                    modifier = Modifier.clickable { onPick(ProviderEditorRoute(templateIndex = index)) },
                )
            }
        }
    }
}
