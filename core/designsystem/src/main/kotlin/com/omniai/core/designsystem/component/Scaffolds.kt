package com.omniai.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import com.omniai.core.designsystem.theme.LocalGlass

/** Standard scaffold with a top bar; shows a back arrow when [onBack] is non-null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = glassTopBarColors(),
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/** Transparent app bar that turns into frosted glass when content scrolls underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = LocalGlass.current.bar,
)
