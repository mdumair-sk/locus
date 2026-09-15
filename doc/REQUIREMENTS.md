# Locus — Requirements Document
 
| | |
|---|---|
| **Product** | Locus — AI-native notes for Android |
| **Version** | 1 |
| **License** | Apache-2.0 (see NF-7) |
| **Distribution** | Open-source on GitHub; personal sideloaded use; no Play Store target |
| **Name rationale** | *Locus* — the "place" in the method of loci / memory palace: an external place where memory lives |
 
---
 
## 1. Product summary
 
Locus is a single-user Android notes app built on plain markdown files, with a local-first RAG engine, an AI agent that can query **and** organize notes, a scheduled AI dashboard, and total AI-provider freedom: any cloud API, subscription-based logins, or fully on-device models. English-only for v1.
 
**One-liner:** your notes, with a memory and a librarian.
 
## 2. Goals & non-goals
 
**Goals**
- G-1 Keep-class capture speed with zero lock-in (open markdown format)
- G-2 Trustworthy AI over notes: mandatory citations, scoped retrieval, hybrid search
- G-3 Provider freedom: OpenAI-compatible APIs, Anthropic, Gemini, subscription logins, fully offline
- G-4 Agentic organization (write/organize) behind safety rails
- G-5 A dashboard that earns screen space: scheduled digests, clusters, action items, reminders
- G-6 Model recommendations that track the local-LLM ecosystem via a repo-hosted catalog, staying current without requiring app releases
**Non-goals (out of scope, v1)**
- Multi-user, accounts, any backend server
- Device-to-device sync (import/export instead)
- Voice memos / audio transcription
- Images, OCR, handwriting
- Non-Latin language support
- Play Store policy compliance (enables e.g. `USE_EXACT_ALARM`)
## 3. Notes core `[P0]`
 
| ID | Requirement |
|---|---|
| N-1 | Notes are markdown files with YAML frontmatter. **The file is the source of truth**; the local database is a rebuildable index and may be deleted/recreated at any time. Write ordering enforces this: in-app edits go keystrokes → dirty in-memory buffer → debounced atomic flush to the `.md` file (the commit point) → DB/FTS/embedding update enqueued only after the flush succeeds. The DB may lag the file; it must never lead it. Forced (non-debounced) flush triggers: editor close, `onStop`, immediately before backup/export (N-11), and immediately before any agent write touching that note (C-4), so the editor buffer can never clobber a journaled AI edit. |
| N-2 | Hierarchy = filesystem folders. Folder membership is never stored inside the file, so external moves are always honored. Filename collisions within a folder (from external tools, restores, or concurrent creation) are resolved by auto-suffixing (`Title (2).md`, `Title (3).md`, …), matching common filesystem convention; the note's UUIDv7 `id` in frontmatter remains the true identity regardless of filename. |
| N-3 | Frontmatter fields: `id` (UUIDv7), `title`, `type` (`note` \| `checklist`), `created`, `modified`, `pinned`, `color`, `tags`, `history` (count), `checksum` (body hash), `app`. **Unknown frontmatter keys are preserved verbatim on rewrite.** |
| N-4 | Note types v1: markdown text note; checklist using GitHub `- [ ]` task syntax. |
| N-5 | Tolerant parser: malformed/hand-edited frontmatter is repaired (fields derived from file metadata, title from first heading). No data-loss failures on parse. |
| N-6 | Two navigation views, both first-class: Keep-style card grid (pin, 8 colors) and folder tree. |
| N-7 | Editor: markdown-native — source editing with formatting toolbar, syntax highlighting, interactive checkboxes, and a rendered-preview toggle. |
| N-8 | Trash with restore; deleting moves files to an app-managed `.locus/trash/` folder, never hard-deletes directly. |
| N-9 | Version history: each overwrite snapshots the previous body to `.locus/history/<noteId>/`; cap 20 revisions/note (FIFO). |
| N-10 | External edits (e.g., Obsidian, desktop editors) are detected via checksum-diff rescan on app open and manual refresh. |
| N-11 | Scheduled auto-backup: zips the entire notes tree to a user-chosen location (default weekly; on-demand available). |
| N-12 | Import/export: full-library zip export; import restores it. Settings export excludes API keys by default; key inclusion requires explicit opt-in. |
| N-13 | Google Keep importer: user supplies a Google Takeout zip; JSON/HTML converted to Locus notes preserving text, checklists, pins, labels, colors, timestamps. |
 
