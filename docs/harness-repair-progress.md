# Harness repair ledger

User-approved scope: every confirmed finding in `harness-audit-2026-10-18.md`.
Plan: `superpowers/plans/2026-10-18-harness-repair.md`.

- Start: latest implementation worktree `codex/news-digests`, base b1e4028. Unrelated private-audio/build/AppShell/DeviceCheck changes remain in place.
- Ruling: use the written audit as the approved specification; the user explicitly requested implementation of all routes. Carry out the plan continuously rather than ask approval again for each repair.
- Ruling: user explicitly permits the existing executor's end_conversation tool (18 October reply). Remove the unsupported canonical-text shortcut; do not add ASR, a second Live call, or per-fragment cloud classification.
- Installation gate: no worker may install; primary verifies latest UI source/hash and an idle device before a streamed update. No production changes installed yet in this repair.

## Implemented and host-reviewed

- A1–A6: ab23798; blocked main-thread mutations cancelled, post-OPEN ADB uncertainty never falls back, media cancellation checked after reads, editor identity/password guards, node recycling, pinned current-app search. Ten Android-backed host suites reproduce original failures and pass repaired sources.
- V1/V3/V4/V5: 3e761c0; revision-keyed auth refresh, invalid-account cleanup, close-safe queued mute, bounded wake retries. V2: deeb3f3 plus call token checked after auth and before cancellable Live POST.
- D1–D6: 849c410 and review follow-up3ca8fae; delivery-aware reminders/recovery/backlog/cancel, bounded note metadata/read, newest-aware summary. Review additionally caught API33 String.isBlank and stale edited reminder snapshots; both regressions now pass on API31-compatible code.
- Shared H1–H7: original speech reaches semantic tools, no canonical close/media phrase routing; bounded current-call snapshots, per-task cancellation/identity/receipts, model task_cancel/status/result, actual HTTP disconnect, failed delivery surfaced without replay. Final tasks require receipt-backed outcomes after mutation. Current implementation is pending final integration/device review.
- No additional Live session, recognition engine or per-fragment classifier added. Concurrent delegated work uses existing Agent HTTP; max8 workers with immediate admission, bounded single-call action serialization and explicit busy response instead of a stale queued action. This is a bound, not unlimited concurrency.
- Review fixes: snapshots stop before a known earlier request; terminal tasks no longer impose a lifetime128-admission ceiling; same-turn aliases reuse receipts; cancellation checked for every result chunk.

## Verification limits

Actual device audio, repeated semantic closing, navigation/HOME and latest UI must pass after the immutable build. Host transport/counters do not prove recognition. Reminder broadcast/process survival during asynchronous long delivery remains a device lifecycle concern; interrupted claims recover as unconfirmed without replay.

## Frozen integration gate

Source snapshot: /home/gleb/.cache/tvgpt-builds/harness-final-lbdamv4v (source-manifest.json, zero source differences when checked). Latest six-section Digests UI and all concurrent imagegen/model work retained. APK e7f04bb37dcabbe1b1889e43575bfd19a0ad0eeb932df884f176869d3814f30e; ARM32 only; original private cue hash matches.

Full host suite, APK and instrumentation builds, format check and npm quality passed in this snapshot. Quality81/100,0 errors,15 warnings:8 size/complexity and7 canonical subscription URL indicators; no token-bearing arbitrary endpoint override introduced. Final review corrections cover nonblocking action admission, alias claims/terminal cancellation, stale failed evidence and socket false/delivery_failed. Delivery failure closes the unusable call quietly without re-executing an action. No device pass claimed yet.

## Device follow-up

Installed final v2 artifact e32d81d829c8b318bd3dfc958fee8c3c989fb5b439fa3c836be96516fe23af9d; UID/account preserved. Device gate details and honest limits are in harness-device-results-2026-10-18.md. A real search exposed missing visible receipt IDs; metadata plus explicit task_result instructions repair it, with host regression and accepted observed outcome in a second real search. Native HOME/close and isolated SQLite pass. Two real speech calls are pending user confirmation; no further updates during that check.
