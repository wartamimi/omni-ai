package com.omniai.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable data object SettingsRoute
@Serializable data object ToolsRoute
@Serializable data object ApiLogRoute
@Serializable data class InfoRoute(val document: String)

fun NavGraphBuilder.settingsGraph(
    onBack: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenApiLog: () -> Unit,
    onOpenDocument: (InfoDocument) -> Unit,
    onOpenPersonas: () -> Unit,
) {
    composable<SettingsRoute> { SettingsScreen(onOpenTools, onOpenApiLog, onOpenDocument, onOpenPersonas) }
    composable<ToolsRoute> { ToolsScreen(onBack) }
    composable<ApiLogRoute> { ApiLogScreen(onBack) }
    composable<InfoRoute> { entry ->
        val doc = runCatching { InfoDocument.valueOf(entry.toRoute<InfoRoute>().document) }.getOrDefault(InfoDocument.PRIVACY)
        InfoDocumentScreen(doc, onBack)
    }
}
