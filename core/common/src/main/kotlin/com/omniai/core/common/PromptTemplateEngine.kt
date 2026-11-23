package com.omniai.core.common

/** `{{variable}}` substitution for prompt templates. */
object PromptTemplateEngine {
    private val variable = Regex("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}")

    fun variables(template: String): List<String> =
        variable.findAll(template).map { it.groupValues[1] }.distinct().toList()

    /** Fills known variables; unknown ones are left untouched so the user can see what's missing. */
    fun fill(template: String, values: Map<String, String>): String =
        variable.replace(template) { m -> values[m.groupValues[1]] ?: m.value }

    fun missing(template: String, values: Map<String, String>): List<String> =
        variables(template).filter { values[it].isNullOrBlank() }
}
