package com.omniai.core.domain.tools

import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.domain.repository.ToolRepository
import com.omniai.core.model.ToolCall
import com.omniai.core.model.ToolExecutionLog
import com.omniai.core.model.ToolPermission

/** UI hook: asks the user whether a tool call may run. */
fun interface ToolApprover {
    suspend fun approve(call: ToolCall, toolDescription: String): Boolean
}

data class ToolResult(val callId: String, val toolName: String, val output: String, val success: Boolean)

/** Runs model-requested tools under the user's permission settings and logs every execution. */
class ToolExecutor(
    private val tools: List<LocalTool>,
    private val repository: ToolRepository,
    private val clock: Clock = Clock.System,
) {
    private val byName = tools.associateBy { it.name }

    fun available(): List<LocalTool> = tools

    suspend fun enabledTools(): List<LocalTool> = tools.filter { repository.permission(it.name) != ToolPermission.DENY }

    suspend fun execute(call: ToolCall, conversationId: String?, approver: ToolApprover): ToolResult {
        val tool = byName[call.name]
            ?: return ToolResult(call.id, call.name, "Error: unknown tool '${call.name}'.", false).also { log(it, call, conversationId, approved = false) }
        val approved = when (repository.permission(tool.name)) {
            ToolPermission.DENY -> false
            ToolPermission.ALLOW -> !tool.sensitive || approver.approve(call, tool.description)
            ToolPermission.ASK -> approver.approve(call, tool.description)
        }
        if (!approved) {
            return ToolResult(call.id, call.name, "The user declined to run this tool.", false).also { log(it, call, conversationId, approved = false) }
        }
        val result = try {
            ToolResult(call.id, call.name, tool.execute(call.argumentsJson).take(MAX_OUTPUT), true)
        } catch (e: ToolArgumentException) {
            ToolResult(call.id, call.name, "Error: ${e.message}", false)
        } catch (e: Exception) {
            ToolResult(call.id, call.name, "Error: tool failed (${e::class.java.simpleName}).", false)
        }
        log(result, call, conversationId, approved = true)
        return result
    }

    private suspend fun log(result: ToolResult, call: ToolCall, conversationId: String?, approved: Boolean) {
        repository.log(
            ToolExecutionLog(
                id = Ids.new(), conversationId = conversationId, toolName = call.name,
                argumentsJson = call.argumentsJson.take(2_000), resultPreview = result.output.take(500),
                approved = approved, success = result.success, timestamp = clock.now(),
            ),
        )
    }

    companion object {
        const val MAX_OUTPUT = 20_000
    }
}
