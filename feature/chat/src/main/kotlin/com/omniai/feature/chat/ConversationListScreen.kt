package com.omniai.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.EmptyState
import com.omniai.core.designsystem.component.SectionHeader
import com.omniai.core.model.Conversation
import com.omniai.core.ui.Format
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val list by viewModel.conversationList.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hits by viewModel.searchResults.collectAsStateWithLifecycle()
    val archived by viewModel.showArchived.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    var exportTarget by remember { mutableStateOf<Pair<Conversation, Boolean>?>(null) }

    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val target = exportTarget
        if (uri != null && target != null) scope.launch {
            val text = if (target.second) viewModel.exportMarkdown(target.first.id) else viewModel.exportJson(target.first.id)
            val ok = text != null && Platform.writeText(context, uri, text)
            snackbar.showSnackbar(if (ok) "Exported." else "Export failed.")
        }
        exportTarget = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Platform.readText(context, uri)?.let(viewModel::importJson)
    }

    BackScaffold(
        title = if (archived) "Archived chats" else "Chats",
        onBack = null,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Outlined.FileUpload, "Import conversation") }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { onOpenConversation(viewModel.newConversation()) } },
                icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("New chat") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { viewModel.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search titles and messages") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { viewModel.query.value = "" }) { Icon(Icons.Outlined.Clear, "Clear search") } },
                    singleLine = true,
                )
                FilterChip(
                    selected = archived, onClick = { viewModel.showArchived.value = !archived },
                    label = { Text("Show archived") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (query.isNotBlank()) {
                if (hits.isEmpty()) item { Text("No matches.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(hits, key = { "hit-" + it.conversation.id }) { hit ->
                    ListItem(
                        headlineContent = { Text(hit.conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(hit.snippet ?: "Title match", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.combinedClickable(onClick = { onOpenConversation(hit.conversation.id) }),
                    )
                }
                return@LazyColumn
            }
            val all = list
            if (all == null) return@LazyColumn
            val visible = all.filter { it.isArchived == archived }
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.ChatBubbleOutline,
                        if (archived) "No archived chats" else "No conversations yet",
                        if (archived) "Archived chats appear here." else "Start a new chat. Messages go directly from this device to the provider you choose.",
                    )
                }
                return@LazyColumn
            }
            val pinned = visible.filter { it.isPinned }
            if (pinned.isNotEmpty()) {
                item { SectionHeader("Pinned", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                items(pinned, key = { "p-" + it.id }) { c ->
                    ConversationRow(c, onOpenConversation, viewModel, { renaming = c }, { deleting = c }) { md -> exportTarget = c to md; exportLauncher.launch(fileName(c, md)) }
                }
            }
            visible.filterNot { it.isPinned }.groupBy { Format.dateGroup(it.updatedAt) }.forEach { (group, items) ->
                item(key = "g-$group") { SectionHeader(group, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                items(items, key = { it.id }) { c ->
                    ConversationRow(c, onOpenConversation, viewModel, { renaming = c }, { deleting = c }) { md -> exportTarget = c to md; exportLauncher.launch(fileName(c, md)) }
                }
            }
        }
    }

    renaming?.let { c -> RenameDialog(c.title, onDismiss = { renaming = null }) { viewModel.rename(c, it) } }
    deleting?.let { c ->
        ConfirmDialog("Delete \"${c.title}\"?", "All messages in this conversation are permanently deleted from this device.", "Delete", { viewModel.delete(c) }, { deleting = null })
    }
}

private fun fileName(c: Conversation, markdown: Boolean): String =
    c.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "conversation" }.take(40) + if (markdown) ".md" else ".json"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    c: Conversation,
    onOpen: (String) -> Unit,
    viewModel: ConversationListViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: (markdown: Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(listOfNotNull(c.modelId, Format.time(c.updatedAt)).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = if (c.isPinned) ({ Icon(Icons.Outlined.PushPin, "Pinned") }) else null,
            modifier = Modifier.combinedClickable(onClick = { onOpen(c.id) }, onLongClick = { menu = true }, onLongClickLabel = "Conversation actions"),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (c.isPinned) "Unpin" else "Pin") }, onClick = { menu = false; viewModel.togglePin(c) })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; viewModel.duplicate(c) })
            DropdownMenuItem(text = { Text(if (c.isArchived) "Unarchive" else "Archive") }, onClick = { menu = false; viewModel.toggleArchive(c) })
            DropdownMenuItem(text = { Text("Export as Markdown") }, onClick = { menu = false; onExport(true) })
            DropdownMenuItem(text = { Text("Export as JSON") }, onClick = { menu = false; onExport(false) })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
internal fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Title") }) },
        confirmButton = { TextButton(onClick = { onConfirm(value); onDismiss() }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
