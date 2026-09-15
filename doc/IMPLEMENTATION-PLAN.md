# Locus — Implementation Plan

applicationId: `com.locus.app` · minSdk 31 · Kotlin 2.0.21 · single-repo, two Gradle flavors (`oss`, `full`)

## 1. Stack

| Library | Version | Why |
|---|---|---|
| Kotlin | 2.0.21 | language |
| Android Gradle Plugin | 8.7.2 | build system |
| Compose BOM | 2024.12.01 | NF-1 Compose UI, version-aligned Compose artifacts |
| Compose Navigation | 2.8.5 | in-app routing (single Activity, NF-1) |
| Material 3 (Compose) | via BOM | NF-1 Material 3 |
| Hilt | 2.52 | mandated DI (ARCHITECTURE LAW); constructor injection only |
| hilt-navigation-compose | 1.2.0 | `hiltViewModel()` in Nav destinations |
| Room + Room-KTX | 2.6.1 | note index, FTS, vectors, chat history, audit journal, reminders (N-1, S-1, C-1, C-6, R-*) |
| kotlinx.coroutines | 1.9.0 | suspend repos, cold Flows, structured concurrency for the flush pipeline |
| kotlinx.serialization-json | 1.7.3 | catalog/models.json, tool-call JSON envelopes, audit-journal diffs |
| SnakeYAML | 2.3 | pure-JVM YAML codec for frontmatter (N-3) — safe inside `:core:domain`, no Android dependency |
| OkHttp + okhttp-sse | 4.12.0 | provider HTTP calls + SSE streaming (P-1/P-2); raw control needed for arbitrary OpenAI-compatible base URLs |
| DataStore Preferences | 1.1.1 | settings: provider config, routing prefs, dashboard schedule, price table, bulk cap |
| WorkManager | 2.10.0 | backups, resumable model downloads, dashboard jobs, lowest-tier reminders (R-2) |
| androidx.security:security-crypto | 1.1.0 | AndroidKeyStore-wrapped secrets at rest (P-6, SEC-3) |
| Android NDK (side-by-side) | 27.2.12479018 | native toolchain for the llama.cpp JNI build |
| CMake | 3.22.1 | native build for `:core:ai`'s llama.cpp submodule |
| llama.cpp | git submodule, `ggml-org/llama.cpp`, commit pinned at Prompt-24 execution time via the GitHub Releases API (see Prompt 24) | M-1 shared chat+embedding runtime, CPU-first |
| JUnit4 | 4.13.2 | test runner |
| kotlinx-coroutines-test | 1.9.0 | coroutine test scheduling |
| Turbine | 1.1.0 | Flow assertions |
| MockK | 1.13.13 | mocks only where a hand-written fake is disproportionate (ARCHITECTURE LAW) |
| Robolectric | 4.14 | Android-framework tests (Workers, receivers) |
| detekt | 1.23.7 | static analysis gate |
| Spotless (+ ktlint 1.3.1) | 6.25.0 | formatting gate |

All versions pinned in `gradle/libs.versions.toml` (created Prompt 1); nothing hardcoded in module `build.gradle.kts` files.

**Why no Retrofit:** provider adapters need raw SSE streaming against arbitrary user-supplied base URLs (P-2) and a JSON-mode tool loop that inspects raw text (C-8) — plain OkHttp + kotlinx.serialization gives that control without a second HTTP abstraction. The HF and GitHub REST calls (model manager, catalog bot) are simple enough not to need Retrofit either.

## 2. Module dependency diagram

```
                        ┌───────────────────────┐
                        │         :app           │
                        │ Compose UI · ViewModels │
                        │ Hilt @Modules · Workers │
                        │ Receivers · FileProvider│
                        │ src/full → P-3 adapters │
                        └──────┬───────┬─────┬────┘
                               │       │     │
                 ┌─────────────┘       │     └───────────────┐
                 ▼                     ▼                     ▼
        ┌────────────────┐   ┌────────────────┐    (also depends on
        │   :core:data    │   │    :core:ai     │     :core:domain
        │ Room · SAF I/O  │   │ provider adapters│     directly, below)
        │ DataStore       │   │ SSE · JSON tool  │
        │ backup/zip      │   │ loop · llama.cpp │
        │ Keep importer   │   │ JNI · model mgr  │
        │ gateway impls   │   │ embedding runner │
        └────────┬────────┘   └────────┬────────┘
                 │                      │
                 └──────────┬───────────┘
                             ▼
                    ┌─────────────────┐
                    │   :core:domain   │  pure Kotlin/JVM
                    │ entities · repo &│  no Android, no Room,
                    │ gateway interfaces│  no OkHttp
                    │ use cases · pure  │
                    │ algorithms        │
                    └─────────────────┘
```

