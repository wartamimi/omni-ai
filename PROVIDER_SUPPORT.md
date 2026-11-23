# Provider support

Templates were built from each provider's official API documentation on **2026-11-23**. Endpoints, models
and prices change; OmniAI doesn't hard-code model lists or prices. Models come from the provider's model-list
endpoint where one exists, otherwise you type model IDs. Prices come from the provider's API when it reports
them (OpenRouter, SambaNova, DeepInfra) or from what you enter.

Raw research notes: [docs/provider-research-2026-11.md](docs/provider-research-2026-11.md).

**Levels**
- **Supported**: template follows the provider's documented API.
- **Compatible**: works through an OpenAI-compatible endpoint the provider documents, with caveats noted.
- **Not supported**: listed so you know why it isn't usable yet.

"Verified live" means a real request was made with a real key. **No provider has been verified live yet**:
the adapters are covered by unit tests against recorded response shapes (MockWebServer), not by calls to
the real services. Please report what works.

| Provider | Level | Protocol | Base URL | Model list | Notes |
|---|---|---|---|---|---|
| OpenAI | Supported | Chat Completions (Responses API selectable) | `https://api.openai.com/v1` | Yes (no pricing/context) | Uses `max_completion_tokens`. |
| Anthropic | Supported | Messages | `https://api.anthropic.com` | Yes (context, max output) | `anthropic-version: 2023-06-01` by default; `2026-10-21` selectable (rejects temperature/top_p/top_k, which OmniAI then omits). |
| Google Gemini | Supported | generateContent / streamGenerateContent (SSE) | `https://generativelanguage.googleapis.com/v1beta` | Yes (token limits) | Google labels generateContent "legacy" but fully supported. |
| DeepSeek | Supported | OpenAI chat | `https://api.deepseek.com` | Yes | Reasoning content shown when returned. |
| Mistral AI | Supported | OpenAI-style chat | `https://api.mistral.ai/v1` | Yes (context, capabilities) | |
| Groq | Supported | OpenAI chat | `https://api.groq.com/openai/v1` | Yes (context) | |
| xAI | Supported | OpenAI chat | `https://api.x.ai/v1` | Yes | |
| Cohere | Compatible | OpenAI Compatibility API | `https://api.cohere.ai/compatibility/v1` | Manual | |
| Perplexity | Compatible | Orchestrator (OpenAI chat) | `https://api.perplexity.ai/orchestrator/v1` | Manual | Sonar Chat Completions ended 2026-09-27. |
| Alibaba Qwen | Compatible | OpenAI chat | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Manual | Workspace host recommended; key must match region. |
| Zhipu AI (China) | Supported | OpenAI chat | `https://open.bigmodel.cn/api/paas/v4` | Manual | |
| Z.ai | Supported | OpenAI chat | `https://api.z.ai/api/paas/v4` | Manual | |
| Moonshot (Kimi) | Supported | OpenAI chat | `https://api.moonshot.ai/v1` | Yes | |
| AI21 Labs | Compatible | OpenAI-shaped chat | `https://api.ai21.com/studio/v1` | Manual | |
| Amazon Bedrock | Compatible | OpenAI chat with Bedrock API key | `https://bedrock-runtime.{region}.amazonaws.com/openai/v1` | Manual | SigV4 credentials not supported. |
| OpenRouter | Supported | OpenAI chat | `https://openrouter.ai/api/v1` | Yes (context, pricing) | Sends `X-OpenRouter-Title: OmniAI`. |
| Together AI | Supported | OpenAI chat | `https://api.together.ai/v1` | Yes | |
| Fireworks AI | Supported | OpenAI chat | `https://api.fireworks.ai/inference/v1` | Manual | |
| NVIDIA NIM | Supported | OpenAI chat | `https://integrate.api.nvidia.com/v1` | Manual | |
| Hugging Face Inference Providers | Supported | OpenAI chat | `https://router.huggingface.co/v1` | Yes | Fine-grained token needed. |
| SiliconFlow | Supported | OpenAI chat | `https://api.siliconflow.com/v1` | Manual | China site: `.cn`. |
| Novita AI | Compatible | OpenAI chat | `https://api.novita.ai/openai` | Manual | Confirm base URL in dashboard. |
| Cerebras | Supported | OpenAI chat | `https://api.cerebras.ai/v1` | Yes | |
| SambaNova | Supported | OpenAI chat | `https://api.sambanova.ai/v1` | Yes (context, pricing) | |
| DeepInfra | Supported | OpenAI chat | `https://api.deepinfra.com/v1/openai` | Yes (pricing) | List at `https://api.deepinfra.com/v1/models`. |
| Replicate | Not supported | Predictions API | — | — | Not a chat API; map one model with the Custom API builder. |
| Local server | Compatible | OpenAI chat | e.g. `http://192.168.1.2:11434/v1` | Yes | Ollama, LM Studio, llama.cpp, vLLM. Needs local-network toggle. |

## Custom APIs

The Custom API builder offers four starting points: OpenAI-compatible, Anthropic-compatible,
Gemini-compatible and **Custom REST JSON**. The last one lets you write a JSON body template with
placeholders (`{{model}}`, `{{messages}}`, `{{prompt}}`, `{{system}}`, `{{temperature}}`, `{{max_tokens}}`,
`{{stream}}`) and JSON paths for the reply text, token counts, error message and the SSE stream delta.
Templates and paths are validated before saving.

## Features by protocol

| | OpenAI chat | OpenAI Responses | Anthropic | Gemini | Custom REST |
|---|---|---|---|---|---|
| Streaming | ✓ | ✓ | ✓ | ✓ | if stream path set |
| Images | ✓ | ✓ | ✓ | ✓ | – |
| Tool calling | ✓ | – | ✓ | ✓ | – |
| Reasoning text | when returned | when returned | thinking blocks | thought parts | – |
| Token usage | ✓ (stream usage where documented) | ✓ | ✓ | ✓ | if paths set |
