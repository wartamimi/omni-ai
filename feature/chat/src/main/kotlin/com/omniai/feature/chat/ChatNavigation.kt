package com.omniai.feature.chat

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object ConversationsRoute
@Serializable data class ChatRoute(val conversationId: String, val initialText: String = "")

fun NavGraphBuilder.chatGraph(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenProviders: () -> Unit,
) {
    composable<ConversationsRoute> { ConversationListScreen(onOpenConversation = onOpenConversation) }
    composable<ChatRoute> { ChatScreen(onBack = onBack, onOpenProviders = onOpenProviders) }
}
