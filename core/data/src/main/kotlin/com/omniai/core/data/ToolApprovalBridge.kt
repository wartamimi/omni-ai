package com.omniai.core.data

import com.omniai.core.domain.tools.ToolApprover
import com.omniai.core.model.ToolCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class PendingToolApproval(val call: ToolCall, val description: String, internal val answer: CompletableDeferred<Boolean>)

/**
 * Connects the engine's tool approval requests to the UI. A request waits for the user's answer
 * (denied after 2 minutes without an answer, or when no screen is showing the dialog).
 */
@Singleton
class ToolApprovalBridge @Inject constructor() : ToolApprover {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<PendingToolApproval?>(null)
    val pending: StateFlow<PendingToolApproval?> = _pending.asStateFlow()

    override suspend fun approve(call: ToolCall, toolDescription: String): Boolean = mutex.withLock {
        val request = PendingToolApproval(call, toolDescription, CompletableDeferred())
        _pending.value = request
        try {
            withTimeoutOrNull(120_000) { request.answer.await() } ?: false
        } finally {
            _pending.value = null
        }
    }

    fun answer(approved: Boolean) {
        _pending.value?.answer?.complete(approved)
    }
}