## 4. Search & retrieval `[P0–P1]`
 
| ID | Requirement |
|---|---|
| S-1 | Hybrid search: keyword (FTS) + semantic (vector) results fused by Reciprocal Rank Fusion. Blended into the normal search experience with no separate "AI search" mode. |
| S-2 | Embeddings are computed **on-device, always**. Default model: embeddinggemma-300m (Q8, 512-d). |
| S-3 | Chunking: heading-aware, ~500 tokens with ~15% overlap; each chunk carries note id, title, and heading path (used for citations). |
| S-4 | Incremental indexing: unchanged notes (checksum match) are never re-embedded; changing the embedding model triggers automatic re-embed. |
| S-5 | Query scoping: folder(s), explicit note selection, and/or time range; **default scope = all notes**. Scopes filter both retrieval paths. |
| S-6 | Graceful degradation: if the vector index is unavailable/incomplete, search falls back to keyword-only. |
| S-7 | Citations are **mandatory** on all RAG answers: inline `[n]` markers plus a Sources block; each citation is tappable and navigates to the note. |
| S-8 | Retrieved context is packed within the target model's context length (top fused chunks, deduped per note). |
| S-9 | Scale target: brute-force cosine over up to ~50k chunks is acceptable for v1 (confirmed sufficient for expected personal-scale usage — roughly 25M tokens of notes before degradation); no ANN library dependency. |
 
## 5. AI chat & agent `[P1–P3]`
 
