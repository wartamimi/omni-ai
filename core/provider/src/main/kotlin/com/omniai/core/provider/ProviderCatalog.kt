package com.omniai.core.provider

import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.ApiProtocol.ANTHROPIC_MESSAGES
import com.omniai.core.model.ApiProtocol.GEMINI_GENERATE_CONTENT
import com.omniai.core.model.ApiProtocol.OPENAI_CHAT_COMPLETIONS
import com.omniai.core.model.AuthConfig
import com.omniai.core.model.HeaderEntry
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.ProviderCategory.GATEWAY
import com.omniai.core.model.ProviderCategory.OFFICIAL
import com.omniai.core.model.ProviderDefinition
import com.omniai.core.model.SupportLevel
import com.omniai.core.model.SupportLevel.COMPATIBLE
import com.omniai.core.model.SupportLevel.SUPPORTED
import com.omniai.core.model.SupportLevel.UNSUPPORTED

/**
 * Built-in provider templates. Every URL and auth scheme here was checked against the provider's
 * official API documentation on 2026-11-23 (see docs/provider-research-2026-11.md).
 * "Models path = null" means no documented model-list endpoint: users enter model IDs manually.
 */
object ProviderCatalog {

    private fun openAi(
        id: String,
        name: String,
        category: ProviderCategory,
        baseUrl: String,
        docs: String,
        modelsPath: String? = "models",
        level: SupportLevel = SUPPORTED,
        notes: String = "",
        headers: List<HeaderEntry> = emptyList(),
        keyPrefix: String? = null,
    ) = ProviderDefinition(
        id = id, displayName = name, category = category, protocol = OPENAI_CHAT_COMPLETIONS,
        defaultBaseUrl = baseUrl, auth = AuthConfig.Bearer, chatPath = "chat/completions", modelsPath = modelsPath,
        defaultHeaders = headers, docsUrl = docs, supportLevel = level, notes = notes, keyPrefixHint = keyPrefix,
    )

