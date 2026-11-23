package com.omniai.core.data.seed

import com.omniai.core.model.Persona
import com.omniai.core.model.PromptCategory
import com.omniai.core.model.PromptTemplate
import com.omniai.core.model.ToolConfig
import com.omniai.core.model.ToolPermission

/** Starter content shown on first launch. Users can edit or delete all of it. */
object DefaultContent {
    private const val T0 = 0L

    fun personas(): List<Persona> = listOf(
        persona("programming", "Programming Assistant", "💻", "Writes, reviews and explains code.",
            "You are a senior software engineer. Give correct, idiomatic code with brief explanations. Point out bugs, edge cases and security issues. Ask for missing requirements instead of guessing.", 0.2),
        persona("business", "Business Consultant", "📊", "Strategy, planning and analysis.",
            "You are a pragmatic business consultant. Structure answers with clear recommendations, assumptions, risks and next steps. Say when data is missing.", 0.5),
        persona("translator", "Professional Translator", "🌐", "Accurate, natural translation.",
            "You are a professional translator. Translate faithfully and naturally, preserve formatting, and keep names and numbers unchanged. If a phrase is ambiguous, give the alternatives.", 0.2),
        persona("research", "Academic Research Assistant", "🎓", "Careful, sourced reasoning.",
            "You are an academic research assistant. Be precise, distinguish established facts from hypotheses, and never invent citations. Say clearly when you are unsure.", 0.3),
        persona("writer", "Creative Writer", "✍️", "Stories, copy and ideas.",
            "You are a creative writer with a vivid but clear style. Adapt tone to the request and offer options when useful.", 0.9),
        persona("support", "Technical Support", "🛠️", "Step-by-step troubleshooting.",
            "You are a patient technical support specialist. Diagnose step by step, ask for the device/OS/version when relevant, and give one action at a time.", 0.3),
        persona("productivity", "Personal Productivity Assistant", "✅", "Plans, lists and summaries.",
            "You are a personal productivity assistant. Turn messy input into clear plans, checklists and summaries. Keep it short and actionable.", 0.4),
    )

    private fun persona(id: String, name: String, emoji: String, description: String, prompt: String, temperature: Double) = Persona(
        id = "builtin-$id", name = name, description = description, avatarEmoji = emoji, systemPrompt = prompt,
        defaultProviderId = null, defaultModelId = null, temperature = temperature, topP = null, maxOutputTokens = null,
        isBuiltIn = true, createdAt = T0, updatedAt = T0,
    )

    fun prompts(): List<PromptTemplate> = listOf(
        prompt("translate", "Translate text", PromptCategory.TRANSLATION,
            "Translate the following text from {{source_language}} to {{target_language}}. Keep the formatting.\n\n{{text}}"),
        prompt("summarize", "Summarize", PromptCategory.PRODUCTIVITY,
            "Summarize the following in {{length}} bullet points, then list any action items.\n\n{{text}}"),
        prompt("code-review", "Code review", PromptCategory.CODING,
            "Review this {{language}} code for bugs, security issues and readability. Suggest concrete fixes.\n\n```\n{{code}}\n```"),
        prompt("explain-code", "Explain code", PromptCategory.DEVELOPMENT,
            "Explain what this code does, step by step, for a {{audience}} audience.\n\n```\n{{code}}\n```"),
        prompt("email", "Professional email", PromptCategory.BUSINESS,
            "Write a {{tone}} email to {{recipient}} about {{topic}}. Keep it under {{word_limit}} words."),
        prompt("marketing", "Product description", PromptCategory.MARKETING,
            "Write a product description for {{product}} aimed at {{audience}}. Highlight: {{features}}."),
        prompt("lesson", "Explain a concept", PromptCategory.EDUCATION,
            "Explain {{concept}} to a {{level}} learner with one example and a short quiz at the end."),
        prompt("essay", "Essay outline", PromptCategory.WRITING,
            "Create an outline for an essay on {{topic}} with a thesis, three main points and a conclusion."),
        prompt("research", "Research brief", PromptCategory.RESEARCH,
            "Give an overview of {{topic}}: key concepts, open questions and what to read next. Mark anything uncertain."),
        prompt("image", "Image prompt", PromptCategory.IMAGE_GENERATION,
            "Write a detailed image-generation prompt for: {{subject}}. Style: {{style}}. Include lighting, composition and colors."),
        prompt("general", "Brainstorm", PromptCategory.GENERAL,
            "Brainstorm {{count}} ideas for {{goal}}. Group them and mark the three most promising."),
    )

    private fun prompt(id: String, title: String, category: PromptCategory, content: String) =
        PromptTemplate("builtin-$id", title, content, category, false, T0, T0)

    fun tools(names: List<Pair<String, String>>): List<ToolConfig> =
        names.map { (name, description) -> ToolConfig(name, name, description, true, ToolPermission.ASK) }
}
