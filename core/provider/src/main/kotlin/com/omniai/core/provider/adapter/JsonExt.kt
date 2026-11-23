package com.omniai.core.provider.adapter

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
internal fun JsonElement?.int(): Int? = str()?.toDoubleOrNull()?.toInt()
internal fun JsonElement?.dbl(): Double? = str()?.toDoubleOrNull()
internal fun JsonElement?.bool(): Boolean? = str()?.toBooleanStrictOrNull()
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

internal fun JsonObjectBuilder.putIfNotNull(key: String, value: Number?) { if (value != null) put(key, value) }
internal fun JsonObjectBuilder.putIfNotNull(key: String, value: String?) { if (value != null) put(key, value) }
