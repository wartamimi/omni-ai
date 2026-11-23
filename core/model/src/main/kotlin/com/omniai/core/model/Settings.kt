package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class AccentColor(val argb: Long, val label: String) {
    INDIGO(0xFF7C83FF, "Indigo"),
    TEAL(0xFF2DD4BF, "Teal"),
    EMERALD(0xFF34D399, "Emerald"),
    AMBER(0xFFFBBF24, "Amber"),
    ROSE(0xFFFB7185, "Rose"),
    SKY(0xFF38BDF8, "Sky"),
    VIOLET(0xFFA78BFA, "Violet"),
}

@Serializable
enum class ContextStrategy(val label: String, val description: String) {
    FULL("Keep everything", "Send the full history. Fails with a clear error if the model's context is exceeded."),
    SLIDING_WINDOW("Recent messages", "Send the most recent messages that fit; you'll see how many were left out."),
    SUMMARIZE("Summarize older", "Older messages are summarized by the selected model (costs an extra request)."),
}

@Serializable
enum class RoutingMode(val label: String) {
    MANUAL("Manual"),
    RECOMMENDED("Recommended"),
    COST_OPTIMIZED("Cost-optimized"),
    PERFORMANCE("Performance-oriented"),
    USER_DEFINED("User-defined rules"),
}

@Serializable
enum class DashboardCard(val label: String) {
    QUICK_CHAT("Quick chat"),
    DEFAULTS("Default provider & model"),
    PROVIDERS("Connected providers"),
    FAVORITE_MODELS("Favorite models"),
    RECENT_CONVERSATIONS("Recent conversations"),
    USAGE("Token usage & cost"),
    QUICK_ACTIONS("Quick actions"),
}

@Serializable
data class DashboardLayout(
    val order: List<DashboardCard> = DashboardCard.entries.toList(),
    val hidden: Set<DashboardCard> = emptySet(),
) {
    /** Visible cards in user order; cards added in later versions are appended. */
    fun visibleCards(): List<DashboardCard> =
        (order + DashboardCard.entries.filterNot { it in order }).filterNot { it in hidden }
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val accent: AccentColor = AccentColor.INDIGO,
    val textScale: Float = 1.0f,
    val defaultProviderId: String? = null,
    val defaultModelId: String? = null,
    val defaultPersonaId: String? = null,
    val streaming: Boolean = true,
    val renderMarkdown: Boolean = true,
    val codeHighlighting: Boolean = true,
    val contextStrategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    val maxContextMessages: Int = 40,
    val defaultSystemPrompt: String = "",
    val requestTimeoutSeconds: Int = 120,
    val maxRetries: Int = 2,
    val biometricLock: Boolean = false,
    val compareLimit: Int = 6,
    val currency: CurrencySettings = CurrencySettings(),
    val routingMode: RoutingMode = RoutingMode.MANUAL,
    /** User-defined routing rules: task name -> ordered "providerId::modelId" keys. */
    val routingRules: Map<String, List<String>> = emptyMap(),
    val debugMode: Boolean = false,
    val apiLogging: Boolean = false,
    val allowLocalNetworkEndpoints: Boolean = false,
    val toolsEnabled: Boolean = false,
    val maxToolRounds: Int = 5,
    val allowCleartextLocalEndpoints: Boolean = false,
    val dashboard: DashboardLayout = DashboardLayout(),
    val onboardingCompleted: Boolean = false,
)
