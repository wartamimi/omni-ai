# Provider API research notes (2026-11-23)

Raw notes gathered from each provider's official documentation while building the provider catalog.
`PROVIDER_SUPPORT.md` is the user-facing summary; this file keeps the details and open questions.

## Notable recent changes
1. **Anthropic** has two API versions: `2026-10-21` (current) and `2023-06-01` (still listed). Under
   `2026-10-21`, `temperature`, `top_p` and `top_k` are rejected with HTTP 400. OmniAI defaults to
   `2023-06-01` and strips those parameters automatically if the user selects `2026-10-21`.
   `Authorization: Bearer` is now primary; `x-api-key` is a supported legacy fallback.
2. **Perplexity** ended Sonar Chat Completions on 2026-09-27. The OpenAI-compatible path is the
   Orchestrator: `https://api.perplexity.ai/orchestrator/v1/chat/completions`.
3. **Gemini** `generateContent` is labelled "legacy" (still fully supported, no retirement date);
   Google recommends the Interactions API. OmniAI uses `generateContent` / `streamGenerateContent?alt=sse`.
4. **Alibaba Model Studio** recommends workspace-specific hosts
   (`https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1`). The template asks
   the user for the base URL.
5. **Amazon Bedrock** accepts Bedrock API keys as `Authorization: Bearer` on an OpenAI-compatible
   endpoint: `https://bedrock-runtime.{region}.amazonaws.com/openai/v1`.

## Endpoint table
| Provider | Base URL | Protocol | Auth | Models list |
|---|---|---|---|---|
| OpenAI | https://api.openai.com/v1 | Chat Completions / Responses | Bearer | GET /models (no pricing/context) |
| Anthropic | https://api.anthropic.com | Messages POST /v1/messages | x-api-key or Bearer + anthropic-version | GET /v1/models (max_input_tokens, max_tokens) |
| Gemini | https://generativelanguage.googleapis.com/v1beta | generateContent | x-goog-api-key | GET /models (inputTokenLimit, outputTokenLimit) |
| DeepSeek | https://api.deepseek.com | OpenAI chat | Bearer | GET /models |
| Mistral | https://api.mistral.ai/v1 | OpenAI-style chat | Bearer | GET /models (max_context_length, capabilities) |
| Groq | https://api.groq.com/openai/v1 | OpenAI chat | Bearer | GET /models (context_window) |
| xAI | https://api.x.ai/v1 | OpenAI chat | Bearer | GET /models |
| Cohere (compat) | https://api.cohere.ai/compatibility/v1 | OpenAI chat | Bearer | unconfirmed |
| Perplexity | https://api.perplexity.ai/orchestrator/v1 | OpenAI chat | Bearer | unconfirmed path |
| Alibaba Qwen | workspace host …/compatible-mode/v1 | OpenAI chat | Bearer (region-bound key) | not documented |
| Zhipu (China) | https://open.bigmodel.cn/api/paas/v4 | OpenAI chat | Bearer | not documented |
| Z.ai | https://api.z.ai/api/paas/v4 | OpenAI chat | Bearer | not documented |
| Moonshot/Kimi | https://api.moonshot.ai/v1 | OpenAI chat | Bearer | GET /models |
| AI21 | https://api.ai21.com/studio/v1 | OpenAI-shaped chat | Bearer | not documented |
| Amazon Bedrock | https://bedrock-runtime.{region}.amazonaws.com/openai/v1 | OpenAI chat | Bedrock API key Bearer | not on runtime host |
| OpenRouter | https://openrouter.ai/api/v1 | OpenAI chat | Bearer (+HTTP-Referer, X-OpenRouter-Title) | GET /models (pricing per token, context_length) |
| Together AI | https://api.together.ai/v1 | OpenAI chat | Bearer | GET /models |
| Fireworks | https://api.fireworks.ai/inference/v1 | OpenAI chat | Bearer | no OpenAI-style list confirmed |
| NVIDIA NIM | https://integrate.api.nvidia.com/v1 | OpenAI chat | Bearer | unconfirmed |
| Hugging Face | https://router.huggingface.co/v1 | OpenAI chat | Bearer HF token | GET /models |
| SiliconFlow | https://api.siliconflow.com/v1 (intl) / .cn | OpenAI chat | Bearer | not documented |
| Novita | https://api.novita.ai/openai (unconfirmed) | OpenAI chat | Bearer | path unconfirmed |
| Cerebras | https://api.cerebras.ai/v1 | OpenAI chat | Bearer | GET /models |
| SambaNova | https://api.sambanova.ai/v1 | OpenAI chat | Bearer | GET /models (pricing per token, context_length) |
| DeepInfra | https://api.deepinfra.com/v1/openai | OpenAI chat | Bearer | GET https://api.deepinfra.com/v1/models |
| Replicate | https://api.replicate.com/v1 | Predictions (not chat) | Bearer | — |
