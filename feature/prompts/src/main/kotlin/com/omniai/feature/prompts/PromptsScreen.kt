package com.omniai.feature.prompts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.omniai.core.common.PromptTemplateEngine
import com.omniai.core.designsystem.component.BackScaffold
import com.omniai.core.designsystem.component.ConfirmDialog
import com.omniai.core.designsystem.component.EmptyState
import com.omniai.core.model.PromptCategory
import com.omniai.core.model.PromptTemplate
import com.omniai.core.ui.Platform
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object PromptsRoute

fun NavGraphBuilder.promptsGraph(onBack: () -> Unit, onStartChat: (conversationId: String, text: String) -> Unit) {
    composable<PromptsRoute> { PromptsScreen(onBack, onStartChat) }
}

private class EditTarget(val prompt: PromptTemplate?)

@Composable
fun PromptsScreen(onBack: () -> Unit, onStartChat: (String, String) -> Unit, viewModel: PromptsViewModel = hiltViewModel()) {
    val list by viewModel.visible.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var using by remember { mutableStateOf<PromptTemplate?>(null) }
    var deleting by remember { mutableStateOf<PromptTemplate?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { snackbar.showSnackbar(if (Platform.writeText(context, uri, viewModel.exportJson())) "Exported." else "Export failed.") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Platform.readText(context, uri)?.let(viewModel::importJson)
    }

    BackScaffold(
        title = "Prompt library",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Icon(Icons.Outlined.FileUpload, "Import prompts") }
            IconButton(onClick = { exportLauncher.launch("omniai-prompts.json") }) { Icon(Icons.Outlined.FileDownload, "Export prompts") }
        },
        floatingActionButton = { FloatingActionButton(onClick = { editing = EditTarget(null) }) { Icon(Icons.Outlined.Add, "New prompt") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    query, { viewModel.query.value = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search prompts") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(category == null, { viewModel.category.value = null }, label = { Text("All") }) }
                    items(PromptCategory.entries) { c -> FilterChip(category == c, { viewModel.category.value = if (category == c) null else c }, label = { Text(c.label) }) }
                }
            }
            val prompts = list ?: return@LazyColumn
            if (prompts.isEmpty()) item { EmptyState(Icons.Outlined.TextSnippet, "No prompts", "Save prompts you reuse. Use {{variable}} for parts you fill in each time.") }
            items(prompts, key = { it.id }) { p ->
                ListItem(
                    overlineContent = { Text(p.category.label) },
                    headlineContent = { Text(p.title) },
                    supportingContent = { Text(p.content, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleFavorite(p) }) {
                            Icon(if (p.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline, if (p.isFavorite) "Unfavorite" else "Favorite")
                        }
                    },
                    modifier = Modifier.clickable { using = p },
                )
            }
        }
    }

    editing?.let { t ->
        PromptEditor(t.prompt, onDismiss = { editing = null }, onDelete = t.prompt?.let { p -> { deleting = p; editing = null } }) { title, content, cat, fav ->
            viewModel.save(t.prompt?.id, title, content, cat, fav); editing = null
        }
    }
    using?.let { p ->
        UsePromptSheet(
            p,
            onDismiss = { using = null },
            onEdit = { using = null; editing = EditTarget(p) },
            onCopy = { Platform.copy(context, it) },
            onStartChat = { text -> using = null; scope.launch { onStartChat(viewModel.startChat(), text) } },
        )
    }
    deleting?.let { p -> ConfirmDialog("Delete \"${p.title}\"?", "This prompt is removed from your library.", "Delete", { viewModel.delete(p) }, { deleting = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsePromptSheet(p: PromptTemplate, onDismiss: () -> Unit, onEdit: () -> Unit, onCopy: (String) -> Unit, onStartChat: (String) -> Unit) {
    val vars = remember(p.id) { PromptTemplateEngine.variables(p.content) }
    val values = remember(p.id) { mutableStateMapOf<String, String>() }
    val filled = PromptTemplateEngine.fill(p.content, values.toMap())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleLarge)
            vars.forEach { v -> OutlinedTextField(values[v].orEmpty(), { values[v] = it }, label = { Text(v) }, modifier = Modifier.fillMaxWidth()) }
            Text("Preview", style = MaterialTheme.typography.labelLarge)
            Text(filled, style = MaterialTheme.typography.bodyMedium)
            val missing = PromptTemplateEngine.missing(p.content, values.toMap())
            if (missing.isNotEmpty()) Text("Not filled: ${missing.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = { onCopy(filled) }) { Text("Copy") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onStartChat(filled) }) { Text("Use in new chat") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptEditor(
    prompt: PromptTemplate?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, String, PromptCategory, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(prompt?.title.orEmpty()) }
    var content by remember { mutableStateOf(prompt?.content.orEmpty()) }
    var cat by remember { mutableStateOf(prompt?.category ?: PromptCategory.GENERAL) }
    var fav by remember { mutableStateOf(prompt?.isFavorite ?: false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (prompt == null) "New prompt" else "Edit prompt", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                content, { content = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                supportingText = { Text("Variables: ${PromptTemplateEngine.variables(content).joinToString().ifBlank { "none (write {{name}} to add one)" }}") },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PromptCategory.entries) { c -> FilterChip(cat == c, { cat = c }, label = { Text(c.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fav, { fav = it }); Text("Favorite") }
            Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(title, content, cat, fav) }, enabled = title.isNotBlank() && content.isNotBlank()) { Text("Save") }
            }
        }
    }
}