    val definitions: List<ProviderDefinition> = listOf(
        // ---- Official providers ----
        openAi(
            "openai", "OpenAI", OFFICIAL, "https://api.openai.com/v1",
            "https://developers.openai.com/api/reference/overview", keyPrefix = "sk-",
            notes = "Chat Completions by default. Switch the protocol to Responses API in the editor if you prefer it.",
        ),
        ProviderDefinition(
            id = "anthropic", displayName = "Anthropic", category = OFFICIAL, protocol = ANTHROPIC_MESSAGES,
            defaultBaseUrl = "https://api.anthropic.com", auth = AuthConfig.header("x-api-key"),
            chatPath = "v1/messages", modelsPath = "v1/models", apiVersion = AnthropicVersions.DEFAULT,
            docsUrl = "https://platform.claude.com/docs/en/api/overview", supportLevel = SUPPORTED, keyPrefixHint = "sk-ant-",
            notes = "Uses anthropic-version ${AnthropicVersions.DEFAULT}. With ${AnthropicVersions.LATEST}, temperature/top_p/top_k are not sent because that version rejects them.",
        ),
        ProviderDefinition(
            id = "gemini", displayName = "Google Gemini", category = OFFICIAL, protocol = GEMINI_GENERATE_CONTENT,
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta", auth = AuthConfig.header("x-goog-api-key"),
            modelsPath = "models", docsUrl = "https://ai.google.dev/api/models", supportLevel = SUPPORTED, keyPrefixHint = "AIza",
            notes = "Uses generateContent / streamGenerateContent (SSE). Google labels generateContent 'legacy' but fully supported.",
        ),
        openAi("deepseek", "DeepSeek", OFFICIAL, "https://api.deepseek.com", "https://api-docs.deepseek.com", keyPrefix = "sk-"),
        openAi("mistral", "Mistral AI", OFFICIAL, "https://api.mistral.ai/v1", "https://docs.mistral.ai/api/endpoint/chat"),
        openAi("groq", "Groq", OFFICIAL, "https://api.groq.com/openai/v1", "https://console.groq.com/docs/openai", keyPrefix = "gsk_"),
        openAi("xai", "xAI", OFFICIAL, "https://api.x.ai/v1", "https://docs.x.ai/developers/rest-api-reference", keyPrefix = "xai-"),
        openAi(
            "cohere", "Cohere", OFFICIAL, "https://api.cohere.ai/compatibility/v1", "https://docs.cohere.com/docs/compatibility-api",
            modelsPath = null, level = COMPATIBLE,
            notes = "Uses Cohere's OpenAI Compatibility API. A model list endpoint is not documented for it; enter model IDs manually.",
        ),
        openAi(
            "perplexity", "Perplexity", OFFICIAL, "https://api.perplexity.ai/orchestrator/v1",
            "https://docs.perplexity.ai/api-reference/gateway-chat-completions-post", modelsPath = null, level = COMPATIBLE,
            notes = "Sonar Chat Completions ended on 2026-09-27; this uses Perplexity's OpenAI-compatible Orchestrator endpoint. Enter model IDs manually.",
        ),
        openAi(
            "qwen", "Alibaba Qwen (Model Studio)", OFFICIAL, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
            "https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope", modelsPath = null, level = COMPATIBLE,
            notes = "Alibaba recommends your workspace host (https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1). The key must come from the same region. Tools cannot be combined with streaming.",
        ),
        openAi(
            "zhipu", "Zhipu AI (BigModel, China)", OFFICIAL, "https://open.bigmodel.cn/api/paas/v4", "https://docs.bigmodel.cn/cn/api/introduction",
            modelsPath = null, notes = "Enter model IDs manually (e.g. from the GLM docs).",
        ),
        openAi("zai", "Z.ai (GLM, international)", OFFICIAL, "https://api.z.ai/api/paas/v4", "https://docs.z.ai/api-reference/introduction", modelsPath = null),
        openAi("moonshot", "Moonshot AI (Kimi)", OFFICIAL, "https://api.moonshot.ai/v1", "https://platform.kimi.ai/docs/api/overview"),
        openAi(
            "ai21", "AI21 Labs", OFFICIAL, "https://api.ai21.com/studio/v1", "https://docs.ai21.com/reference/jamba-1-6-api-ref",
            modelsPath = null, level = COMPATIBLE, notes = "OpenAI-shaped chat endpoint. Models: jamba-large, jamba-mini. Streaming cannot be combined with tools.",
        ),
        openAi(
            "bedrock", "Amazon Bedrock", OFFICIAL, "https://bedrock-runtime.us-east-1.amazonaws.com/openai/v1",
            "https://docs.aws.amazon.com/bedrock/latest/userguide/inference-chat-completions-mantle.html", modelsPath = null, level = COMPATIBLE,
            notes = "Uses a Bedrock API key (Bearer) on the OpenAI-compatible endpoint. Change us-east-1 to your region. AWS SigV4 credentials are not supported.",
        ),

        // ---- Gateways ----
        openAi(
            "openrouter", "OpenRouter", GATEWAY, "https://openrouter.ai/api/v1", "https://openrouter.ai/docs/api-reference/overview",
            keyPrefix = "sk-or-", headers = listOf(HeaderEntry("X-OpenRouter-Title", "OmniAI")),
            notes = "Model list includes context length and per-token pricing.",
        ),
        openAi("together", "Together AI", GATEWAY, "https://api.together.ai/v1", "https://docs.together.ai/docs/inference/openai-compatibility"),
        openAi(
            "fireworks", "Fireworks AI", GATEWAY, "https://api.fireworks.ai/inference/v1", "https://docs.fireworks.ai/tools-sdks/openai-compatibility",
            modelsPath = null, notes = "No OpenAI-style model list is documented; enter model IDs such as accounts/fireworks/models/<name>.",
        ),
        openAi(
            "nvidia", "NVIDIA NIM", GATEWAY, "https://integrate.api.nvidia.com/v1", "https://docs.api.nvidia.com/nim/reference/llm-apis",
            modelsPath = null, keyPrefix = "nvapi-", notes = "Model list for the hosted API is not documented; enter model IDs manually.",
        ),
        openAi(
            "huggingface", "Hugging Face Inference Providers", GATEWAY, "https://router.huggingface.co/v1",
            "https://huggingface.co/docs/inference-providers/index", keyPrefix = "hf_",
            notes = "Needs a fine-grained token with 'Make calls to Inference Providers'. Model IDs accept :fastest, :cheapest or :<provider> suffixes.",
        ),
        openAi(
            "siliconflow", "SiliconFlow", GATEWAY, "https://api.siliconflow.com/v1",
            "https://docs.siliconflow.com/en/api-reference/chat-completions/chat-completions", modelsPath = null,
            notes = "International site. For the China site use https://api.siliconflow.cn/v1 with a key from that site.",
        ),
        openAi(
            "novita", "Novita AI", GATEWAY, "https://api.novita.ai/openai", "https://docs.novita.ai/api-reference/model-apis-llm-create-chat-completion",
            modelsPath = null, level = COMPATIBLE, notes = "Base URL is the commonly used value; confirm it in your Novita dashboard.",
        ),
        openAi("cerebras", "Cerebras", GATEWAY, "https://api.cerebras.ai/v1", "https://inference-docs.cerebras.ai/resources/openai"),
        openAi(
            "sambanova", "SambaNova", GATEWAY, "https://api.sambanova.ai/v1", "https://docs.sambanova.ai/docs/en/get-started/api-keys-urls",
            notes = "Model list includes context length and per-token pricing.",
        ),
        openAi(
            "deepinfra", "DeepInfra", GATEWAY, "https://api.deepinfra.com/v1/openai", "https://docs.deepinfra.com/chat/overview",
            modelsPath = "https://api.deepinfra.com/v1/models", notes = "Model list (with pricing) lives outside the /openai path.",
        ),
        ProviderDefinition(
            id = "replicate", displayName = "Replicate", category = GATEWAY, protocol = ApiProtocol.CUSTOM_REST,
            defaultBaseUrl = "https://api.replicate.com/v1", auth = AuthConfig.Bearer, docsUrl = "https://replicate.com/docs/reference/http",
            supportLevel = UNSUPPORTED, supportsStreaming = false,
            notes = "Replicate uses a predictions API, not chat completions. Not supported as a chat provider yet; you can map a specific model with the Custom API builder.",
        ),

        // ---- Local ----
        ProviderDefinition(
            id = "local-openai", displayName = "Local server (OpenAI-compatible)", category = ProviderCategory.LOCAL,
            protocol = OPENAI_CHAT_COMPLETIONS, defaultBaseUrl = "http://192.168.1.2:11434/v1", auth = AuthConfig.None,
            chatPath = "chat/completions", modelsPath = "models", docsUrl = "https://github.com/ollama/ollama/blob/main/docs/openai.md",
            supportLevel = COMPATIBLE,
            notes = "For Ollama, LM Studio, llama.cpp server or vLLM on your network. Requires 'Allow local-network endpoints' in Settings › Network. Replace the IP with your computer's address.",
        ),
    )

    private val byId = definitions.associateBy { it.id }

    fun find(id: String?): ProviderDefinition? = id?.let { byId[it] }

    /** Templates offered in the Custom API builder. */
    val customTemplates: List<CustomTemplate> = listOf(
        CustomTemplate("OpenAI-compatible", OPENAI_CHAT_COMPLETIONS, AuthConfig.Bearer, "chat/completions", "models"),
        CustomTemplate("Anthropic-compatible", ANTHROPIC_MESSAGES, AuthConfig.header("x-api-key"), "v1/messages", "v1/models", AnthropicVersions.DEFAULT),
        CustomTemplate("Gemini-compatible", GEMINI_GENERATE_CONTENT, AuthConfig.header("x-goog-api-key"), null, "models"),
        CustomTemplate("Custom REST JSON", ApiProtocol.CUSTOM_REST, AuthConfig.Bearer, null, null),
    )
}

data class CustomTemplate(
    val label: String,
    val protocol: ApiProtocol,
    val auth: AuthConfig,
    val chatPath: String?,
    val modelsPath: String?,
    val apiVersion: String? = null,
)
