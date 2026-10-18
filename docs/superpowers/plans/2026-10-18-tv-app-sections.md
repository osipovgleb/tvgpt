> UI amendment,18 October: use [interface-contract.md](../../ui/interface-contract.md). Direct Appearance/Access sidebar, no Home/Settings wrapper, horizontal option rows, right-side windows. Earlier Task1 route/file names below are historical. No new TV deployment is authorized.

# TV GPT app sections implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. This side conversation forbids subagents; implementation and final review run inline.

**Goal:** Turn the launcher into a native TV app with separate settings, personalised voice/orb, dated conversation history, editable shared knowledge and device-local reminders that wait for the TV to be on.

**Architecture:** A native navigation shell hosts separate screens. Typed preferences configure the existing native voice transport; SQLite repositories store discussion records, notes and scheduled tasks. AlarmManager/JobScheduler and screen-on callbacks drive delivery without a polling service or desktop dependency.

**Tech stack:** Java 8 source / Android API29 minimum and API31 target, built-in SQLite/AlarmManager, ARMv7 WebRTC, existing subscription protocol; no new product runtime dependencies.

**Spec:** `docs/superpowers/specs/2026-10-18-tv-app-sections-design.md` (approved structure; user selected defer reminders until next screen-on).

## Global constraints

- TCL Android12 / ARM32; native audio-only WebRTC and system VoiceInteractionService remain.
- Popup contains only the animated 87dp orb; no panel/text/buttons and no spoken greeting. Preserve the exact voice-start.ogg cue.
- Stable working context rotates only after 12 hours of inactivity. Neither voice preview nor app navigation resets it.
- Existing account, local ADB key, settings and context survive APK updates with the existing signing certificate.
- No desktop runtime, WebView, global music-volume changes, arbitrary scheduled shell commands, GitHub or container/deployment commands.
- Reminders do NOT turn the screen on or speak while it is off. They wait until the next interactive state.
- New code is split by feature responsibility; do not put every screen/tool/repository into MainActivity or Agent.
- No extra constant polling loop, loaded archive on every audio frame, or one network summary request per token.

## Review focus

1. Close/reopen/remote invocation during voice preview must release preview resources and never revive a closed microphone or change the working context.
2. Unknown voice, network failure or logout during preview/summary must preserve working defaults and not resurrect cleared account/history.
3. Duplicate final transcript events and legacy data without timestamps must not duplicate discussions or invent exact times.
4. Timer/recurrence across clock changes, DST, reboot and a screen-off due date must not fire early, wake the screen or replay every missed occurrence.
5. Remote focus, existing access/setup actions and local goodbye must survive the navigation refactor and APK update.

## Task 1 — Native navigation and technical settings

**Files:** modify `app/src/local/tvgpt/assistant/MainActivity.java`; create `AppNavigation.java`, `TechnicalSettingsView.java`, `ui/AppShell.java`, `ui/HomeScreen.java`, `ui/SettingsMenuScreen.java`; extend `SettingsGlyph.java`; adapt `tests/android/DeviceCheck.java`; add `tests/AppNavigationTest.java` and test runner entry.

**Interfaces:** `AppNavigation.Route` contains HOME, CONVERSATIONS, KNOWLEDGE, REMINDERS, SETTINGS, ACCESS, APPEARANCE, AUDIO. `AppNavigation.open(Route)` and `back()` return the resulting Route; `back()` at HOME returns null to finish. `TechnicalSettingsView(Activity, Runnable openAssistant)` owns existing account/setup/pairing views and exposes `refresh()` / `permissionsResult(int,int[])`. `AppShell.show(Route,View)` updates selected menu and content; navigation callbacks never directly create voice resources.

- [ ] Add failing navigation tests for nested→section→home→exit and `setup=true` opening ACCESS.
- [ ] Run `bash scripts/test.sh`; observe the new class/test fail before implementation.
- [ ] Extract existing settings actions without changing assistant configuration, inline remote pairing, account logout or permission flow. Implement dark native sidebar/home; every menu item opens a distinct real screen (subsequent feature screens may show an honest empty state until its task).
- [ ] Run host tests + ARM32 build. On device check D-pad focus, back navigation, permission/setup entry and native assistant invocation; preserve the existing close-ui smoke scenario.
- [ ] Commit the independently working menu/settings refactor.

## Task 2 — Typed preferences and 12 orb colours

**Files:** create `settings/AssistantPreferences.java`, `settings/OrbPalette.java`, `ui/AppearanceScreen.java`; modify `VoiceOrb.java` and relevant app routing; add `tests/OrbPaletteTest.java`.

**Interfaces:** `AssistantPreferences(Context)` reads/writes theme ID, voice ID and speech preset with validated defaults. `OrbPalette` exposes immutable IDs/labels/ARGB colours; `colour(String)` falls back to BLUE. IDs: sky, blue, turquoise, mint, green, lime, yellow, amber, orange, pink, violet, silver. One texture and cached per-theme colour filters; no twelve animation loops.

