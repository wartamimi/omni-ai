package com.omniai.core.domain.tools

import com.omniai.core.model.ToolSpec

/**
 * A locally executed tool the model may call. Tools never run shell commands or code from model
 * output; arguments are validated JSON and every tool is pure Kotlin.
 */
interface LocalTool {
    val name: String
    val description: String
    val parametersSchema: String
    /** True when the tool has side effects or reaches the network (requires approval by default). */
    val sensitive: Boolean get() = false

    /** Returns a textual result or throws [ToolArgumentException]. */
    suspend fun execute(argumentsJson: String): String

    fun spec() = ToolSpec(name, description, parametersSchema)
}

class ToolArgumentException(message: String) : IllegalArgumentException(message)