Dependency rule points inward (`:app → :core:data/:core:ai → :core:domain`); `:app` only touches `:core:data`/`:core:ai` concrete types inside Hilt `@Module` classes (enforced mechanically by `scripts/import-hygiene.sh`, Prompt 1).

## 3. Layering summary (ARCHITECTURE LAW recap)

- `:core:domain` — entities, `Repository`/`*Gateway` interfaces, use cases, pure algorithms (frontmatter parser, checksum, chunker, RRF, P-4 router + SEC-5 gate, C-5/C-5a classifier, reminder recurrence). Compile-enforced Android-free.
- `:core:data` — Room (5 entity groups: note index/FTS, vectors, chat, audit journal, reminders), SAF file I/O, DataStore, backup/zip, Keep importer, gateway implementations (`AlarmScheduler`, WorkManager wrappers). Implements domain interfaces.
- `:core:ai` — provider adapters (OpenAI-compatible/Anthropic/Gemini), SSE, JSON-mode tool loop, usage/cost tracking, llama.cpp JNI + model manager, on-device embedding runner. Implements domain interfaces.
- `:app` — Compose screens (Route + ViewModel + immutable `UiState` + one-time-effect Channel), Hilt wiring, Workers, receivers, notification channels, FileProvider. `src/full` isolates P-3 subscription adapters.

## 4. Prompt index

77 prompts across 5 phases + a final gate. Full text in `PROMPTS.md`; full ID→prompt mapping in `TRACEABILITY.md`.

