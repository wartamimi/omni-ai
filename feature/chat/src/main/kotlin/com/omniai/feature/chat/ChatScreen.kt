package com.omniai.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.omniai.core.designsystem.component.ErrorBanner
import com.omniai.core.designsystem.component.MetaPill
import com.omniai.core.designsystem.theme.MonoStyle
import com.omniai.core.domain.chat.ToolCallCodec
import com.omniai.core.domain.router.RouteCandidate
import com.omniai.core.domain.router.RoutingTask
import com.omniai.core.model.Attachment
import com.omniai.core.model.AttachmentType
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.MessageRole
import com.omniai.core.model.MessageStatus
import com.omniai.core.model.ModelKey
import com.omniai.core.model.RoutingMode
import com.omniai.core.ui.Format
import com.omniai.core.ui.MarkdownText
import com.omniai.core.ui.ModelPickerSheet
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providers by viewModel.providerList.collectAsStateWithLifecycle()
    val models by viewModel.modelList.collectAsStateWithLifecycle()
    val personas by viewModel.personaList.collectAsStateWithLifecycle()
    val prompts by viewModel.promptList.collectAsStateWithLifecycle()
    val provider by viewModel.currentProvider.collectAsStateWithLifecycle()
    val supported by viewModel.supportedParams.collectAsStateWithLifecycle()
    val pending by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val approval by viewModel.approvals.pending.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var showParams by rememberSaveable { mutableStateOf(false) }
    var showPersonas by rememberSaveable { mutableStateOf(false) }
    var showPrompts by rememberSaveable { mutableStateOf(false) }
    var showRouter by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var inspecting by remember { mutableStateOf<Attachment?>(null) }
    var exportMarkdown by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    val messages = ui?.messages.orEmpty()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.take(5).forEach(viewModel::addAttachment) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) scope.launch {
            val text = if (exportMarkdown) viewModel.exportMarkdown() else viewModel.exportJson()
            snackbar.showSnackbar(if (text != null && Platform.writeText(context, uri, text)) "Exported." else "Export failed.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column(Modifier.clickable { showModelPicker = true }) {
                        Text(conversation?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(provider?.name, conversation?.modelId).joinToString(" · ").ifBlank { "Choose a model" },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showParams = true }) { Icon(Icons.Outlined.Tune, "Parameters") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Change model") }, onClick = { menu = false; showModelPicker = true })
                            DropdownMenuItem(text = { Text("Suggest a model") }, onClick = { menu = false; showRouter = true })
                            DropdownMenuItem(text = { Text("Persona") }, onClick = { menu = false; showPersonas = true })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; showRename = true })
                            DropdownMenuItem(text = { Text("Export as Markdown") }, onClick = { menu = false; exportMarkdown = true; exportLauncher.launch("chat.md") })
                            DropdownMenuItem(text = { Text("Export as JSON") }, onClick = { menu = false; exportMarkdown = false; exportLauncher.launch("chat.json") })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (providers.none { it.enabled }) {
                ErrorBanner("No provider is set up yet.", Modifier.padding(12.dp), actionLabel = "Add provider", onAction = onOpenProviders)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Ask anything", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        conversation?.systemPrompt?.let { Text("System prompt is set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                val lastAssistantId = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
                items(messages, key = { it.id }) { m ->
                    MessageItem(
                        m = m,
                        renderMarkdown = settings.renderMarkdown,
                        highlight = settings.codeHighlighting,
                        isLastAssistant = m.id == lastAssistantId,
                        generating = ui?.generating == true,
                        onCopy = { Platform.copy(context, m.content) },
                        onShare = { Platform.shareText(context, m.content) },
                        onEdit = { editing = m },
                        onDelete = { viewModel.deleteMessage(m.id) },
                        onRegenerate = { viewModel.regenerate() },
                        onInspect = { inspecting = it },
                    )
                }
                ui?.let { u ->
                    if (u.generating && (u.omittedMessages > 0 || u.usedSummary || u.liveNote != null)) item {
                        Text(
                            listOfNotNull(
                                u.liveNote,
                                if (u.omittedMessages > 0) "${u.omittedMessages} older messages left out to fit the context window" else null,
                                if (u.usedSummary) "Using a summary of earlier messages" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Composer(
                text = draft,
                onTextChange = { viewModel.draft.value = it },
                attachments = pending,
                importing = importing,
                generating = ui?.generating == true,
                onAttach = { pickFile.launch(arrayOf("image/*", "text/*", "application/pdf", "application/json", "application/vnd.openxmlformats-officedocument.*", "*/*")) },
                onRemoveAttachment = viewModel::removeAttachment,
                onInspect = { inspecting = it },
                onPrompts = { showPrompts = true },
                onSend = viewModel::send,
                onStop = viewModel::stop,
            )
        }
    }

    if (showModelPicker) ModelPickerSheet(
        providers = providers, models = models,
        selected = conversation?.let { c -> c.providerId?.let { p -> c.modelId?.let { ModelKey(p, it) } } },
        onSelect = { viewModel.selectModel(it); showModelPicker = false },
        onDismiss = { showModelPicker = false },
    )
    if (showParams) ParamsSheet(
        params = conversation?.params, systemPrompt = conversation?.systemPrompt.orEmpty(), supported = supported,
        onApply = { p, sys -> viewModel.setParams(p); viewModel.setSystemPrompt(sys); showParams = false },
        onDismiss = { showParams = false },
    )
    if (showPersonas) PersonaSheet(personas, conversation?.personaId, onPick = { viewModel.applyPersona(it); showPersonas = false }, onDismiss = { showPersonas = false })
    if (showPrompts) PromptSheet(prompts, onPick = { viewModel.draft.value = if (draft.isBlank()) it else draft + "\n\n" + it; showPrompts = false }, onDismiss = { showPrompts = false })
    if (showRouter) RouterSheet(
        mode = settings.routingMode,
        suggest = { viewModel.suggest(it) },
        onPick = { viewModel.selectModel(it.model.key); showRouter = false },
        onPinCurrent = viewModel::pinCurrentModelForTask,
        onDismiss = { showRouter = false },
    )
    if (showRename) RenameDialog(conversation?.title.orEmpty(), onDismiss = { showRename = false }, onConfirm = viewModel::rename)
    editing?.let { m -> EditMessageDialog(m.content, onDismiss = { editing = null }) { viewModel.editAndResend(m.id, it) } }
    inspecting?.let { a -> AttachmentInspector(a) { inspecting = null } }
    approval?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.approvals.answer(false) },
            title = { Text("Allow tool \"${req.call.name}\"?") },
            text = {
                Column {
                    Text(req.description, style = MaterialTheme.typography.bodyMedium)
                    Text("Arguments", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(req.call.argumentsJson.take(2_000), style = MonoStyle)
                    Text("Tools run locally on this device and never see your API keys.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.approvals.answer(true) }) { Text("Allow once") } },
            dismissButton = { TextButton(onClick = { viewModel.approvals.answer(false) }) { Text("Deny") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    m: ChatMessage,
    renderMarkdown: Boolean,
    highlight: Boolean,
    isLastAssistant: Boolean,
    generating: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onInspect: (Attachment) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var showReasoning by rememberSaveable(m.id) { mutableStateOf(false) }
    val isUser = m.role == MessageRole.USER
    when (m.role) {
        MessageRole.SYSTEM -> return
        MessageRole.TOOL -> {
            var open by rememberSaveable(m.id) { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
                Column(Modifier.padding(10.dp)) {
                    Text("Tool result", style = MaterialTheme.typography.labelMedium)
                    if (open) Text(m.content, style = MonoStyle, modifier = Modifier.padding(top = 4.dp))
                }
            }
            return
        }
        else -> Unit
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Box {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .then(if (isUser) Modifier.widthIn(max = 340.dp) else Modifier.fillMaxWidth())
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Message actions"),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (m.attachments.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            m.attachments.forEach { a -> AttachmentChip(a, onClick = { onInspect(a) }) }
                        }
                    }
                    val reasoning = m.reasoning
                    if (!reasoning.isNullOrBlank()) {
                        Text(
                            if (showReasoning) "Hide reasoning" else "Show reasoning",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showReasoning = !showReasoning }.padding(bottom = 4.dp),
                        )
                        AnimatedVisibility(showReasoning) {
                            Text(reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                    when {
                        m.content.isEmpty() && m.status == MessageStatus.STREAMING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        isUser -> SelectionContainer { Text(m.content, style = MaterialTheme.typography.bodyLarge) }
                        else -> MarkdownText(m.content, renderMarkdown = renderMarkdown, highlightCode = highlight)
                    }
                    val calls = ToolCallCodec.decode(m.toolCallsJson)
                    if (calls.isNotEmpty()) Text("Called: " + calls.joinToString { it.name }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    if (m.status == MessageStatus.ERROR) {
                        Text(m.errorMessage ?: "Request failed.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (m.status == MessageStatus.CANCELLED) Text("Stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Copy") }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                if (isUser && !generating) DropdownMenuItem(text = { Text("Edit & resend") }, onClick = { menu = false; onEdit() })
                if (!isUser && isLastAssistant && !generating) DropdownMenuItem(text = { Text("Regenerate") }, onClick = { menu = false; onRegenerate() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() }, enabled = !generating || m.status != MessageStatus.STREAMING)
            }
        }
        if (!isUser && m.status != MessageStatus.STREAMING) {
            Row(Modifier.padding(top = 4.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                m.modelId?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) }
                m.usage?.let { u ->
                    MetaPill("${Format.tokens(u.inputTokens)} in · ${Format.tokens(u.outputTokens)} out" + if (u.providerReported) "" else " (est.)")
                }
                m.latencyMs?.let { MetaPill(Format.latency(it)) }
                if (isLastAssistant && !generating) TextButton(onClick = onRegenerate, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Regenerate") }
            }
        }
    }
}

@Composable
private fun AttachmentChip(a: Attachment, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    InputChip(
        selected = false, onClick = onClick,
        label = { Text(a.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
        leadingIcon = { Icon(if (a.type == AttachmentType.IMAGE) Icons.Outlined.Image else Icons.Outlined.Description, null, Modifier.size(18.dp)) },
        trailingIcon = if (onRemove == null) null else {
            { IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) { Icon(Icons.Outlined.Close, "Remove ${a.displayName}", Modifier.size(16.dp)) } }
        },
    )
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<Attachment>,
    importing: Boolean,
    generating: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    onInspect: (Attachment) -> Unit,
    onPrompts: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            if (attachments.isNotEmpty() || importing) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    items(attachments, key = { it.id }) { a -> AttachmentChip(a, onClick = { onInspect(a) }, onRemove = { onRemoveAttachment(a) }) }
                    if (importing) item { CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach) { Icon(Icons.Outlined.AttachFile, "Attach file") }
                IconButton(onClick = onPrompts) { Icon(Icons.Outlined.AutoAwesome, "Insert prompt template") }
                OutlinedTextField(
                    value = text, onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 200.dp),
                    placeholder = { Text("Message") },
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(6.dp))
                if (generating) {
                    FilledIconButton(onClick = onStop) { Icon(Icons.Outlined.Stop, "Stop generating") }
                } else {
                    FilledIconButton(onClick = onSend, enabled = text.isNotBlank() || attachments.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                }
            }
        }
    }
}

@Composable
private fun EditMessageDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = {
            Column {
                Text("Messages after this one are removed and a new answer is generated.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value); onDismiss() }, enabled = value.isNotBlank()) { Text("Resend") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AttachmentInspector(a: Attachment, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.displayName) },
        text = {
            val extracted = a.extractedText
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${a.mimeType} · ${a.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                when {
                    a.type == AttachmentType.IMAGE -> Text("The image (resized to at most 1568 px) is sent to models that accept image input.", modifier = Modifier.padding(top = 8.dp))
                    extracted.isNullOrBlank() -> Text("No text could be extracted from this file.", modifier = Modifier.padding(top = 8.dp))
                    else -> {
                        Text("This extracted text is what will be sent to the model:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        SelectionContainer { Text(extracted.take(20_000), style = MonoStyle, modifier = Modifier.padding(top = 4.dp)) }
                        if (extracted.length > 20_000) Text("… (${extracted.length} characters in total)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaSheet(personas: List<com.omniai.core.model.Persona>, currentId: String?, onPick: (com.omniai.core.model.Persona?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Text("Persona", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            item {
                ListItem(headlineContent = { Text("No persona") }, supportingContent = { Text("Use the default system prompt") },
                    modifier = Modifier.clickable { onPick(null) }, trailingContent = { if (currentId == null) Text("✓") })
            }
            items(personas, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text("${p.avatarEmoji} ${p.name}") },
                    supportingContent = { Text(p.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { if (p.id == currentId) Text("✓") },
                    modifier = Modifier.clickable { onPick(p) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptSheet(prompts: List<com.omniai.core.model.PromptTemplate>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filling by remember { mutableStateOf<com.omniai.core.model.PromptTemplate?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val current = filling
        if (current != null) {
            PromptVariableForm(current, onCancel = { filling = null }, onDone = onPick)
        } else LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Text("Prompt library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            if (prompts.isEmpty()) item { Text("No prompts saved yet.", Modifier.padding(16.dp)) }
            items(prompts.sortedByDescending { it.isFavorite }, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text((if (p.isFavorite) "★ " else "") + p.title) },
                    supportingContent = { Text(p.content, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    overlineContent = { Text(p.category.label) },
                    modifier = Modifier.clickable {
                        if (com.omniai.core.common.PromptTemplateEngine.variables(p.content).isEmpty()) onPick(p.content) else filling = p
                    },
                )
            }
        }
    }
}

/** Shared form that asks for each {{variable}} in a prompt template and returns the filled text. */
@Composable
fun PromptVariableForm(prompt: com.omniai.core.model.PromptTemplate, onCancel: () -> Unit, onDone: (String) -> Unit) {
    val vars = remember(prompt.id) { com.omniai.core.common.PromptTemplateEngine.variables(prompt.content) }
    val values = remember(prompt.id) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(prompt.title, style = MaterialTheme.typography.titleLarge)
        vars.forEach { v -> OutlinedTextField(values[v].orEmpty(), { values[v] = it }, label = { Text(v) }, modifier = Modifier.fillMaxWidth()) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Back") }
            TextButton(onClick = { onDone(com.omniai.core.common.PromptTemplateEngine.fill(prompt.content, values.toMap())) }) { Text("Insert") }
        }
        Spacer(Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouterSheet(
    mode: RoutingMode,
    suggest: suspend (RoutingTask) -> List<RouteCandidate>,
    onPick: (RouteCandidate) -> Unit,
    onPinCurrent: (RoutingTask) -> Unit,
    onDismiss: () -> Unit,
) {
    var task by rememberSaveable { mutableStateOf(RoutingTask.GENERAL) }
    var results by remember { mutableStateOf<List<RouteCandidate>?>(null) }
    LaunchedEffect(task) { results = null; results = suggest(task) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Suggest a model", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Mode: ${if (mode == RoutingMode.MANUAL) RoutingMode.RECOMMENDED.label else mode.label}. Ranked on this device from your model metadata, tags, favorites, pricing and recent provider health. Your prompt is not sent anywhere to decide.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RoutingTask.entries) { t -> androidx.compose.material3.FilterChip(selected = t == task, onClick = { task = t }, label = { Text(t.label) }) }
                }
            }
            val r = results
            when {
                r == null -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                r.isEmpty() -> item {
                    Text(
                        if (mode == RoutingMode.USER_DEFINED) "No rule for this task yet. Use \"Add current model to this task\" below."
                        else "No matching models. Refresh model lists in Providers, or tag models with categories in the model browser.",
                        Modifier.padding(16.dp),
                    )
                }
                else -> items(r, key = { it.model.providerId + it.model.modelId }) { c ->
                    ListItem(
                        headlineContent = { Text(c.model.displayName) },
                        overlineContent = { Text(c.providerName) },
                        supportingContent = { Text(c.reasons.joinToString(" · "), maxLines = 3) },
                        modifier = Modifier.clickable { onPick(c) },
                    )
                }
            }
            item {
                AssistChip(onClick = { onPinCurrent(task) }, label = { Text("Add current model to this task's rule") }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

