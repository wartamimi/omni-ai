# Technical decisions

Each entry: the decision, why, and what it costs. Newest decisions are appended at the end.

## Build and toolchain
1. **AGP 9.5.1, Gradle 9.9.2, Kotlin 2.4.21, KSP 2.3.14, Hilt 2.60.1, compileSdk/targetSdk 37, minSdk 26.**
   Latest stable versions verified on 2026-11-23. AGP 9 compiles Kotlin itself ("built-in Kotlin"),
   so modules don't apply `org.jetbrains.kotlin.android`. If a plugin turns out to be incompatible,
   the fallback is `android.builtInKotlin=false` in `gradle.properties`.
2. **Convention plugins in `build-logic/`** keep the 20 module build files to a few lines and keep SDK levels,
   Java target and test dependencies in one place.
3. **Pure-Kotlin core modules** (`model`, `common`, `provider`, `domain`) have no Android dependency, so the
   protocol adapters and chat engine are tested on the plain JVM quickly and without an emulator.

## Networking
4. **OkHttp directly, no Retrofit.** Every provider endpoint is user-configurable (base URL, paths, headers,
   auth style), streaming is Server-Sent Events, and the Custom REST mapping builds bodies from templates.
   Retrofit's static interfaces add nothing here and would hide request construction.
5. **No automatic retries inside OkHttp** (`retryOnConnectionFailure=false`) and **no redirects**.
   `RetryPolicy` retries only on 429/503/DNS/connection errors and only before any response body has been
   received, honouring `Retry-After` up to 30 s. Rationale: a retried request after the provider started
   generating can be billed twice.
6. **SSRF guard at DNS level** (`SafeDns`) in addition to URL validation, so a public hostname that resolves to
   a private address is still blocked unless local-network access is allowed.
7. **Cleartext HTTP is permitted in the network security config** but enforced by the app: `EndpointValidator`
   rejects `http://` for any non-private host and allows it for private/loopback hosts only when the user
   enables it. A per-domain config can't express "any LAN IP", which local servers (Ollama etc.) need.
   TLS validation is never disabled and user-installed CAs aren't trusted.

## Providers
8. **Four protocol adapters + one mapped REST adapter** instead of one class per provider. Most providers
   expose an OpenAI-compatible chat endpoint; differences are captured as data (`ProviderDefinition`,
   `OpenAiChatOptions`).
9. **No hard-coded model lists or prices.** Models come from list endpoints; prices only when the provider's
   API reports them or the user enters them. Unknown is shown as unknown.
10. **Anthropic defaults to `anthropic-version: 2023-06-01`.** The newer `2026-10-21` rejects temperature,
    top_p and top_k. Both are selectable; with the newer one those parameters are omitted.
11. **Unsupported parameters are dropped, not remapped** (`GenerationParams.restrictedTo`). The parameters
    sheet only shows what the selected protocol accepts.
12. **Perplexity uses the Orchestrator endpoint** because Sonar Chat Completions ended on 2026-09-27.

## Data and security
13. **API keys: AES-256-GCM, key in Android Keystore, ciphertext in `noBackupFilesDir`.** Room holds only
    metadata (label, masked hint, status). EncryptedSharedPreferences (deprecated) is not used.
14. **No backup, no device transfer** (`allowBackup=false`, data extraction rules exclude everything).
    Keystore keys can't be restored on another device anyway, and conversations may be sensitive.
    Users move data with explicit export files, which never include keys.
15. **Settings in DataStore as one JSON document** with defaults on decode failure and clamping of numeric
    values, so a corrupted or older file never crashes the app.
16. **Room schema version 1 exported to `core/database/schemas`.** Later schema changes must add migrations
    to `OmniMigrations`; destructive migration is not enabled.
17. **Attachments:** images are re-encoded to JPEG (max 1568 px side, strips EXIF); documents are converted
    to text on the device (PDFBox-Android for PDF, ZIP/XML parsing for DOCX/PPTX/XLSX). Only the extracted
    text is sent, and the user can read it first. Audio/video are rejected with an explanation.

## Chat engine
18. **Generations run in an application-scoped coroutine**, so leaving the screen doesn't cancel them; the UI
    overlays the live text (`ChatEngine.live`) on stored messages. Stop cancels the job and the HTTP call.
19. **Context strategies:** full, sliding window (default), and summarize-older-messages. The UI shows how many
    messages were left out.
20. **Tool calling is off by default**, tools are local and pure (no network, no file system, no code
    execution), each has allow/ask/deny permission, and every call is logged.
21. **Model router is local and heuristic.** It never sends the prompt anywhere to decide, and its scores are
    rankings, not quality claims. User-defined rules are stored in settings.

## Currency
22. **Exchange rates are entered by the user** (e.g. USD→BND). The app doesn't fetch rates, so it makes no
    network calls except to the user's providers. If no rate is set, amounts are shown in USD with a note.

## UI
23. **Single activity, Navigation Compose with type-safe routes, `NavigationSuiteScaffold`** so phones get a
    bottom bar and tablets/foldables get a rail. The bar is hidden on detail screens.
24. **`MainActivity` extends `FragmentActivity`** because `BiometricPrompt` requires it.
