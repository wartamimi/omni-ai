package com.omniai.feature.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omniai.core.common.EndpointValidator
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.ErrorBanner
import com.omniai.core.designsystem.component.KeyValueRow
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.designsystem.component.SecretField
import com.omniai.core.designsystem.component.SettingSwitchRow
import com.omniai.core.designsystem.component.StatusChip
import com.omniai.core.designsystem.component.StatusTone
import com.omniai.core.designsystem.theme.MonoStyle
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.AuthConfig
import com.omniai.core.model.AuthType
import com.omniai.core.model.CredentialInfo
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.CustomApiMapping
import com.omniai.core.model.HeaderEntry
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.SupportLevel
import com.omniai.core.provider.AnthropicVersions
import com.omniai.core.security.BiometricGate
import com.omniai.core.security.findFragmentActivity
import com.omniai.core.ui.Format
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditorScreen(
    onBack: () -> Unit,
    onOpenModels: (String) -> Unit,
    viewModel: ProviderEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showJson by rememberSaveable { mutableStateOf(false) }
    var keysUnlocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) snackbar.showSnackbar("Saved.") }

    val draft = state.draft
    BackScaffold(
        title = if (state.isNew) "Add provider" else draft?.name ?: "Provider",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { showJson = true }, enabled = draft != null) { Icon(Icons.Outlined.Code, "Edit as JSON") }
            TextButton(onClick = viewModel::save, enabled = draft != null && !state.saving) { Text("Save") }
        },
    ) { padding ->
        if (draft == null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            return@BackScaffold
        }
        val update: ((ProviderConfig) -> ProviderConfig) -> Unit = viewModel::edit
        val isCustom = draft.category == ProviderCategory.CUSTOM || draft.definitionId == null
        val urlCheck = remember(draft.baseUrl, settings.allowLocalNetworkEndpoints, settings.allowCleartextLocalEndpoints) {
            EndpointValidator.validateBaseUrl(draft.baseUrl, EndpointValidator.Policy(settings.allowLocalNetworkEndpoints, settings.allowCleartextLocalEndpoints))
        }

        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ErrorBanner(it) }

            state.definition?.let { def ->
                OmniCard(title = def.displayName) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetaPill(def.supportLevel.label, emphasized = def.supportLevel == SupportLevel.SUPPORTED)
                        MetaPill(def.protocol.displayName)
                    }
                    if (def.notes.isNotBlank()) Text(def.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { Platform.openUrl(context, def.docsUrl) }, modifier = Modifier.padding(top = 4.dp)) { Text("Open official API docs") }
                }
            }

            OmniCard(title = "Connection") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(draft.name, { v -> update { it.copy(name = v) } }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        draft.baseUrl, { v -> update { it.copy(baseUrl = v.trim()) } },
                        label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        isError = urlCheck is EndpointValidator.Result.Invalid,
                        supportingText = { (urlCheck as? EndpointValidator.Result.Invalid)?.let { Text(it.reason) } },
                    )
                    if (isCustom) {
                        EnumDropdown("API protocol", draft.protocol, ApiProtocol.entries, { it.displayName }) { p ->
                            update {
                                it.copy(
                                    protocol = p,
                                    customMapping = if (p == ApiProtocol.CUSTOM_REST) it.customMapping ?: ProviderEditorViewModel.DEFAULT_MAPPING else it.customMapping,
                                    apiVersion = if (p == ApiProtocol.ANTHROPIC_MESSAGES) it.apiVersion ?: AnthropicVersions.DEFAULT else it.apiVersion,
                                )
                            }
                        }
                    }
                    EnumDropdown("Authentication", draft.auth.type, AuthType.entries, ::authLabel) { t ->
                        update { it.copy(auth = AuthConfig(t, if (t == AuthType.HEADER || t == AuthType.QUERY) it.auth.name ?: (if (t == AuthType.HEADER) "x-api-key" else "key") else null)) }
                    }
                    if (draft.auth.type == AuthType.HEADER || draft.auth.type == AuthType.QUERY) {
                        OutlinedTextField(
                            draft.auth.name.orEmpty(), { v -> update { it.copy(auth = it.auth.copy(name = v.trim())) } },
                            label = { Text(if (draft.auth.type == AuthType.HEADER) "Header name" else "Query parameter name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            supportingText = { if (draft.auth.type == AuthType.QUERY) Text("Keys in URLs can end up in proxy logs. Prefer a header when the API allows it.") },
                        )
                    }
                    if (draft.protocol != ApiProtocol.CUSTOM_REST && draft.protocol != ApiProtocol.GEMINI_GENERATE_CONTENT) {
                        OutlinedTextField(draft.chatPath.orEmpty(), { v -> update { it.copy(chatPath = v.trim().ifBlank { null }) } }, label = { Text("Chat path") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
                    }
                    OutlinedTextField(
                        draft.modelsPath.orEmpty(), { v -> update { it.copy(modelsPath = v.trim().ifBlank { null }) } },
                        label = { Text("Models list path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle,
                        supportingText = { Text("Leave empty if the API has no model list. You can still add model IDs by hand.") },
                    )
                    if (draft.protocol == ApiProtocol.ANTHROPIC_MESSAGES) {
                        EnumDropdown("anthropic-version", draft.apiVersion ?: AnthropicVersions.DEFAULT, listOf(AnthropicVersions.DEFAULT, AnthropicVersions.LATEST), { v ->
                            if (AnthropicVersions.rejectsSamplingParams(v)) "$v (no temperature/top_p)" else v
                        }) { v -> update { it.copy(apiVersion = v) } }
                    }
                    OutlinedTextField(draft.defaultModelId.orEmpty(), { v -> update { it.copy(defaultModelId = v.trim().ifBlank { null }) } }, label = { Text("Default model ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
                }
            }

            // API keys
            OmniCard(title = "API key") {
                if (draft.auth.type == AuthType.NONE) {
                    Text("This provider is set to use no authentication.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val locked = settings.biometricLock && !keysUnlocked && keys.isNotEmpty()
                    if (locked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null)
                            Text("Stored keys are locked.", Modifier.padding(start = 8.dp).weight(1f))
                            Button(onClick = {
                                val activity = context.findFragmentActivity()
                                if (activity == null) scope.launch { snackbar.showSnackbar("Biometric prompt is not available here.") }
                                else BiometricGate.authenticate(activity, "Manage API keys", draft.name) { ok, err ->
                                    if (ok) keysUnlocked = true else scope.launch { snackbar.showSnackbar(err ?: "Authentication failed") }
                                }
                            }) { Text("Unlock") }
                        }
                    } else {
                        keys.forEach { key -> KeyRow(key, viewModel) }
                        if (keys.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        Text(
                            if (keys.isEmpty()) "Paste your API key. It's encrypted with the Android Keystore and never leaves this device except in requests to ${draft.baseUrl.substringAfter("://").substringBefore('/')}."
                            else "Add another key (e.g. for a second project). The active key is used for requests.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        SecretField(state.newKey, viewModel::setNewKey, label = "API key", placeholder = state.definition?.keyPrefixHint?.let { "$it…" })
                        OutlinedTextField(state.newKeyLabel, viewModel::setNewKeyLabel, label = { Text("Key label") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        if (!state.isNew && state.newKey.isNotBlank()) {
                            OutlinedButton(onClick = { viewModel.addKey(state.newKeyLabel, state.newKey); viewModel.setNewKey("") }, modifier = Modifier.padding(top = 8.dp)) { Text("Add key") }
                        }
                    }
                }
            }

            OmniCard(title = "Test") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::test, enabled = !state.testing && urlCheck is EndpointValidator.Result.Valid) {
                        if (state.testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test connection")
                    }
                    if (!state.isNew) OutlinedButton(onClick = { onOpenModels(draft.id) }) { Text("Models") }
                }
                state.testResult?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    StatusChip(if (r.success) "Connection OK" else r.error?.title ?: "Failed", if (r.success) StatusTone.SUCCESS else StatusTone.DANGER)
                    Text(r.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    KeyValueRow("Latency", Format.latency(r.latencyMs))
                    r.httpStatus?.let { KeyValueRow("HTTP status", it.toString()) }
                    r.modelCount?.let { KeyValueRow("Models found", it.toString()) }
                }
                Text(
                    "Testing uses the models endpoint when available, otherwise a 1-token request (this may cost a tiny amount).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (draft.protocol == ApiProtocol.CUSTOM_REST) MappingEditor(draft.customMapping ?: ProviderEditorViewModel.DEFAULT_MAPPING) { m -> update { it.copy(customMapping = m) } }

            AdvancedSection(draft, update)

            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(if (state.isNew) "Save provider" else "Save changes") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showJson) JsonDialog(
        initial = viewModel.configAsJson(),
        onApply = { raw -> viewModel.applyJson(raw) },
        onDismiss = { showJson = false },
    )
}

private fun authLabel(t: AuthType) = when (t) {
    AuthType.BEARER -> "Authorization: Bearer"
    AuthType.HEADER -> "Custom header"
    AuthType.QUERY -> "Query parameter"
    AuthType.NONE -> "None (local server)"
}

@Composable
private fun KeyRow(key: CredentialInfo, viewModel: ProviderEditorViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(key.label + if (key.isActive) " · active" else "", style = MaterialTheme.typography.titleSmall)
                Text(key.maskedHint, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(
                when (key.status) { CredentialStatus.VALID -> "Valid"; CredentialStatus.INVALID -> "Invalid"; CredentialStatus.ERROR -> "Error"; CredentialStatus.UNVERIFIED -> "Not tested" },
                when (key.status) { CredentialStatus.VALID -> StatusTone.SUCCESS; CredentialStatus.INVALID, CredentialStatus.ERROR -> StatusTone.DANGER; else -> StatusTone.NEUTRAL },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!key.isActive) TextButton(onClick = { viewModel.activateKey(key.id) }) { Text("Use") }
            TextButton(onClick = { renaming = true }) { Text("Rename") }
            TextButton(onClick = { replacing = true }) { Text("Replace") }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete key \"${key.label}\"?", "The encrypted key is erased from this device. This can't be undone.", "Delete", { viewModel.deleteKey(key.id) }, { confirmDelete = false })
    if (replacing) TextInputDialog("Replace key", "New API key", secret = true, initial = "", onDismiss = { replacing = false }) { viewModel.replaceKey(key.id, it) }
    if (renaming) TextInputDialog("Rename key", "Label", secret = false, initial = key.label, onDismiss = { renaming = false }) { viewModel.renameKey(key.id, it) }
}

@Composable
private fun TextInputDialog(title: String, label: String, secret: Boolean, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (secret) SecretField(value, { value = it }, label = label)
            else OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()); onDismiss() }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AdvancedSection(draft: ProviderConfig, update: ((ProviderConfig) -> ProviderConfig) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OmniCard(title = "Advanced", onClick = { open = !open }) {
        if (!open) {
            Text("Timeout, retries, streaming, custom headers and parameters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@OmniCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSwitchRow("Streaming", draft.streamingEnabled, { v -> update { it.copy(streamingEnabled = v) } }, subtitle = "Show the answer as it's generated")
            NumberField("Timeout (seconds)", draft.timeoutSeconds, 10..600) { v -> update { it.copy(timeoutSeconds = v) } }
            NumberField("Retries on rate limit / unavailable", draft.maxRetries, 0..5) { v -> update { it.copy(maxRetries = v) } }
            Text("Retries happen only before any response is received, so a request is never charged twice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Custom headers", style = MaterialTheme.typography.titleSmall)
            draft.customHeaders.forEachIndexed { i, h ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(h.name, { v -> update { c -> c.copy(customHeaders = c.customHeaders.toMutableList().also { it[i] = h.copy(name = v.trim()) }) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(h.value, { v -> update { c -> c.copy(customHeaders = c.customHeaders.toMutableList().also { it[i] = h.copy(value = v) }) } }, label = { Text("Value") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { update { c -> c.copy(customHeaders = c.customHeaders.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Outlined.Close, "Remove header") }
                }
            }
            TextButton(onClick = { update { it.copy(customHeaders = it.customHeaders + HeaderEntry("", "")) } }) { Text("Add header") }
            Text("Don't put API keys in custom headers; use the API key field so they're encrypted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                draft.customParamsJson.orEmpty(), { v -> update { it.copy(customParamsJson = v.ifBlank { null }) } },
                label = { Text("Extra request parameters (JSON object)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), textStyle = MonoStyle,
                placeholder = { Text("{\"user\": \"omniai\"}") },
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { v ->
            text = v.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.let { if (it in range) onChange(it) }
        },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = text.toIntOrNull()?.let { it !in range } ?: true,
        supportingText = { Text("${range.first}–${range.last}") },
    )
}

@Composable
private fun MappingEditor(m: CustomApiMapping, onChange: (CustomApiMapping) -> Unit) {
    OmniCard(title = "Request & response mapping") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("POST", "PUT").forEach { method -> FilterChip(selected = m.httpMethod == method, onClick = { onChange(m.copy(httpMethod = method)) }, label = { Text(method) }) }
            }
            OutlinedTextField(m.endpointPath, { onChange(m.copy(endpointPath = it.trim())) }, label = { Text("Endpoint path") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
            OutlinedTextField(
                m.bodyTemplate, { onChange(m.copy(bodyTemplate = it)) }, label = { Text("Body template (JSON)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), textStyle = MonoStyle,
                supportingText = { Text("Placeholders: {{model}} {{messages}} {{prompt}} {{system}} {{temperature}} {{max_tokens}} {{stream}} (inserted as JSON values)") },
            )
            OutlinedTextField(m.responseTextPath, { onChange(m.copy(responseTextPath = it.trim())) }, label = { Text("Response text path") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
            OutlinedTextField(m.inputTokensPath.orEmpty(), { onChange(m.copy(inputTokensPath = it.trim().ifBlank { null })) }, label = { Text("Input tokens path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
            OutlinedTextField(m.outputTokensPath.orEmpty(), { onChange(m.copy(outputTokensPath = it.trim().ifBlank { null })) }, label = { Text("Output tokens path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
            OutlinedTextField(m.errorMessagePath.orEmpty(), { onChange(m.copy(errorMessagePath = it.trim().ifBlank { null })) }, label = { Text("Error message path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle)
            OutlinedTextField(
                m.streamDeltaPath.orEmpty(), { onChange(m.copy(streamDeltaPath = it.trim().ifBlank { null })) },
                label = { Text("SSE stream delta path (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MonoStyle,
                supportingText = { Text("Leave empty if the API doesn't stream with Server-Sent Events.") },
            )
        }
    }
}

@Composable
private fun JsonDialog(initial: String, onApply: (String) -> String?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provider JSON") },
        text = {
            Column {
                Text("API keys are not part of this JSON.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it; error = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp).padding(top = 8.dp), textStyle = MonoStyle, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { val e = onApply(text); if (e == null) onDismiss() else error = e }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> EnumDropdown(label: String, value: T, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display(value), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); expanded = false }) }
        }
    }
}
