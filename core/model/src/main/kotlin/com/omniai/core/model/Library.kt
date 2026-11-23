package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PromptCategory(val label: String) {
    GENERAL("General"), CODING("Coding"), BUSINESS("Business"), MARKETING("Marketing"),
    EDUCATION("Education"), TRANSLATION("Translation"), WRITING("Writing"), RESEARCH("Research"),
    IMAGE_GENERATION("Image Generation"), PRODUCTIVITY("Productivity"), DEVELOPMENT("Development"),
}

@Serializable
data class PromptTemplate(
    val id: String,
    val title: String,
    val content: String,
    val category: PromptCategory,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val avatarEmoji: String,
    val systemPrompt: String,
    val defaultProviderId: String?,
    val defaultModelId: String?,
    val temperature: Double?,
    val topP: Double?,
    val maxOutputTokens: Int?,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val params: GenerationParams
        get() = GenerationParams(temperature = temperature, topP = topP, maxOutputTokens = maxOutputTokens)
}