- [ ] Add failing tests: exactly12 distinct IDs, stable blue default, corrupted/unknown preference fallback, finite colour transform preserving neutral cloud whites.
- [ ] Observe RED via host suite; implement palette/preferences and a single selected-theme animated preview.
- [ ] Apply theme without allocating graphics per frame or changing the 87dp overlay/focus behavior. Selection survives process restart.
- [ ] Run tests, quality, build and inspect all12 previews on TV; commit.

## Task 3 — Voice selection, previews and six real speech presets

**Files:** create `settings/SpeechPreset.java`, `settings/VoiceCatalogue.java`, `ui/AudioSettingsScreen.java`, `voice/VoicePreview.java`; introduce a bounded voice-session options object and shared call-signalling helper as required; modify `LiveConnection.java`, `NativeWebRtcTransport.java`, `NativeAudioDevice.java`, `VoiceSession.java`; add `tests/SpeechPresetTest.java` and preview lifecycle tests.

**Interfaces:** presets NORMAL, QUIET, FAST, CALM, DETAILED, ENERGETIC; NORMAL default, current working voice `cove` retained. Presets expose instructions and `outputGain` (QUIET=.35, others=1). `VoicePreview.start(String voiceId, SpeechPreset, Listener)` / `stop(Runnable)` is playback-only, tool-free, absent from history and mutually exclusive with live capture. A successful preview can persist a verified voice; an unsupported option or network error cannot change the current selection.

- [ ] Add RED tests for quiet gain affecting only the remote AudioTrack, unknown preset fallback, preview cancellation/late callbacks, no history/tool access and unchanged workspace identity.
- [ ] Implement voice preset instructions and remote-track `setVolume` gain. Do not change the TV music stream. Apply voice choice on the next Live session; do not send unsupported session.update fields.
- [ ] Verify voice names through the actual subscription endpoint/preview path. Persist only successfully previewed names; distinguish unsupported voice from network/auth failure. Keep cove working on every failure. Never substitute the public Realtime API or require an API key.
- [ ] Build playback-only native WebRTC preview with no microphone track/capture. Short previews are on demand, cancellable and bounded; cache valid clips when practical without restoring the old audio-input bridge.
- [ ] Verify on TV: audible previews, no active recording from preview, quiet gain, each preset instruction, normal remote-button/wake invocation after stopping preview. Run suite/quality/build; commit.

## Task 4 — Durable dated discussion archive

**Files:** create `data/AppDatabase.java`, `data/ConversationRepository.java`, `ui/ConversationsScreen.java`; modify `SessionStore.java`, `SessionWorkspace.java`, `LiveConnection.java` and app navigation; add host tests for transcript-event identity/time normalization and device persistence tests.

**Interfaces:** database schema version1 contains conversations and turns with foreign keys. `appendTurn(workspaceId,callId,eventId,atMillis,role,text)` deduplicates real event/turn IDs. `recent(limit,offset)` / `turns(workspaceId,limit,offset)` are paginated. Migration imports the existing current context before rotation and marks individual old turn times as unknown; archived records never alter active session grouping IDs.

- [ ] Add RED tests for duplicated sideband/WebRTC events, identical text in distinct real turns, migration without timestamps, current-context retention and persistence across reopening/12h rotation.
- [ ] Implement the repository, archive before old workspace rotation, and persist final turns only. Raw audio, partial deltas and screenshots are excluded. Avoid full archive reads/DB writes on the audio/UI thread.
- [ ] Render real date ranges, topic placeholders and a paginated transcript view. Deletion requires confirmation and does not wipe another active context by accident.
- [ ] Run host and SQLite device tests, update/install without uninstalling and verify existing context/account; commit.

## Task 5 — Discussion summaries without blocking Live

**Files:** create `data/ConversationSummarizer.java`; modify archive repository and close/turn hooks; add summary revision/cancellation tests and rendering states.

**Interfaces:** `requestSummary(workspaceId,revision)` runs after call close with a minimum60s debounce and only if new final turns exist. `saveSummaryIfCurrent(id,revision,title,summary)` does not resurrect deleted or superseded records. Requests use the existing subscription account and a bounded transcript input, no tools and no mutations to Agent history.

- [ ] Add RED tests for coalesced requests, outdated result after editing/deletion/logout, network failure and no blocking of reopening.
- [ ] Implement background summary, literal user-topic excerpt while pending, and retryable failure state. The list distinguishes generated summary from an excerpt.
- [ ] Run tests and a real subscription summary request; verify voice reopening during pending network work; commit.

## Task 6 — Editable shared knowledge and voice tools

