# Locus — Traceability Matrix

Every REQ ID from `doc/REQUIREMENTS.md`, the prompt(s) that implement it, and its acceptance check.
`SEC-1…SEC-4` and most `NF-*` IDs aren't named in the §14 roadmap table (they're cross-cutting), so
they're mapped to whichever prompt actually builds the mechanism that satisfies them — this doesn't
move any roadmap-scoped feature between phases, it just records where each property becomes true.

## Notes core (N-1…N-13)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| N-1 | 5,7,8 (gate 22) | Editing a note never touches Room until `NoteFileWriter.atomicWrite` returns success; killing the process mid-debounce leaves the `.md` file at its last flushed state, never partially written |
| N-2 | 6,8,12 | Moving a note's file externally (outside the app) is reflected on next rescan with no DB-side "move" record required; two files landing with the same title auto-suffix `(2)`, `(3)`, … |
| N-3 | 3,4,5,6 | Round-tripping a note through parse→render preserves an injected unknown frontmatter key verbatim |
| N-4 | 13,16 | Creating a checklist note renders GitHub `- [ ]` syntax as tappable checkboxes |
| N-5 | 4 | A hand-corrupted frontmatter block (bad YAML) still opens with body intact and `wasRepaired=true`, never a parse exception surfaced to the user |
| N-6 | 11,12 | Both Grid and Tree are reachable from the same nav root; pin/8-color set are visible in Grid |
| N-7 | 13 | Editor round-trips source↔preview without data loss; toolbar + syntax highlighting present |
| N-8 | 14 | Deleting moves the file under `.locus/trash/`; Restore returns it to its original folder |
| N-9 | 15 | 21 sequential overwrites of one note leave exactly 20 snapshots (FIFO) in `.locus/history/<id>/` |
| N-10 | 8 | Editing a note in an external editor while the app is closed is detected via checksum diff on next app open and on manual refresh |
| N-11 | 16 | A scheduled weekly backup and an on-demand backup both produce a valid zip of the full notes tree |
| N-12 | 17 | Export→import round-trip restores the library; API keys absent from the zip unless the opt-in toggle was set |
| N-13 | 74 | A real Google Takeout zip import produces notes with text, checklists, pins, labels, colors, and timestamps preserved |

## Search & retrieval (S-1…S-9)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| S-1 | 5,9,21,26,30 (gate 40) | A query blending keyword and vector hits appears in one unified results list, RRF-ranked, with no separate "AI search" toggle |
| S-2 | 25 | Embedding generation succeeds with the device's network disabled |
| S-3 | 23 | Chunking a note with 3 heading levels yields chunks whose `headingPath` matches the ancestor chain; adjacent chunks in one section overlap ~15% |
| S-4 | 27 | Re-running indexing on an unchanged note re-embeds zero chunks; switching the embedding model triggers a full re-embed |
| S-5 | 28,30 | Scoping a query to one folder excludes hits from other folders in both FTS and vector results; default scope returns library-wide hits |
| S-6 | 29 (gate 40) | Forcing the vector index into an unavailable state still returns keyword-only results instead of an error |
| S-7 | 34 | Every RAG answer renders inline `[n]` markers and a Sources block; tapping a citation navigates to the source note |
| S-8 | 28 | A RAG answer's assembled context never exceeds the active model's context length; duplicate chunks from the same note are deduped |
| S-9 | 27 | A 50k-chunk synthetic index returns brute-force cosine results within the NF-3 hybrid-retrieval budget |

