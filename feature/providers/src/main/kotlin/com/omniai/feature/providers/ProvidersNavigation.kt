package com.omniai.feature.providers

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object ProvidersRoute

/** Exactly one of the arguments identifies what to edit: an existing provider, a catalog entry, or a custom template. */
@Serializable data class ProviderEditorRoute(
    val providerId: String = "",
    val definitionId: String = "",
    val templateIndex: Int = -1,
)

fun NavGraphBuilder.providersGraph(
    onBack: () -> Unit,
    onOpenEditor: (ProviderEditorRoute) -> Unit,
    onOpenModels: (String) -> Unit,
) {
    composable<ProvidersRoute> { ProvidersScreen(onOpenEditor = onOpenEditor, onOpenModels = onOpenModels) }
    composable<ProviderEditorRoute> { ProviderEditorScreen(onBack = onBack, onOpenModels = onOpenModels) }
}