| # | Title | Phase | REQ IDs | Depends on |
|---|---|---|---|---|
| 1 | Repo scaffold: modules, version catalog, Hilt app class | 0 | NF-1, NF-2 | — |
| 2 | Spotless/detekt/import-hygiene/CI workflow/DECISIONS.md | 0 | NF-5(partial), NF-1 | 1 |
| 3 | Domain core models, Clock/DispatcherProvider, checksum util | 0 | N-3, NF-1 | 1 |
| 4 | Frontmatter parser + repair (verbatim) | 0 | N-3, N-5 | 3 |
| 5 | Room schema v1 (note index + FTS) | 0 | N-1, N-3, S-1 | 3 |
| 6 | SAF/.md file I/O + NoteRepository interface | 0 | N-2, N-3 | 3,4 |
| 7 | N-1 flush pipeline (verbatim) | 0 | N-1, NF-6 | 3,5,6 |
| 8 | FileNoteRepository: collision suffixing, checksum rescan | 0 | N-1, N-2, N-10 | 6,7 |
| 9 | Keyword search use case (FTS) | 0 | S-1(keyword) | 5 |
| 10 | Hilt modules, NavGraph skeleton, Compose theme | 0 | NF-1 | 1,8 |
| 11 | Notes Grid screen | 0 | N-6(grid) | 10 |
| 12 | Folder Tree screen | 0 | N-6(tree), N-2 | 10 |
| 13 | Editor screen | 0 | N-4, N-7 | 10,7 |
| 14 | Trash (restore) | 0 | N-8 | 8 |
| 15 | Version history | 0 | N-9, NF-6 | 7 |
| 16 | Scheduled auto-backup | 0 | N-11 | 8 |
| 17 | Import/export full-library zip | 0 | N-12 | 16 |
| 18 | Reminder model + recurrence calc + AlarmScheduler interface | 0 | R-6 | 3 |
| 19 | Permission ladder + BootReceiver | 0 | R-1,R-2,R-3,R-4 | 18 |
| 20 | Reminder notification (Complete/Snooze) | 0 | R-5 | 19 |
| 21 | Search screen (keyword) | 0 | S-1(UI) | 9,10 |
| 22 | **Phase 0 gate** | 0 | N-1…N-12,R-1…R-6,S-1(kw),NF-3,NF-6 | 1–21 |
| 23 | Chunker (verbatim) | 1 | S-3 | 3 |
| 24 | llama.cpp JNI module (embedding path) | 1 | M-1(partial) | 1 |
| 25 | embeddinggemma-300m bundling + EmbeddingRunner | 1 | S-2, SEC-1 | 24 |
| 26 | RRF fusion (verbatim) | 1 | S-1(fusion) | 3 |
| 27 | Vector store (brute-force cosine) + incremental indexing | 1 | S-4, S-9 | 25 |
| 28 | Indexing pipeline + query scoping + context packing | 1 | S-5, S-8 | 7,23,26,27 |
| 29 | Degradation policy | 1 | S-6 | 27 |
| 30 | Hybrid search UI | 1 | S-1(UI),S-5(UI) | 21,28 |
| 31 | Chat session/message models + history | 1 | C-1 | 5 |
| 32 | Provider adapter interfaces + OpenAI-compatible adapter | 1 | P-1, P-2 | 3 |
| 33 | Anthropic + Gemini adapters | 1 | P-1 | 32 |
| 34 | RAG answer use case (mandatory citations) | 1 | S-7, C-2 | 28,32 |
| 35 | JSON-mode tool loop (verbatim) + read tools | 1 | C-3, C-8 | 32,6,9 |
| 36 | Chat screen | 1 | C-1(UI), C-9 | 31,34,35 |
| 37 | SEC-2 active-model indicator | 1 | SEC-2 | 36 |
| 38 | Bulk-op cap setting | 1 | C-7 | 10 |
| 39 | P-4 routing table (Chat/RAG row, partial) | 1 | P-4(partial) | 34 |
| 40 | **Phase 1 gate** | 1 | S-1…S-9,C-1…C-3,C-7…C-9,P-1,P-2,P-4(partial),SEC-1,SEC-2 | 23–39 |
| 41 | llama.cpp text-generation path | 2 | M-1 | 24 |
| 42 | Full-offline pipeline flag | 2 | M-2 | 41,32 |
| 43 | HF model browse/search client | 2 | M-3(browse) | 24 |
| 44 | Quant picker, resumable downloads, storage stats | 2 | M-3(download) | 43 |
| 45 | Per-model notes/ratings + load & benchmark | 2 | M-3, M-9(tok/s) | 44 |
| 46 | Unified model registry/picker | 2 | M-5(picker), M-4 | 45,33 |
| 47 | Catalog fetch (bundled snapshot + live refresh) | 2 | M-5(catalog) | 46 |
| 48 | Catalog seeds: real HF repo/filename/SHA-256 | 2 | M-6, M-8 | 47 |
| 49 | Dismissible recommendations | 2 | M-8(surfacing) | 48 |
| 50 | Recommendation ranking policy | 2 | M-9 | 45,48 |
| 51 | Thermal warning | 2 | M-7 | 41 |
| 52 | P-4 router + SEC-5 gate (verbatim) | 2 | P-4(full), SEC-5 | 39,50 |
| 53 | Usage/cost tracking | 2 | P-5 | 32 |
| 54 | Secret encryption at rest | 2 | P-6, SEC-3 | 32 |
| 55 | NF-8 catalog discovery bot | 2 | NF-8 | 48 |
| 56 | **Phase 2 gate** | 2 | M-1…M-9,NF-8,P-4(full),P-5,P-6,SEC-5,NF-3 | 41–55 |
| 57 | Safety-tier classifier (verbatim) | 3 | C-5, C-5a | 3 |
| 58 | Write-tool contracts | 3 | C-4 | 6,18 |
| 59 | Write tools wired into tool loop + bulk cap enforcement | 3 | C-4(wired), C-7 | 35,57,58,38 |
| 60 | Audit journal | 3 | C-6, SEC-4 | 59 |
| 61 | Agent-turn UI | 3 | C-5(UI), C-5a(UI), C-6(UI) | 57,59,60 |
| 62 | C-10 manual model override | 3 | C-10 | 52,37 |
| 63 | I-1 inline AI | 3 | I-1 | 13,34 |
| 64 | I-2 auto-tagging | 3 | I-2 | 63 |
| 65 | I-3 auto-linking | 3 | I-3 | 27 |
| 66 | I-4 prompt templates | 3 | I-4 | 36,63 |
| 67 | Dashboard scheduling | 3 | D-2 | 47 |
| 68 | Digest computation | 3 | D-1(digest) | 34,67 |
| 69 | Topic clusters + labeling | 3 | D-1(clusters), D-6 | 27,52,67 |
| 70 | Action items + parsed reminders | 3 | D-1(actions+reminders), D-7 | 34,18,67 |
| 71 | Dashboard screen | 3 | D-3, D-5 | 68,69,70 |
| 72 | Digest notification delivery | 3 | D-4 | 68 |
| 73 | **Phase 3 gate** | 3 | C-4…C-6,C-10,I-1…I-4,D-1…D-7,SEC-4 | 57–72 |
| 74 | Google Keep Takeout importer + UI | 4 | N-13 | 8 |
| 75 | `full` flavor subscription adapters | 4 | P-3 | 32 |
| 76 | Flavor finalization, CI matrix, README, LICENSE | 4 | NF-5, NF-7 | 2,75 |
| 77 | **Final gate** | 4 | ALL 78 | 74,75,76 |
