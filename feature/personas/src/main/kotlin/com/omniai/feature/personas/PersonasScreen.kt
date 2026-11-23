package com.omniai.feature.personas

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.model.AIModel
import com.omniai.core.model.ModelKey
import com.omniai.core.model.Persona
import com.omniai.core.model.ProviderConfig
import com.omniai.core.ui.ModelPickerSheet
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object PersonasRoute

fun NavGraphBuilder.personasGraph(onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    composable<PersonasRoute> { PersonasScreen(onBack, onOpenConversation) }
}

@Composable
fun PersonasScreen(onBack: () -> Unit, onOpenConversation: (String) -> Unit, viewModel: PersonasViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providerList.collectAsStateWithLifecycle()
    val models by viewModel.modelList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Persona?>(null) }
    var deleting by remember { mutableStateOf<Persona?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { snackbar.showSnackbar(if (Platform.writeText(context, uri, viewModel.exportJson())) "Exported." else "Export failed.") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Platform.readText(context, uri)?.let(viewModel::importJson)
    }

    BackScaffold(
        title = "Personas",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Outlined.FileUpload, "Import personas") }
            IconButton(onClick = { exportLauncher.launch("omniai-personas.json") }) { Icon(Icons.Outlined.FileDownload, "Export personas") }
        },
        floatingActionButton = { FloatingActionButton(onClick = { editing = viewModel.newPersona() }) { Icon(Icons.Outlined.Add, "New persona") } },
    ) { padding ->
        val personas = list
        if (personas != null && personas.isEmpty()) {
            EmptyState(Icons.Outlined.Face, "No personas", "A persona bundles a system prompt, parameters and an optional default model.", Modifier.padding(padding))
            return@BackScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(personas.orEmpty(), key = { it.id }) { p ->
                val isDefault = settings.defaultPersonaId == p.id
                OmniCard(onClick = { editing = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.avatarEmoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isDefault) MetaPill("Default", emphasized = true)
                        if (p.isBuiltIn) MetaPill("Built-in")
                        p.defaultModelId?.let { MetaPill(it) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { onOpenConversation(viewModel.startChat(p)) } }) { Text("Chat") }
                    }
                }
            }
        }
    }

    editing?.let { p ->
        PersonaEditor(
            persona = p, providers = providers, models = models, isDefault = settings.defaultPersonaId == p.id,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = if (list.orEmpty().any { it.id == p.id }) ({ deleting = p; editing = null }) else null,
            onSetDefault = { viewModel.setDefault(if (it) p else null) },
        )
    }
    deleting?.let { p -> ConfirmDialog("Delete ${p.name}?", "Existing conversations keep their system prompt.", "Delete", { viewModel.delete(p) }, { deleting = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaEditor(
    persona: Persona,
    providers: List<ProviderConfig>,
    models: List<AIModel>,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit,
    onDelete: (() -> Unit)?,
    onSetDefault: (Boolean) -> Unit,
) {
    var p by remember { mutableStateOf(persona) }
    var temp by remember { mutableStateOf(persona.temperature?.toString().orEmpty()) }
    var maxTokens by remember { mutableStateOf(persona.maxOutputTokens?.toString().orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(p.avatarEmoji, { v -> p = p.copy(avatarEmoji = v.take(4)) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(88.dp))
                OutlinedTextField(p.name, { v -> p = p.copy(name = v) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(p.description, { v -> p = p.copy(description = v) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.systemPrompt, { v -> p = p.copy(systemPrompt = v) }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(temp, { temp = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, label = { Text("Temperature") }, placeholder = { Text("default") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(maxTokens, { maxTokens = it.filter(Char::isDigit).take(7) }, label = { Text("Max output tokens") }, placeholder = { Text("default") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("Default model: " + (p.defaultModelId?.let { m -> "${providers.firstOrNull { it.id == p.defaultProviderId }?.name ?: "?"} · $m" } ?: "use the app default"), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text("Choose model") }
                if (p.defaultModelId != null) TextButton(onClick = { p = p.copy(defaultProviderId = null, defaultModelId = null) }) { Text("Clear") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(isDefault, onSetDefault)
                Text("Use for new chats by default")
            }
            Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(p.copy(temperature = temp.toDoubleOrNull()?.coerceIn(0.0, 2.0), maxOutputTokens = maxTokens.toIntOrNull())) },
                    enabled = p.name.isNotBlank(),
                ) { Text("Save") }
            }
        }
    }
    if (picking) ModelPickerSheet(
        providers = providers, models = models,
        selected = p.defaultProviderId?.let { pid -> p.defaultModelId?.let { ModelKey(pid, it) } },
        onSelect = { key -> p = p.copy(defaultProviderId = key.providerId, defaultModelId = key.modelId); picking = false },
        onDismiss = { picking = false },
    )
}
