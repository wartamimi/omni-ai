# OmniAI

OmniAI is a native Android client for many AI providers that you use with **your own API keys** (BYOK).
It's written in Kotlin with Jetpack Compose and Material 3. There's no OmniAI server: requests go straight
from your phone to the provider you configure.

> **Status:** first complete build of all planned phases. See [PROJECT_STATUS.md](PROJECT_STATUS.md)
> for what's done, what's verified and what's still limited.

## Features

- **27 provider templates** from official API docs (OpenAI, Anthropic, Gemini, DeepSeek, Mistral, Groq, xAI,
  OpenRouter, Together, Hugging Face, Cerebras, SambaNova, DeepInfra, Bedrock and more), a local-server
  template (Ollama / LM Studio / llama.cpp), and a **Custom API builder** for any OpenAI-, Anthropic- or
  Gemini-compatible API or a fully mapped custom REST JSON API. See [PROVIDER_SUPPORT.md](PROVIDER_SUPPORT.md).
- **Multiple keys per provider**, encrypted with the Android Keystore, with optional biometric lock.
- **Chat** with streaming, stop, regenerate, edit & resend, reasoning display, Markdown and code highlighting,
  per-chat model and parameters, personas, prompt templates, and image/document attachments
  (PDF, DOCX, PPTX, XLSX, text; text is extracted on the device and you can inspect it before sending).
- **Conversation management**: search across titles and messages, pin, archive, rename, duplicate,
  export/import (Markdown, JSON).
- **Compare** one prompt across up to 6 models in parallel, with latency, tokens and estimated cost.
- **Model browser**: discovered models with context window and pricing when the provider reports it,
  favorites, capabilities, categories, manual model IDs and user-entered pricing.
- **Model router** that ranks models on the device from metadata, tags, pricing and recent provider health.
- **Local tools** (calculator, date/time, JSON formatter, text stats, unit converter) the model can call,
  with per-tool permission (allow / ask / deny) and an execution log. Off by default.
- **Usage dashboard**: tokens and estimated cost per provider/model/day, CSV/JSON export,
  manual adjustments. Costs can be shown in USD, **BND (Brunei dollar)** and other currencies using
  exchange rates you enter.
- Light/dark/system theme, accent colors, Material You dynamic color, text size.

## Security in short

- API keys are encrypted with AES-256-GCM using a non-exportable key in the Android Keystore and stored in
  app-private no-backup storage. They're never logged, exported, backed up or sent anywhere except to the
  provider endpoint you configured.
- TLS is always validated against the system trust store. Plain HTTP is only possible for local/private
  network addresses, and only after you enable it.
- Requests to private, loopback and cloud-metadata addresses are blocked unless you allow local-network
  endpoints. Redirects aren't followed.
- Read [docs/SECURITY.md](docs/SECURITY.md), including the limits of storing keys on a phone.

## Building

Requirements: JDK 17+, Android SDK with platform 37.

```bash
./gradlew assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests (JVM modules run with ./gradlew test)
./gradlew assembleRelease        # needs signing config, see docs/SIGNING.md
```

Every push to `main` is built by GitHub Actions ([.github/workflows/android.yml](.github/workflows/android.yml)),
which runs the unit tests and uploads the debug APK as a workflow artifact.

## Project layout

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md).

```
app/                 Application, MainActivity, navigation shell
build-logic/         Gradle convention plugins
core/model           Domain models (pure Kotlin)
core/common          Redaction, endpoint validation, token/cost math, templates (pure Kotlin)
core/provider        Provider adapters, SSE, HTTP, retry, catalog (pure Kotlin + OkHttp)
core/domain          Chat engine, context strategies, compare, router, tools (pure Kotlin)
core/security        Keystore encryption, credential vault, biometric gate
core/database        Room database
core/datastore       Settings (DataStore)
core/data            Repositories, attachments, export/import, DI wiring
core/designsystem    Theme and shared components
core/ui              Markdown, formatting, model picker
feature/*            Screens: home, chat, compare, providers, models, prompts, personas, usage, settings
```

## License

No license has been chosen yet. All rights reserved by the repository owner until one is added.