| ID | Requirement |
|---|---|
| C-1 | Streaming chat with multiple named sessions and persistent history. |
| C-2 | Every chat can query the note library via retrieval; per-query scope selection (default all notes). |
| C-3 | Read tools: `search_notes`, `read_note`, `list_folders`. |
| C-4 | Write tools: `create_note`, `update_note`, `append_to_note`, `move_note`, `tag_note`, `merge_notes`, `trash_note`, `create_folder`, `set_reminder`. |
| C-5 | Safety tiers apply to **agent-originated intent** (user's own chat instructions), not to instructions discovered in retrieved content: **read** → auto-run. **create/append** → auto-run with visible diff + one-tap undo. **update/move/tag** → preview + confirm. **delete/merge/bulk** → always confirm. |
| C-5a | **Prompt-injection guard:** retrieved note content is untrusted input. If text sourced from a note (not the user's own message) appears to direct a tool call — e.g. an instruction embedded in a note body — that call is always routed to explicit user confirmation regardless of which C-5 tier it would otherwise fall under, including read-tier actions if they were triggered this way. The agent must not treat note content as a command channel. |
| C-6 | Every AI write is recorded in an audit journal (what, when, which model, diff), viewable and exportable. |
| C-7 | Bulk operations are capped at 50 notes per agent run by default; the cap is user-editable in settings. |
| C-8 | Providers without native function-calling (including local models) work through a JSON-mode tool loop. |
| C-9 | Any chat answer can be pinned as a new note (citations preserved as links). |
| C-10 | Chat defaults route automatically (see P-4); the user may manually override the model per conversation. A mid-conversation manual switch from a local to a cloud model triggers the SEC-5 warning before any note content is transmitted. |
 
## 6. AI on notes & note intelligence `[P3]`
 
| ID | Requirement |
|---|---|
| I-1 | Inline AI: select text → summarize / rewrite / translate / extract tasks; whole-note actions in the editor menu. |
| I-2 | Auto-tagging: suggested (and user-approved) tags written to frontmatter. |
| I-3 | Auto-linking: related-notes suggestions rendered as backlinks; tap to navigate. |
| I-4 | Prompt templates: user-defined reusable prompts/commands available in chat and inline actions. |
 
## 7. AI dashboard `[P3]`
 
| ID | Requirement |
|---|---|
| D-1 | Dashboard cards: **daily/weekly/monthly digest**, **auto-discovered topic clusters**, **cross-note action items**, **parsed reminders**. |
| D-2 | Computation runs on a user-configured schedule at regular intervals (default: nightly; heavy jobs constrained to charging/Wi-Fi by default). |
| D-3 | Every card is actionable: digest item → jump to note; action item → convert to task/note; cluster → open filtered view. |
| D-4 | Digest is delivered as a notification as well as a dashboard card. |
| D-5 | Each card type can be enabled/disabled independently, with "compute now" triggers. |
| D-6 | Clusters are computed from note embedding centroids; labeling is an LLM task and follows the standard P-4 routing table (local utility model by default, with the same cloud-upgrade/fallback chain as digest/tagging) rather than requiring a cloud model. |
| D-7 | Smart reminders: dates/times parsed from note content become reminders (see §8) with a link back to the source note. |
 
## 8. Reminders & alarms `[P0]`
 
| ID | Requirement |
|---|---|
| R-1 | Both exact alarms and inexact reminders are supported; the user accepts the associated permissions. |
| R-2 | Permission ladder with graceful degradation: exact alarm → inexact window (±10 min) → WorkManager. Scheduling never hard-fails. If a permission required by an already-scheduled **repeating** reminder is revoked, the reminder re-arms at the next available lower tier going forward and the app surfaces a one-time notification/banner informing the user of the downgrade (not a silent change). |
| R-3 | Permissions declared: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. |
| R-4 | All active reminders re-arm after device reboot. |
| R-5 | Reminders appear as a grouped notification with **Complete** (ticks the checkbox in the source note via the repository) and **Snooze** actions. |
| R-6 | Repeat options: none, daily, weekly, monthly. |
 
## 9. AI providers `[P1–P2]`
 
| ID | Requirement |
|---|---|
| P-1 | Three adapter families, each declaring capabilities (tool support, context length, pricing): OpenAI-compatible, Anthropic Messages, Gemini. |
| P-2 | OpenAI-compatible adapter supports any base URL → covers OpenAI, OpenRouter, Groq, Together, DeepSeek, Mistral, xAI, Ollama, LM Studio, and future providers without code changes. |
| P-3 | Subscription-based logins (Codex, Claude Code OAuth, Gemini/Antigravity) are supported as **experimental**, isolated behind a build flavor/flag within the same public repository (code is visible in the OSS source, just not built into the default artifact), with a ToS disclaimer in the README. They may break at any time without regressing the core app. |
| P-4 | **Automatic routing** by task type with capability matching and fallback chains (chain always ends at the local model): |
 
| Task | Default | Upgrade | Fallback |
|---|---|---|---|
| Digest / tagging / cluster labels | Local utility model | Cheap cloud model | Local chat model |
| Chat / RAG Q&A | Local chat model | User's strong cloud model | Local chat model |
| Agentic multi-step | Strongest available cloud | — | Local chat model |
 
Any fallback that transitions an active conversation from a local to a cloud model mid-conversation is gated by the SEC-5 user confirmation before transmission.
 
| ID | Requirement |
|---|---|
| P-5 | Usage & cost tracking per provider: tokens in/out, editable price table, monthly summary view. |
| P-6 | API keys and tokens encrypted at rest (AndroidKeyStore-wrapped key); excluded from backups/exports unless explicitly opted in. |
 
## 10. On-device AI & model management `[P2]`
 
| ID | Requirement |
|---|---|
| M-1 | Runtime: llama.cpp via JNI, one runtime serving both chat and embeddings; CPU-first. |
| M-2 | **Full offline fallback**: chat and retrieval-embedding function with zero network when local models are selected; local models also serve as the privacy lane. |
| M-3 | Model manager: browse/search Hugging Face for GGUF, quantization picker, resumable downloads, storage usage stats, deletion, per-model user notes/ratings, and one-tap "load & benchmark" storing measured tok/s. |
| M-4 | Any GGUF can be loaded; the app never hardcodes or restricts models. |
| M-5 | Unified model registry: local and cloud models appear in one picker with context length, capabilities, price, and an offline badge for local. The recommendation catalog is a versioned JSON file (`catalog/models.json`) hosted in the main GitHub repo (no backend, no separate repo), decoupled from app releases: the app bundles a snapshot for first-run/offline use and refreshes the live file at launch (with a periodic fallback check). |
| M-6 | Recommended defaults (efficient, English-strong): **Qwen3-4B** (chat/agent), **Qwen3-1.7B** (utility), **embeddinggemma-300m** (embeddings) — swappable via the registry, never baked in. These are the seed entries of the catalog snapshot (M-8); the catalog, not the app binary, is the source of future default recommendations. |
| M-7 | Sustained-generation thermal warning when throttling is likely. |
| M-8 | Dynamic recommendations: the catalog carries per-task recommendation sets (chat / utility / embeddings) and P-4 routing defaults, so they update without app releases. Every entry pins the exact Hugging Face repo, filename, and SHA-256; downloads are integrity-checked and a checksum mismatch blocks loading. Recommendations surface as dismissible suggestions in the model manager and **never** switch models automatically. |
| M-9 | Recommendation ranking is device- and task-aware: RAM/storage headroom gates candidates; measured tok/s from M-3 benchmarks ranks equally-eligible entries; capability matching (tool support, context length) follows P-4 needs. Switching the embedding model requires explicit confirmation showing the estimated full re-index time (S-4). |
 
## 11. Privacy & security
 
| ID | Requirement |
|---|---|
| SEC-1 | Embeddings and the vector index never leave the device. |
| SEC-2 | Note content (retrieved chunks) is sent to a cloud provider only when a cloud model handles that request; selecting a local model yields a fully local pipeline. The UI makes it visually obvious which is active. |
| SEC-3 | All secrets encrypted at rest via AndroidKeyStore; never logged. |
| SEC-4 | Audit journal exportable; all AI modifications reversible per §5 safety tiers. |
| SEC-5 | **Local↔cloud transition guard:** when an active conversation switches from a local model to a cloud model — whether by manual override (C-10) or automatic fallback routing (P-4) — the app must show an explicit warning identifying the cloud provider and requiring user confirmation before any note content is transmitted. The passive SEC-2 active-model indicator is necessary but not sufficient. |
 
## 12. Platform & non-functional requirements
 
| ID | Requirement |
|---|---|
| NF-1 | Native Kotlin; single-Activity Jetpack Compose UI; Material 3. |
| NF-2 | minSdk 31 (Android 12); target latest stable Android. |
| NF-3 | Primary benchmark device: Snapdragon 8 Elite, 12 GB RAM. Performance targets: cold start to notes list < 1 s at 2,000 notes; typical keyword search < 100 ms; hybrid retrieval < 500 ms; checksum rescan of 1,000 notes ≤ 2 s; local 4B Q4 generation ≥ 10 tok/s. |
| NF-4 | English-only UI and AI prompting for v1. |
| NF-5 | Open-source on GitHub with CI builds; two build flavors: public OSS build and a `full` build adding subscription adapters. |
| NF-6 | Data durability: note saves are flushed to disk immediately; version snapshot precedes any destructive edit; database self-heals from files at any time. |
| NF-7 | License: Apache-2.0, applied from first public release. Subscription-adapter code lives in a cleanly separated source set powering the `full` flavor (NF-5) — publicly visible per P-3, but structurally excisable so the permissive core remains standalone. |
| NF-8 | Catalog discovery bot: a scheduled GitHub Action (weekly, no backend) queries the Hugging Face API for new GGUF models, filters by verified authors, license, required quant files, and minimum traction, and opens a draft PR against `catalog/models.json` with pre-filled metadata; human merge is required before any entry ships to clients. The `catalog/` path is protected via CODEOWNERS requiring maintainer review; trusted contributors may open catalog-only PRs alongside the bot, subject to the same review-and-merge bar. Catalog PRs must not modify code outside `catalog/`. |
 
## 13. Explicitly deferred (post-v1 candidates)
 
Quick capture surfaces (share sheet, quick-settings tile, homescreen widget) · voice memos + Whisper transcription · images + OCR + multimodal chat · device sync · WYSIWYG editor · non-Latin language support · GPU/NPU acceleration experiments.
 
## 14. Roadmap (phases define build order; requirements tagged accordingly)
 
| Phase | Scope | Definition of done |
|---|---|---|
| **0 — Notes** | N-1…N-12, R-*, S-1 keyword half | A daily-usable notes app: files on disk, editor, grid/tree, search, trash/history, backup, reminders |
| **1 — RAG chat** | S-*, C-1…C-3, C-7…C-9, P-1…P-2, P-4 (partial) | Ask your notes from cloud providers with streaming, scoping, mandatory citations |
| **2 — Local AI** | M-* (incl. M-8/M-9), NF-8, P-4 full, P-5…P-6, SEC-5 | Model manager + registry; fully offline chat works end-to-end; auto-routing with fallbacks; SEC-5 local→cloud switch warning enforced on both fallback and manual override; cost tracking; catalog refresh and recommendation flow verified end-to-end |
| **3 — Agent + Dashboard** | C-4…C-6, C-10, I-*, D-* | AI can safely organize notes; dashboard computes on schedule and is actionable |
| **4 — Release** | N-13, NF-5, NF-7, P-3 | Takeout importer, subscription flavor, README/disclaimer, public GitHub release under Apache-2.0 |
