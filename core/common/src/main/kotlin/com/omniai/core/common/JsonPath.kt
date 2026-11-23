package com.omniai.core.common

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal JSON path for response mapping: `a.b[0].c`, `choices[0].delta.content`.
 * No wildcards or filters by design (predictable, no code execution).
 */
object JsonPath {
    private val segment = Regex("([^.\\[\\]]+)|\\[(\\d+)]")

    fun read(root: JsonElement?, path: String): JsonElement? {
        if (root == null) return null
        if (path.isBlank() || path == "$") return root
        var current: JsonElement? = root
        val clean = path.removePrefix("$").removePrefix(".")
        for (m in segment.findAll(clean)) {
            val key = m.groupValues[1]
            val index = m.groupValues[2]
            current = when {
                key.isNotEmpty() -> (current as? JsonObject)?.get(key)
                index.isNotEmpty() -> (current as? JsonArray)?.getOrNull(index.toInt())
                else -> null
            }
            if (current == null || current is JsonNull) return null
        }
        return current
    }

    fun readString(root: JsonElement?, path: String): String? =
        (read(root, path) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    fun readInt(root: JsonElement?, path: String): Int? = readString(root, path)?.toDoubleOrNull()?.toInt()

    fun isValid(path: String): Boolean {
        val clean = path.removePrefix("$").removePrefix(".")
        if (clean.isBlank()) return false
        val rebuilt = segment.findAll(clean).joinToString("") { it.value }
        return rebuilt.replace(".", "") == clean.replace(".", "")
    }
}