**Files:** create `data/KnowledgeRepository.java`, `ui/KnowledgeScreen.java`, `KnowledgeTools.java`; extend the database migration, Agent tool schema/execution and Live context injection; add validation/context/race tests.

**Interfaces:** note fields id/title/text/updated/source/revision. Title<=120 characters, text<=4000, maximum100 notes; invalid input returns an explicit error while preserving editor contents. `save(id,expectedRevision,title,text,source)` protects concurrent edits; `delete(id)` and `list()` are available. Prompt context is bounded to8000 characters, presented as user data. Tool names `knowledge_list`, `knowledge_save`, `knowledge_delete` use typed JSON arguments and real persisted results.

- [ ] Add RED tests for create/update/delete, Unicode limits, stale concurrent edits, screen-text prompt injection and stale context after changes/logout.
- [ ] Implement native editing, explicit destructive confirmations, voice-requested writes and bounded context refresh. Do not autonomously harvest arbitrary screen text into memory.
- [ ] Verify manual and spoken “запомни…” persistence/readback on TV and account/context handling on logout. Run suite/quality/build; commit.

## Task 7 — Persistent scheduling rules and reminder editing

**Files:** create `reminders/Reminder.java`, `reminders/SchedulePolicy.java`, `data/ReminderRepository.java`, `ui/RemindersScreen.java`; extend DB migration; add `tests/SchedulePolicyTest.java` and persistence tests.

**Interfaces:** kinds REMINDER, TIMER, ACTION; repeats NONE, DAILY, WEEKLY with weekday mask; event fields id/body/dueEpoch/zoneId/repeat/enabled/status. Timers retain elapsed-time/boot metadata to separate duration from calendar changes. `nextOccurrence(after,zone)` is strictly future. `deliveryPolicy(interactive)` always defers when false.

- [ ] Add RED tests for past/invalid dates, negative timers, DST gap/overlap, next Tuesday21:00 in TV timezone, reboot restoration and many missed weekly occurrences producing one pending item.
- [ ] Implement typed schedules, SQLite repository, creation/edit/disable/delete and display of original due time. No raw cron syntax is required.
- [ ] Run tests and native editing checks; commit.

## Task 8 — Local delivery, timers, repeats and voice scheduling tools

**Files:** create `reminders/AlarmScheduler.java`, `reminders/ReminderReceiver.java`, `reminders/BootReceiver.java`, `reminders/ReminderDispatcher.java`, `ReminderTools.java`; modify manifest and assistant/screen-on hooks; extend Agent tool schemas; add receiver/reconciliation tests.

**Interfaces:** `reconcile(Context)` restores active schedules; `onDue(id,now)` persists pending work; `onInteractive()` delivers pending reminders idempotently. Use non-screen-waking alarm types; do not set TURN_SCREEN_ON, send KEYCODE_WAKEUP or acquire a screen wake lock. Special exact-alarm access is checked before scheduling; unsupported access is shown in technical settings. `reminder_create`, `timer_create`, `reminder_list`, `reminder_cancel` accept validated arguments. Repeating action tasks execute existing allowed TV tools, never model-provided shell strings.

- [ ] Add RED tests: due while non-interactive never speaks/opens UI/actions; screen-on delivers once, cancelled/disabled events do not deliver, interrupted action execution is not blindly replayed, reboot/clock-change does not create a burst.
- [ ] Implement AlarmManager/JobScheduler/boot/screen-on callbacks without polling. Persist delivery outcomes and show pending/failed/completed states. Summarise multiple delayed events instead of replaying all old repeats.
- [ ] Add the current date/timezone to scheduling prompts. Require clarification for ambiguous/missing time and return exact accepted date/time to the user.
- [ ] Verify a real short timer while TV is on, a reminder due in standby deferred until screen-on, cancellation and a recurring task. Verify notifications/voice fallback when cloud/account is unavailable; do not claim unsupported firmware wake behavior.
- [ ] Run full suite, quality and build; commit.

## Task 9 — Integration, resource comparison and update

**Files:** update `AGENTS.md`, `README.md`, device tests and performance results. Keep parallel main-checkout diagnostics intact.

- [ ] Review the whole diff independently as a separate self-review pass (subagents forbidden). Check all five Review Focus conditions and every approved menu/feature.
- [ ] Run host suite, Java format check, AI Slop, ARM32 app build and device-test build. Fix important findings with a reproducing test; list real limitations.
- [ ] Merge using local Git, rebuild with the main signing key and update the existing TV package without uninstalling. Pause installation until any active voice call ends; announce the update.
- [ ] Check remote focus/menu/back, technical access, previews and selected voice, real speech/tools, local goodbye/reopen, history/memory, on-screen timer and deferred reminder. Leave assistant bound and wake listening working.
- [ ] Repeat read-only memory/CPU sampling with the same launcher/voice state; compare against `docs/native-device-results.md`. Commit results and report exactly which cases passed.
