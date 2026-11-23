package com.omniai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.EmptyState
import com.omniai.core.designsystem.component.OmniCard
import com.omniai.core.designsystem.component.SectionHeader
import com.omniai.core.designsystem.component.StatusChip
import com.omniai.core.designsystem.component.StatusTone
import com.omniai.core.designsystem.theme.MonoStyle
import com.omniai.core.model.ToolPermission
import com.omniai.core.ui.Format

@Composable
fun ToolsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val tools by viewModel.toolConfigs.collectAsStateWithLifecycle()
    val logs by viewModel.toolLogs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    BackScaffold("Tools & permissions", onBack, actions = {
        IconButton(onClick = { viewModel.clearToolLogs() }) { Icon(Icons.Outlined.DeleteSweep, "Clear tool log") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    (if (settings.toolsEnabled) "Tool calling is on. " else "Tool calling is off (enable it in Settings › Chat). ") +
                        "Tools run locally on your device. They never execute code or shell commands from model output, and never receive your API keys.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(tools, key = { it.name }) { tool ->
                OmniCard(title = tool.name) {
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolPermission.entries.forEach { p ->
                            FilterChip(selected = tool.permission == p, onClick = { viewModel.setToolPermission(tool.name, p) }, label = { Text(p.label) })
                        }
                    }
                }
            }
            item { SectionHeader("Execution log") }
            if (logs.isEmpty()) item { Text("No tool calls yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(logs, key = { it.id }) { log ->
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(log.toolName, style = MaterialTheme.typography.titleSmall)
                        StatusChip(
                            if (!log.approved) "Declined" else if (log.success) "OK" else "Error",
                            if (!log.approved) StatusTone.WARNING else if (log.success) StatusTone.SUCCESS else StatusTone.DANGER,
                        )
                        Text(Format.dateTime(log.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("args: ${log.argumentsJson}", style = MonoStyle, maxLines = 3)
                    Text("→ ${log.resultPreview}", style = MonoStyle, maxLines = 3)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun ApiLogScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val lines by viewModel.apiLog.lines.collectAsStateWithLifecycle()
    BackScaffold("API log", onBack, actions = {
        IconButton(onClick = { viewModel.apiLog.clear() }) { Icon(Icons.Outlined.DeleteSweep, "Clear log") }
    }) { padding ->
        if (lines.isEmpty()) {
            EmptyState(Icons.Outlined.Build, "No requests logged", "Turn on Debug mode and API request log in Settings › Advanced. Only method, path, status and timing are recorded, with credentials redacted.", Modifier.padding(padding))
        } else {
            SelectionContainer(Modifier.padding(padding)) {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(lines.asReversed()) { Text(it, style = MonoStyle, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
        }
    }
}

enum class InfoDocument(val title: String) { PRIVACY("Privacy policy"), TERMS("Terms of use"), LICENSES("Open-source licenses"), SECURITY("Security notes") }

@Composable
fun InfoDocumentScreen(document: InfoDocument, onBack: () -> Unit) {
    BackScaffold(document.title, onBack) { padding ->
        SelectionContainer(Modifier.padding(padding)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTexts.forDocument(document).forEach { (heading, body) ->
                    if (heading.isNotEmpty()) Text(heading, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

internal object InfoTexts {
    fun forDocument(d: InfoDocument): List<Pair<String, String>> = when (d) {
        InfoDocument.PRIVACY -> listOf(
            "Summary" to "OmniAI has no account system, no OmniAI server, no analytics, no advertising SDKs and no crash reporting service. Everything you configure stays on this device.",
            "What is stored on your device" to "Provider settings, chats, attachments you add, prompts, personas, usage statistics and app settings are stored in the app's private storage. API keys are encrypted with AES-256-GCM using a key held by Android Keystore and are excluded from device backups.",
            "What leaves your device" to "When you send a message, test a connection or refresh models, OmniAI sends that request directly to the provider endpoint you configured, together with your API key for that provider. Nothing is sent to OmniAI. No background requests are made.",
            "Third-party providers" to "Each AI provider processes your prompts and attachments under its own terms and privacy policy. OmniAI cannot control how long a provider retains data or whether it uses it for training. Check each provider's policy and account settings.",
            "Your controls" to "Settings › Privacy & data lets you export your data, delete chats, delete all keys, delete providers, reset usage statistics or clear everything.",
        )
        InfoDocument.TERMS -> listOf(
            "" to "OmniAI is a client for AI services that you access with your own accounts and API keys. You are responsible for complying with each provider's terms, usage policies and billing.",
            "Costs" to "Token counts and costs shown in OmniAI are estimates based on locally measured or provider-reported usage and the prices you or the provider supplied. Your provider's invoice is the only authoritative record of charges.",
            "No warranty" to "The software is provided \"as is\", without warranty of any kind. AI-generated output can be wrong; verify important information.",
        )
        InfoDocument.LICENSES -> listOf(
            "" to "OmniAI is built with the following open-source software:",
            "Apache License 2.0" to "AndroidX (Compose, Material 3, Navigation, Lifecycle, Room, DataStore, WorkManager, Biometric, Core), Kotlin, kotlinx.coroutines, kotlinx.serialization, OkHttp, Okio, Dagger / Hilt, Coil, PdfBox-Android, multiplatform-markdown-renderer, JetBrains Markdown, Highlights.",
            "Eclipse Public License 1.0" to "JUnit 4 (tests only).",
            "Notice" to "Full license texts: https://www.apache.org/licenses/LICENSE-2.0 and https://www.eclipse.org/legal/epl-v10.html",
        )
        InfoDocument.SECURITY -> listOf(
            "Client-side API keys" to "Your keys are stored encrypted on this device and sent only to the provider you configured. Any app that holds a key on a device can, in principle, be inspected by someone with full control of that device (root access, malware, or a debugger). Use provider-side limits: spending caps, per-key restrictions and separate keys per device, and revoke keys you no longer use.",
            "Network" to "Certificate validation is never disabled. Public endpoints must use HTTPS. Private, loopback and cloud metadata addresses are blocked unless you enable local-network endpoints, and the check is repeated at connection time to stop DNS rebinding. Redirects are not followed, so your key can't be forwarded to another host.",
            "Logs" to "API keys are never written to logs, notifications or exports. The optional API log records only method, path, status and timing, with credentials redacted, and is kept in memory.",
            "Tools" to "Model tool calls run only built-in local tools with the permissions you set. OmniAI never runs code or shell commands produced by a model.",
        )
    }
}