## AI chat & agent (C-1…C-10, C-5a)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| C-1 | 31,36 | Two named chat sessions retain independent, persistent history across app restarts |
| C-2 | 34 | A chat answer's retrieval scope defaults to all notes and honors an explicit per-query scope override |
| C-3 | 35 | `search_notes`, `read_note`, `list_folders` are invocable by the agent and return real repository data |
| C-4 | 58,59 | All nine write tools are callable end-to-end against a real note library |
| C-5 | 57,61 | read auto-runs silently; create/append auto-runs then shows diff+undo; update/move/tag blocks on preview+confirm; delete/merge always blocks on confirm |
| C-5a | 57,61 | A tool-call instruction embedded inside retrieved note text (not the user's message) is routed to confirmation even for a read-tier tool |
| C-6 | 60,61 | Every AI write appears in the audit journal with what/when/model/diff, and is exportable |
| C-7 | 38,59 | An agent run touching 51 notes with the default cap is blocked/truncated at 50; raising the setting raises the enforced cap |
| C-8 | 35 | A local (non-function-calling) model completes a multi-step tool task purely through the JSON-mode loop |
| C-9 | 36 | Pinning a chat answer creates a new note with its citations preserved as links |
| C-10 | 62 | Manually switching a conversation from a local to a cloud model mid-conversation triggers the SEC-5 warning before transmission |

## AI on notes & note intelligence (I-1…I-4)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| I-1 | 63 | Selecting text in the editor exposes summarize/rewrite/translate/extract-tasks; whole-note actions appear in the editor menu |
| I-2 | 64 | Suggested tags require explicit user approval before being written to frontmatter |
| I-3 | 65 | A note with high embedding similarity to another surfaces as a tappable backlink suggestion |
| I-4 | 66 | A user-defined prompt template is invocable from both chat and an inline editor action |

## AI dashboard (D-1…D-7)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| D-1 | 68,69,70 | All four card types (digest, clusters, action items, reminders) populate from a real library |
| D-2 | 67 | The dashboard computation job runs on the configured schedule (default nightly) and heavy jobs wait for charging+Wi-Fi by default |
| D-3 | 71 | Tapping a digest item opens its note; tapping an action item offers convert-to-task/note; tapping a cluster opens a filtered view |
| D-4 | 72 | A completed digest run posts both a notification and a dashboard card |
| D-5 | 71 | Disabling one card type stops its computation without affecting the others; "compute now" runs it immediately |
| D-6 | 69 | Cluster labeling follows the same P-4 local-default/cloud-upgrade/local-fallback chain as digest/tagging, not a hardcoded cloud call |
| D-7 | 70 | A date/time parsed from note content produces a reminder linking back to the source note |

## Reminders & alarms (R-1…R-6)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| R-1 | 19 | Both an exact and an inexact reminder can be created and fire |
| R-2 | 19 | Revoking exact-alarm permission on a repeating reminder re-arms it at the next lower tier and surfaces a one-time downgrade notice, without a scheduling failure |
| R-3 | 1(manifest),19 | Manifest declares `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` |
| R-4 | 19 | All active reminders re-fire correctly after a simulated reboot |
| R-5 | 20 | The reminder notification is grouped and exposes working Complete (ticks the source checkbox via the repository) and Snooze actions |
| R-6 | 18 | Recurrence calculator unit tests cover none/daily/weekly/monthly correctly, including month-end edge cases |

## AI providers (P-1…P-6)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| P-1 | 32,33 | All three adapter families report accurate capability metadata (tool support, context length, pricing) |
| P-2 | 32 | Pointing the OpenAI-compatible adapter at a non-OpenAI base URL (e.g. a local Ollama instance) completes a chat request |
| P-3 | 75 | `assembleOssDebug` never links subscription-adapter code; `assembleFullDebug` includes it and shows the ToS disclaimer |
| P-4 | 39,52 (gate 56) | For each task type, the default/upgrade/fallback chain resolves correctly and every chain terminates at a local model |
| P-5 | 53 | Token counts and cost accumulate correctly per provider after a mixed session; the price table is user-editable |
| P-6 | 54 | Provider API keys are unreadable from an unrooted export of app storage; excluded from settings export unless opted in |

## On-device AI & model management (M-1…M-9)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| M-1 | 24,41 | The same llama.cpp runtime instance serves both an embedding call and a chat-generation call |
| M-2 | 42 | With network disabled and a local model selected, chat + retrieval both function fully |
| M-3 | 43,44,45 | HF GGUF search, quant selection, resumable download, storage stats, deletion, per-model notes/ratings, and load-and-benchmark all work end-to-end |
| M-4 | 46 | Loading an arbitrary user-supplied GGUF not present in the catalog succeeds |
| M-5 | 46,47 | The picker lists local and cloud models together with context length/capabilities/price/offline badge; the catalog refreshes from the live JSON at launch |
| M-6 | 48 | Catalog seed entries resolve to real, currently-downloadable HF files with matching SHA-256 |
| M-7 | 51 | Sustained local generation on the benchmark device surfaces a throttling warning |
| M-8 | 48,49 | A checksum mismatch blocks loading; recommendations appear as dismissible suggestions and never auto-switch the active model |
| M-9 | 45,50 | Ranking changes correctly when RAM headroom or measured tok/s changes; switching the embedding model shows an estimated re-index time before confirming |

## Privacy & security (SEC-1…SEC-5)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| SEC-1 | 25 (gate 40) | No network call occurs during embedding generation, verified by traffic capture |
| SEC-2 | 37 | The active-model indicator visibly and correctly reflects local vs. cloud at all times in chat |
| SEC-3 | 54 | Secrets are absent from logcat output and from an unencrypted storage dump |
| SEC-4 | 60 | The audit journal export contains every AI-originated write with a diff, and reverting via the journal restores prior content |
| SEC-5 | 52,62 (gate 56) | Both an automatic P-4 fallback and a manual C-10 override from local→cloud block on the warning dialog before any note content leaves the device |

## Platform & non-functional (NF-1…NF-8)

| ID | Prompt(s) | Acceptance check |
|---|---|---|
| NF-1 | 1,2,3,10 | Single Activity, Compose-only UI, Material 3 theme applied app-wide |
| NF-2 | 1 | `minSdk = 31` in the version catalog and every module |
| NF-3 | gates 22,56 | Benchmark-device measurements meet cold-start <1s@2000 notes, keyword search <100ms, hybrid retrieval <500ms, checksum rescan of 1000 notes ≤2s, local 4B Q4 ≥10 tok/s |
| NF-4 | 1 (gate 22) | All `strings.xml` are English-only; no locale-qualified resource directories exist |
| NF-5 | 2,76 | CI runs `assembleOssDebug`, `assembleFullDebug`, and `test` on every push; both flavors are green |
| NF-6 | 7,15 (gate 22) | A forced flush completes before the editor screen returns control on close/`onStop`; a version snapshot exists before every destructive edit; deleting the Room DB and reopening the app fully rebuilds the index from files |
| NF-7 | 76 | `LICENSE` is Apache-2.0; subscription-adapter code lives only under `:app/src/full` and the OSS build compiles without it |
| NF-8 | 55 | The scheduled GitHub Action opens a draft PR against `catalog/models.json` with pre-filled, verified metadata; direct pushes to `catalog/` outside a reviewed PR are rejected by CODEOWNERS |

## §13 absence check

Confirmed absent from every prompt: quick-capture surfaces (share sheet/QS tile/widget), voice memos/Whisper,
images/OCR/multimodal chat, device sync, WYSIWYG editor, non-Latin language support, GPU/NPU acceleration
experiments. Re-verified line-by-line at the Final Gate (Prompt 77).
