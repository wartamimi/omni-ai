package com.omniai.feature.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.SectionHeader
import com.omniai.core.designsystem.component.SettingClickRow
import com.omniai.core.designsystem.component.SettingSwitchRow
import com.omniai.core.model.AccentColor
import com.omniai.core.model.AppSettings
import com.omniai.core.model.ContextStrategy
import com.omniai.core.model.CurrencyCode
import com.omniai.core.model.ModelKey
import com.omniai.core.model.RoutingMode
import com.omniai.core.model.ThemeMode
import com.omniai.core.security.BiometricGate
import com.omniai.core.security.findFragmentActivity
import com.omniai.core.ui.ModelPickerSheet
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenTools: () -> Unit,
    onOpenApiLog: () -> Unit,
    onOpenDocument: (InfoDocument) -> Unit,
    onOpenPersonas: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showRates by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf<List<String>?>(null) }
    var includeChatsInExport by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = Platform.writeText(context, uri, viewModel.exportAll(includeChatsInExport))
            snackbar.showSnackbar(if (ok) "Exported. API keys are not included." else "Export failed.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Platform.readText(context, uri)?.let(viewModel::importData) ?: scope.launch { snackbar.showSnackbar("Could not read file.") }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item { Header("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { viewModel.update { it.copy(themeMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
            item {
                SettingSwitchRow(
                    "Dynamic color", s.dynamicColor, { v -> viewModel.update { it.copy(dynamicColor = v) } },
                    subtitle = if (android.os.Build.VERSION.SDK_INT >= 31) "Use colors from your wallpaper" else "Requires Android 12 or later",
                    enabled = android.os.Build.VERSION.SDK_INT >= 31,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Accent color", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(AccentColor.entries) { accent ->
                            val selected = s.accent == accent
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(accent.argb))
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { viewModel.update { it.copy(accent = accent) } },
                                contentAlignment = Alignment.Center,
                            ) { if (selected) Icon(Icons.Outlined.Check, contentDescription = "${accent.label} selected", tint = Color.Black) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Text size: ${(s.textScale * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
                    Slider(value = s.textScale, onValueChange = { v -> viewModel.update { it.copy(textScale = (v * 20).toInt() / 20f) } }, valueRange = 0.8f..1.6f, steps = 15)
                }
            }

            item { Header("Defaults") }
            item {
                val p = providers.firstOrNull { it.id == s.defaultProviderId }
                SettingClickRow("Default model", subtitle = "Used for new chats", value = if (p != null) "${p.name} · ${s.defaultModelId ?: "—"}" else "Not set") { showPicker = true }
            }
            item {
                Dropdown(
                    label = "Default persona",
                    options = listOf<Pair<String?, String>>(null to "None") + personas.map { it.id to "${it.avatarEmoji} ${it.name}" },
                    selected = s.defaultPersonaId,
                    onSelect = { id -> viewModel.update { it.copy(defaultPersonaId = id) } },
                )
            }
            item { SettingClickRow("Manage personas", onClick = onOpenPersonas) }

            item { Header("Chat") }
            item { SettingSwitchRow("Streaming responses", s.streaming, { v -> viewModel.update { it.copy(streaming = v) } }, subtitle = "Show text as it arrives (only where the provider supports it)") }
            item { SettingSwitchRow("Render Markdown", s.renderMarkdown, { v -> viewModel.update { it.copy(renderMarkdown = v) } }) }
            item { SettingSwitchRow("Code highlighting", s.codeHighlighting, { v -> viewModel.update { it.copy(codeHighlighting = v) } }, enabled = s.renderMarkdown) }
            item {
                Dropdown(
                    label = "Context retention",
                    options = ContextStrategy.entries.map { it to it.label },
                    selected = s.contextStrategy,
                    onSelect = { v -> viewModel.update { it.copy(contextStrategy = v) } },
                    helper = s.contextStrategy.description,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Max messages sent: ${s.maxContextMessages}", style = MaterialTheme.typography.bodyLarge)
                    Slider(value = s.maxContextMessages.toFloat(), onValueChange = { v -> viewModel.update { it.copy(maxContextMessages = v.toInt()) } }, valueRange = 4f..200f)
                }
            }
            item {
                var draft by remember(s.defaultSystemPrompt) { mutableStateOf(s.defaultSystemPrompt) }
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    label = { Text("Default system prompt") }, minLines = 2, maxLines = 6,
                    supportingText = { Text("Used for new chats without a persona") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    trailingIcon = if (draft != s.defaultSystemPrompt) ({ TextButton(onClick = { viewModel.update { it.copy(defaultSystemPrompt = draft) } }) { Text("Save") } }) else null,
                )
            }
            item { SettingSwitchRow("Allow tool calling", s.toolsEnabled, { v -> viewModel.update { it.copy(toolsEnabled = v) } }, subtitle = "Models may call local tools you allow (calculator, date/time, converters)") }
            item { SettingClickRow("Tools & permissions", icon = Icons.Outlined.Build, onClick = onOpenTools) }

            item { Header("Compare & routing") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Models per comparison: ${s.compareLimit}", style = MaterialTheme.typography.bodyLarge)
                    Slider(value = s.compareLimit.toFloat(), onValueChange = { v -> viewModel.update { it.copy(compareLimit = v.toInt()) } }, valueRange = 2f..12f, steps = 9)
                }
            }
            item {
                Dropdown("Default routing mode", RoutingMode.entries.map { it to it.label }, s.routingMode, { v -> viewModel.update { it.copy(routingMode = v) } },
                    helper = "Routing suggestions use only local metadata and history. Your prompt is never sent anywhere to decide.")
            }

            item { Header("Usage & currency") }
            item {
                Dropdown("Display currency", CurrencyCode.entries.map { it to "${it.name} · ${it.displayName}" }, s.currency.display, { v ->
                    viewModel.update { it.copy(currency = it.currency.copy(display = v)) }
                })
            }
            item {
                val rate = s.currency.rateFor(s.currency.display)
                SettingClickRow(
                    "Exchange rates",
                    subtitle = "Entered by you; OmniAI does not fetch rates online",
                    value = if (s.currency.display == CurrencyCode.USD) "USD" else rate?.let { "1 USD = $it ${s.currency.display.name}" } ?: "Not set",
                ) { showRates = true }
            }

            item { Header("Network") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Default request timeout: ${s.requestTimeoutSeconds} s", style = MaterialTheme.typography.bodyLarge)
                    Slider(value = s.requestTimeoutSeconds.toFloat(), onValueChange = { v -> viewModel.update { it.copy(requestTimeoutSeconds = (v / 10).toInt() * 10) } }, valueRange = 10f..600f)
                    Text("Retries on rate limits / unavailable: ${s.maxRetries}", style = MaterialTheme.typography.bodyLarge)
                    Slider(value = s.maxRetries.toFloat(), onValueChange = { v -> viewModel.update { it.copy(maxRetries = v.toInt()) } }, valueRange = 0f..5f, steps = 4)
                    Text(
                        "Retries only happen when the provider rejected the request before generating anything, so they cannot duplicate charges. Per-provider timeout and headers are set in each provider. The system proxy (Wi-Fi settings) is used automatically.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingSwitchRow("Allow local-network endpoints", s.allowLocalNetworkEndpoints, { v -> viewModel.update { it.copy(allowLocalNetworkEndpoints = v, allowCleartextLocalEndpoints = v && it.allowCleartextLocalEndpoints) } },
                    subtitle = "For model servers on your own network (Ollama, LM Studio). Off blocks private and loopback addresses.")
            }
            item {
                SettingSwitchRow("Allow http:// for local endpoints", s.allowCleartextLocalEndpoints, { v -> viewModel.update { it.copy(allowCleartextLocalEndpoints = v) } },
                    subtitle = "Unencrypted traffic stays on your network. Public endpoints always require https.", enabled = s.allowLocalNetworkEndpoints)
            }
            item { SettingClickRow("Network diagnostics", subtitle = "Test every enabled provider", icon = Icons.Outlined.NetworkCheck) { viewModel.runDiagnostics { diagnostics = it } } }

            item { Header("Privacy & data") }
            item {
                SettingSwitchRow("Biometric lock for API keys", s.biometricLock, { v ->
                    val activity = context.findFragmentActivity()
                    if (!v) viewModel.update { it.copy(biometricLock = false) }
                    else if (activity != null) BiometricGate.authenticate(activity, "Enable biometric lock", "Confirm it's you") { ok, err ->
                        if (ok) viewModel.update { it.copy(biometricLock = true) } else scope.launch { snackbar.showSnackbar(err ?: "Authentication failed") }
                    }
                }, subtitle = "Require fingerprint, face or screen lock to manage keys")
            }
            item { SettingSwitchRow("Include chats in export", includeChatsInExport, { includeChatsInExport = it }) }
            item {
                SettingClickRow("Export data", subtitle = "JSON backup of settings, providers, chats, prompts and personas. API keys are never exported.", icon = Icons.Outlined.Download) {
                    exportLauncher.launch("omniai-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json")
                }
            }
            item { SettingClickRow("Import data", subtitle = "Restore an OmniAI JSON export", icon = Icons.Outlined.Upload) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }
            item { SettingClickRow("Delete conversation history", icon = Icons.Outlined.Delete) { confirm = ConfirmAction.HISTORY } }
            item { SettingClickRow("Delete all API keys", icon = Icons.Outlined.Delete) { confirm = ConfirmAction.CREDENTIALS } }
            item { SettingClickRow("Delete all providers", icon = Icons.Outlined.Delete) { confirm = ConfirmAction.PROVIDERS } }
            item { SettingClickRow("Reset usage statistics", icon = Icons.Outlined.Delete) { confirm = ConfirmAction.USAGE } }
            item { SettingClickRow("Clear all app data", subtitle = "Everything above plus settings", icon = Icons.Outlined.Delete) { confirm = ConfirmAction.ALL } }
            item {
                Text(
                    "Prompts and attachments you send go directly from this device to the provider you choose, and are processed under that provider's own policies. OmniAI can't control how long providers keep data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                )
            }

            item { Header("Advanced") }
            item { SettingSwitchRow("Debug mode", s.debugMode, { v -> viewModel.update { it.copy(debugMode = v, apiLogging = v && it.apiLogging) } }) }
            item { SettingSwitchRow("API request log (redacted)", s.apiLogging, { v -> viewModel.update { it.copy(apiLogging = v) } }, subtitle = "Method, URL path, status and timing only. Kept in memory, never saved.", enabled = s.debugMode) }
            item { SettingClickRow("View API log", icon = Icons.AutoMirrored.Outlined.List, onClick = onOpenApiLog) }

            item { Header("About") }
            item {
                val (version, build) = remember { appVersion(context) }
                SettingClickRow("OmniAI", subtitle = "One App. All AI.", value = "v$version ($build)", icon = Icons.Outlined.Info) {}
            }
            item { SettingClickRow("Privacy policy", icon = Icons.Outlined.Policy) { onOpenDocument(InfoDocument.PRIVACY) } }
            item { SettingClickRow("Terms of use", icon = Icons.Outlined.Description) { onOpenDocument(InfoDocument.TERMS) } }
            item { SettingClickRow("Open-source licenses", icon = Icons.Outlined.Description) { onOpenDocument(InfoDocument.LICENSES) } }
            item { SettingClickRow("Security notes", icon = Icons.Outlined.Policy) { onOpenDocument(InfoDocument.SECURITY) } }
        }
    }

    if (showPicker) {
        ModelPickerSheet(
            providers = providers, models = models,
            selected = s.defaultProviderId?.let { p -> s.defaultModelId?.let { ModelKey(p, it) } },
            onSelect = { key -> viewModel.update { it.copy(defaultProviderId = key.providerId, defaultModelId = key.modelId) }; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
    if (showRates) RatesDialog(s, onSave = { rates -> viewModel.update { it.copy(currency = it.currency.copy(usdRates = rates)) } }, onDismiss = { showRates = false })
    diagnostics?.let { lines ->
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text("Network diagnostics") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } },
            confirmButton = { TextButton(onClick = { diagnostics = null }) { Text("Close") } },
        )
    }
    confirm?.let { action ->
        ConfirmDialog(
            title = action.title, text = action.text, confirmLabel = "Delete",
            onConfirm = {
                when (action) {
                    ConfirmAction.HISTORY -> viewModel.clearHistory()
                    ConfirmAction.CREDENTIALS -> viewModel.deleteCredentials()
                    ConfirmAction.PROVIDERS -> viewModel.deleteProviders()
                    ConfirmAction.USAGE -> viewModel.resetUsage()
                    ConfirmAction.ALL -> viewModel.clearAll()
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

private enum class ConfirmAction(val title: String, val text: String) {
    HISTORY("Delete all chats?", "All conversations, messages and attachments on this device will be deleted. This can't be undone."),
    CREDENTIALS("Delete all API keys?", "Every stored API key will be erased and the encryption key destroyed. Providers stay configured but need new keys."),
    PROVIDERS("Delete all providers?", "All provider configurations, their models and API keys will be deleted."),
    USAGE("Reset usage statistics?", "All recorded token usage and cost estimates will be deleted."),
    ALL("Clear all data?", "Chats, providers, API keys, usage history and settings will be deleted. Prompts and personas are kept."),
}

@Composable
private fun Header(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        SectionHeader(text, Modifier.padding(horizontal = 16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Dropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, helper: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = helper?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun RatesDialog(s: AppSettings, onSave: (Map<CurrencyCode, Double>) -> Unit, onDismiss: () -> Unit) {
    val drafts = remember { CurrencyCode.entries.filter { it != CurrencyCode.USD }.associateWith { mutableStateOf(s.currency.usdRates[it]?.toString() ?: "") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exchange rates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Units per 1 USD. Use your bank's or a trusted source's current rate. Costs stay in USD for currencies without a rate.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.height(320.dp)) {
                    items(drafts.keys.toList()) { code ->
                        val state = drafts.getValue(code)
                        OutlinedTextField(
                            value = state.value, onValueChange = { state.value = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("${code.name} (${code.displayName})") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(drafts.mapNotNull { (code, st) -> st.value.toDoubleOrNull()?.takeIf { it > 0 }?.let { code to it } }.toMap())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun appVersion(context: Context): Pair<String, Long> = runCatching {
    val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    (info.versionName ?: "?") to code
}.getOrDefault("?" to 0L)
