package com.omniai.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.feature.chat.ChatRoute
import com.omniai.feature.chat.ConversationsRoute
import com.omniai.feature.chat.chatGraph
import com.omniai.feature.compare.CompareRoute
import com.omniai.feature.compare.compareGraph
import com.omniai.feature.home.HomeActions
import com.omniai.feature.home.HomeRoute
import com.omniai.feature.home.homeGraph
import com.omniai.feature.models.ModelsRoute
import com.omniai.feature.models.modelsGraph
import com.omniai.feature.personas.PersonasRoute
import com.omniai.feature.personas.personasGraph
import com.omniai.feature.prompts.PromptsRoute
import com.omniai.feature.prompts.promptsGraph
import com.omniai.feature.providers.ProvidersRoute
import com.omniai.feature.providers.providersGraph
import com.omniai.feature.settings.ApiLogRoute
import com.omniai.feature.settings.InfoRoute
import com.omniai.feature.settings.SettingsRoute
import com.omniai.feature.settings.ToolsRoute
import com.omniai.feature.settings.settingsGraph
import com.omniai.feature.usage.UsageRoute
import com.omniai.feature.usage.usageGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.reflect.KClass

private enum class TopLevel(val label: String, val icon: ImageVector, val route: Any, val routeClass: KClass<*>) {
    HOME("Home", Icons.Outlined.Home, HomeRoute, HomeRoute::class),
    CHATS("Chats", Icons.AutoMirrored.Outlined.Chat, ConversationsRoute, ConversationsRoute::class),
    COMPARE("Compare", Icons.Outlined.Compare, CompareRoute, CompareRoute::class),
    PROVIDERS("Providers", Icons.Outlined.Cloud, ProvidersRoute, ProvidersRoute::class),
    SETTINGS("Settings", Icons.Outlined.Settings, SettingsRoute, SettingsRoute::class),
}

/** Small app-level ViewModel so the shell can create a conversation for shared text. */
@HiltViewModel
class AppShellViewModel @Inject constructor(private val engine: ChatEngine) : ViewModel() {
    suspend fun newConversation(): String = engine.createConversation().id
}

@Composable
fun OmniApp(sharedText: StateFlow<String?>, onSharedTextConsumed: () -> Unit, shell: AppShellViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val incoming by sharedText.collectAsStateWithLifecycle()

    LaunchedEffect(incoming) {
        val text = incoming ?: return@LaunchedEffect
        onSharedTextConsumed()
        nav.navigate(ChatRoute(shell.newConversation(), text))
    }

    val isTopLevel = TopLevel.entries.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }
    val layoutType = if (isTopLevel || destination == null) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevel.entries.forEach { t ->
                item(
                    selected = destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true,
                    onClick = { nav.navigateTopLevel(t.route) },
                    icon = { Icon(t.icon, contentDescription = null) },
                    label = { Text(t.label) },
                )
            }
        },
    ) {
        val back: () -> Unit = { nav.popBackStack() }
        val openConversation: (String) -> Unit = { id -> nav.navigate(ChatRoute(id)) }
        NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
            homeGraph(
                HomeActions(
                    openConversation = { id, text -> nav.navigate(ChatRoute(id, text)) },
                    openConversations = { nav.navigateTopLevel(ConversationsRoute) },
                    openProviders = { nav.navigateTopLevel(ProvidersRoute) },
                    openCompare = { nav.navigateTopLevel(CompareRoute) },
                    openModels = { nav.navigate(ModelsRoute()) },
                    openPrompts = { nav.navigate(PromptsRoute) },
                    openPersonas = { nav.navigate(PersonasRoute) },
                    openUsage = { nav.navigate(UsageRoute) },
                ),
            )
            chatGraph(onBack = back, onOpenConversation = openConversation, onOpenProviders = { nav.navigateTopLevel(ProvidersRoute) })
            compareGraph()
            providersGraph(
                onBack = back,
                onOpenEditor = { nav.navigate(it) },
                onOpenModels = { nav.navigate(ModelsRoute(it)) },
            )
            modelsGraph(onBack = back)
            promptsGraph(onBack = back, onStartChat = { id, text -> nav.navigate(ChatRoute(id, text)) })
            personasGraph(onBack = back, onOpenConversation = openConversation)
            usageGraph(onBack = back)
            settingsGraph(
                onBack = back,
                onOpenTools = { nav.navigate(ToolsRoute) },
                onOpenApiLog = { nav.navigate(ApiLogRoute) },
                onOpenDocument = { nav.navigate(InfoRoute(it.name)) },
                onOpenPersonas = { nav.navigate(PersonasRoute) },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
