# All-route harness repair implementation plan

> For agentic workers: use subagent-driven-development for independent repair domains; the primary agent implements the shared orchestration and integration. No worker installs or controls the TV. Every task requires a failing reproduction, passing checks, scoped commit, and review.

**Goal:** Repair all confirmed H/A/V/D findings in the user-approved all-route audit while preserving Android TV startup, native MIC16k WebRTC, the latest six-row Digests UI, and account/data.

**Spec:** `docs/harness-audit-2026-10-18.md`, approved for implementation by the user. The user additionally permits the existing Agent HTTP/tool executor to invoke end_conversation; no extra Live/ASR session or per-audio-fragment classifier.

**Architecture:** Live speaks and delegates the original request. A call-owned task harness snapshots bounded current conversation/state, tracks request/action identity, supports model-chosen cancellation and structured outcomes, and feeds verified results back to the same Live call. Mutations and I/O have explicit cancellation scopes. Existing native audio and feature repositories remain their owners.

## Constraints

- Work in existing latest `codex/news-digests`; preserve unrelated uncommitted private-audio/public-release changes. No GitHub, containers or deployment.
- No UI layout or microphone/audio mode changes; preserve current six sidebar sections, 87dp orb, startup cue, HOME behavior and fresh-call isolation.
- Never derive intent from local language dictionaries. Do not treat handoff input_transcript as generated command text.
- Call cancellation must close cancellable I/O, prevent queued undispatched mutations, and preserve uncertainty for already-dispatched operations.
- No blind post-dispatch retry, no false success from sent/queued state, no automatic old-memory replay.
- Existing background digest jobs survive call closure and retain epoch/revision/idempotency protection.
- TV updates only after all host/build/review gates, using immutable named/hash-verified latest APK and --no-incremental -r, retaining UID/account. Do not interrupt an active call.

## Frozen shared interface

`public final CancellationToken`: `public static final NONE`, `public CancellationToken()`, `boolean cancelled()`, `void check()` (CancellationException, also honors thread interruption), `boolean cancel()`, `Registration onCancel(Runnable)`; `Registration.close()` unregisters without throwing. Cancellation callbacks run outside the token lock. NONE is immutable. No Android dependency.

`TvTools(Context,CancellationToken)` supplements the existing constructor. AdbRemote adds `shell(Context,String,int,CancellationToken)`; existing signatures remain compatible. Main owns token/Http; TV worker owns propagation into Android/ADB/media.

## Tasks

- [x] 1. Shared call/task harness (primary): bounded current-context snapshot tied to delegation/turn ID; remove canonical phrase/media transcript shortcuts; per-task cancellation, distinct running-task IDs and semantic task_cancel/status tools; terminal results/outcome/evidence; delivery errors surfaced without duplicate mutation. Retain ordinary pure Live dialogue and fresh-call startup.
  - RED: current spoken context missing from Agent; mirrored/same-turn requests duplicated; cancellation waits behind work; late output from cancelled task reaches Live; result send false ignored.
  - GREEN: introduce pure tested context/task/cancellation/result units, integrate Agent/LiveConnection, cancellable Http with checks before remote call creation. Existing end tool is the only semantic closing action.
- [x] 2. TV actions (TV worker): A1–A6, cancellation scope for ADB/media/UI queues; dispatch-phase exception prevents fallback after uncertain send; scoped input identity/password guards; node recycling; pin current search target.
  - RED/GREEN: blocked mutation queue, post-dispatch transport error, cancelled media read, editor replacement/password, changing current package. Keep old constructors and tool contracts compatible.
- [x] 3. Data/background (data worker): D1–D6; delivery-aware reminder state, cancel-vs-dispatch linearization, process recovery without replay, backlog scheduling; bounded/paged knowledge; latest-aware summary truncation. Preserve digest positive controls.
  - RED/GREEN: delivery failure/overflow/standby; cancel-after-claim; process restart in-flight; >200 due; 100 max notes; huge first/decisive last summary turn.
- [x] 4. Auth/voice lifecycle (voice worker): V1,V3,V4,V5; single-flight refresh with logout revision protection; inert-orb preflight closes/fails quietly; mute cannot be discarded by close; bounded wake recovery without restart after explicit stop/logout. Main handles V2 cancelled Live POST.
  - RED/GREEN: concurrent expired auth; no account; queued mute then close; transient recorder failure; no revival after close/logout. Never change native capture format/AEC/NS/audio mode.
- [x] 5. Integration and review (primary): wire every registered tool through the appropriate context/cancellation/outcome contract; align concise Live prompt, Agent instructions and runtime skills; update inventory/AGENTS/runbook. Review all domain commits and full shared flow. No task may be marked complete merely because its helper tests pass.
- [ ] 6. Verification and device gate (primary): npm ci --ignore-scripts; npm run quality; bash scripts/test.sh; format check; ARM32 APK + instrumentation builds. Verify source snapshot still matches newest UI and private cue; hash/cert/decompile checks. On TV verify first ordinary call after restart, repeated fresh calls, semantic closing in repeated calls, HOME, cancel/late actions, safe search/media and restored volume, archives. Record actual results and any remaining limitations. No fake startup/check prompt.

## Review focus

- Cancellation racing auth/HTTP/ADB dispatch or Android main/native queues must not invent certainty or permit a queued mutation.
- A user clarification may refer to prior nondelegated speech; include current-call context without archived/future data.
- RTC and sideband mirrors are transport duplicates; separate deliberate user requests must remain possible.
- Background summaries/reminders/digests must not inherit cancelled call ownership or resurrect deleted/logged-out data.
- Current UI and real first-call microphone reception are device gates, not consequences of a successful compile.
