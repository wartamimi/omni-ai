# Architecture

## Analysis
OmniAI has to talk to ~30 different AI APIs that mostly share one of four wire protocols, store secrets on a
device that can be lost or rooted, and stay honest about things it can't know (prices, capabilities,
model lists). The main risks and how the design handles them:

| Risk | Mitigation |
|---|---|
| Provider APIs change or differ in small ways | Protocols as adapters, provider differences as data; per-provider overrides (paths, headers, extra JSON params); Custom API builder as an escape hatch |
| Leaking API keys | Keystore encryption, keys never in Room/DataStore/logs/exports, redaction of errors and logs, FLAG-free clipboard copy marked sensitive |
| SSRF / local network attacks through user-entered URLs | URL validation + DNS-level blocking of private/metadata addresses, no redirects |
| Double billing on retry | Retries only before a response starts, never on 5xx after streaming begins |
| Misleading cost numbers | Costs labelled "estimated", unknown prices shown as unknown, reported vs estimated tokens labelled |
| App killed during generation | Partial text is persisted when stopped or failed; status shows cancelled/error |
| Android build not verifiable in this environment | Pure-JVM core tested locally; Android modules built and tested on GitHub Actions |

## Layers (Clean Architecture + MVVM)

```
 feature/* (Compose screens + ViewModels)
        │ uses
 core/ui, core/designsystem
        │
 core/domain  ── interfaces (repositories) + engines (ChatEngine, CompareEngine, ModelRouter, ToolExecutor)
        ▲ implemented by
 core/data  ── repositories, AdapterProvider, ProviderManager, attachments, export, DI
        │ uses
 core/database (Room) · core/datastore (DataStore) · core/security (Keystore) · core/provider (adapters, HTTP)
        │
 core/model, core/common (pure Kotlin)
```

- **core/provider**: `AIProviderAdapter` with `fetchModels`, `generate`, `stream`, `validateConnection`,
  `cancelRequest`. Implementations: `OpenAiChatAdapter`, `OpenAiResponsesAdapter`, `AnthropicAdapter`,
  `GeminiAdapter`, `CustomRestAdapter`. Shared HTTP plumbing in `BaseHttpAdapter`; `ProviderCatalog` holds
  the documented templates.
- **core/domain**: `ChatEngine` builds context, filters parameters, streams, persists, records usage and runs
  the tool loop. `CompareEngine` runs one job per target. `ModelRouter` ranks models.
- **core/data**: `AdapterProviderImpl` re-validates the endpoint and decrypts the active key per request;
  `ProviderManager` handles test/refresh/save; Hilt modules wire everything.

## Data flow: sending a message
1. `ChatViewModel.send()` → `ChatEngine.send()` stores the user message and starts a job in the app scope.
2. The engine loads provider, model, persona and settings; `ContextBuilder` applies the context strategy.
3. `AdapterProvider` validates the URL against current network settings, decrypts the key, builds the adapter.
4. The adapter streams SSE events; the engine updates `live` (UI overlay) and persists at the end.
5. Usage (reported or estimated tokens, estimated cost) is recorded; provider health is updated.
6. If the model asks for tools and tools are enabled, `ToolExecutor` checks permission (asking the user via
   `ToolApprovalBridge` when needed), runs the tool locally, and the engine continues for up to N rounds.

## Roadmap (phases from the spec)
1. Foundation ✓ 2. Provider management ✓ 3. Core integrations ✓ 4. Chat engine ✓ 5. Model management ✓
6. Compare, router, prompts, personas ✓ 7. Multimodal (images, documents) ✓ 8. Tools ✓ 9. Analytics ✓
10. Security & optimisation ✓ 11. Final build: in CI. Next steps are in `PROJECT_STATUS.md`.
