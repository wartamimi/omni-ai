# Project status

_Last updated: 2026-11-23_

## Summary
All 11 phases of the OmniAI spec have a first implementation. The pure-Kotlin core (protocols, chat engine,
router, tools, cost math, security helpers) is compiled and unit-tested. The Android modules and screens are
built on GitHub Actions; see the latest run for the current build status.

## Phase report

| Phase | What's implemented | Verified by |
|---|---|---|
| 1 Foundation | Multi-module Gradle (convention plugins), Hilt, Room, DataStore, theme, navigation shell | CI build |
| 2 Provider management | Catalog of 27 templates + local server; editor; multiple keys per provider; test connection; enable/disable; default; duplicate; export/import config (no keys); biometric lock for keys; Custom API builder with validation and JSON editing | CI build; unit tests for validation helpers |
| 3 Core integrations | Adapters: OpenAI Chat Completions, OpenAI Responses, Anthropic Messages, Gemini generateContent, mapped Custom REST; SSE parser; error mapping; safe retries; SSRF-safe DNS | 21 JVM unit tests with MockWebServer |
| 4 Chat engine | Streaming, stop, regenerate, edit & resend, persistence, reasoning display, context strategies (full / sliding window / summarize), auto titles, search, pin/archive/rename/duplicate/delete, Markdown/JSON export & import | 13 JVM engine tests; Room DAO tests (Robolectric) |
| 5 Model management | Model discovery, metadata parsing (context, pricing where reported), favorites, capabilities, categories, manual models, user pricing | Parser unit tests |
| 6 Compare / router / prompts / personas | Parallel compare with per-target stop and export; local router with modes and user rules; prompt library with variables; personas with default model | Router unit tests |
| 7 Multimodal | Image attachments (resized, EXIF removed); PDF/DOCX/PPTX/XLSX/text extraction with preview; vision capability check | CI build only |
| 8 Tools | 5 local tools, allow/ask/deny permissions, approval dialog, execution log, tool loop limit | Calculator/tool unit tests |
| 9 Analytics | Usage per provider/model/day, reported vs estimated tokens, estimated cost, USD/BND/other currencies with user rates, CSV/JSON export, manual adjustments, reset | Cost math unit tests |
| 10 Security & optimisation | Keystore encryption, no backup, redaction, endpoint validation, cleartext only for local, R8 for release | Redactor/validator unit tests |
| 11 Final build | GitHub Actions: JVM tests, debug APK, Android unit tests, optional signed release | CI |

## Test counts
- JVM: `core:common` 15, `core:provider` 21, `core:domain` 13 → **49 passing** locally.
- Android unit tests: `core:datastore` (settings), `core:database` (DAO, Robolectric). Run in CI.

## Known limitations
- **No provider has been tested with a real API key yet.** Adapters follow the official docs and are tested
  against recorded response shapes. Real-world quirks will need fixes; please report them.
- Audio and video attachments are not supported (rejected with an explanation). Voice input/output is not
  implemented.
- Image generation models are not supported in chat (no image output rendering).
- OpenAI Responses adapter doesn't do tool calling (Chat Completions does).
- Exchange rates are manual; no automatic currency updates.
- Token counts are estimated locally when a provider doesn't report usage, and labelled "(est.)".
- Scanned PDFs without a text layer yield no text (no OCR).
- UI is English only.
- No instrumented (on-device) UI tests yet.

## Next steps
1. Try each provider with a real key and fix differences.
2. Add Compose UI tests for the main flows (add provider, chat, compare).
3. Add localisation (Malay, Indonesian).
4. Optional: OCR for scanned PDFs, speech input via a provider that accepts audio.
