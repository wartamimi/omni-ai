package com.omniai.core.domain.chat

import com.omniai.core.model.ToolCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stores assistant tool calls on a message as JSON. */
object ToolCallCodec {
    @Serializable
    private data class Stored(val id: String, val name: String, val arguments: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(calls: List<ToolCall>): String? =
        if (calls.isEmpty()) null else json.encodeToString(calls.map { Stored(it.id, it.name, it.argumentsJson) })

    fun decode(raw: String?): List<ToolCall> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Stored>>(raw).map { ToolCall(it.id, it.name, it.arguments) } }.getOrDefault(emptyList())
    }
}
