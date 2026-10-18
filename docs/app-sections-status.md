# App sections status — 2026-10-18

UI worktree: `/home/gleb/.codex/worktrees/tv-performance/tvgpt`, branch `codex/tv-app-sections`.

The first app-sections feature build was integrated into the main branch at `f5852db` and installed. The rounded-style/no-Home revision `86b9122` was also installed before the user requested **no more deployments**. Neither proves all feature/device cases passed.

The user then authorized deployment with «выкатывай». Revision `0982dcc` (including main voice fixes through `7d153c7`) was installed on TCL at06:38:46 Moscow time on18 October. The subsequent UI revision includes: five direct sidebar destinations, distinct menu background and focus, combined Appearance colour/voice/sound rows with left/right controls, right-hand editors/confirmations/pickers, and a non-focusable top-right reminder card. On-device screenshots confirmed the distinct sidebar, combined Appearance rows and right-side timer/reminder editors. Remote right/OK opened an editor; Back dismissed it. Both secure assistant fields and actual assistant/accessibility bindings were verified. No fatal exception was found in the post-update log window. The authoritative layout is [interface-contract.md](ui/interface-contract.md); setup steps are [installation-and-development.md](installation-and-development.md).

## Verified before this UI revision

- Host tests cover native lifecycle, context reuse, navigation, preferences, data validation, scheduling/DST, summary revision/logout races and preview release ordering.
- ARM32 app and instrumentation builds and Java architecture checks passed.
- Isolated TCL SQLite/preferences checks passed: fresh schema and upgrade, event dedupe, legacy import without invented timestamps, summary/delete revision guards, note edit/delete/persistence across processes, reminder pending/claim-once/completion, cancellation invalidating stale edits, action payload preserved on editing, corrupt-preference fallback and12 theme IDs.
- Earlier native microphone speech/response and TV control worked. They are separate evidence from these new app-section features.

## Current local UI verification

Host suite passed, ARM32 APK and instrumentation APK built, Java formatting and architecture checks passed. AI Slop:96/100,0 errors,4 existing canonical OpenAI URL warnings. The final integrated host suite and ARM32 build passed before installation. UI placement was checked on TV as described above; audible voice/previews and standby delivery were not re-verified in this update.

## Remaining device checks

- Full left/right selection cycles, all pickers, preview audio, repeated focus/back paths and actual delivery of the reminder card on real TV. Basic sidebar/right-editor placement and dismissal are verified.
- Receive-only previews against this subscription, actual available voices and quiet gain. Candidates are not guaranteed voices.
- Real background summary request, voice knowledge/scheduling, timer sound, standby deferral and repeats.
- Whole voice close/reopen with current integration, microphone handover and foreground-service lifecycle.
- Matched before/after resource measurements. The existing native-conversion numbers are not measurements of these UI changes.

A foreground-start timeout occurred during one previous update/rebind. The connection script now preserves working bindings and waits for pending audio promotion before rebinding, but repeated on-device handover remains unverified. Do not claim the lifecycle issue resolved solely from a build.

Main-checkout voice edits are concurrent and remain owned by the main thread. This side work does not overwrite them. Do not install a side-branch APK that omits later main fixes.

## Subsequent UI work in this side worktree

User revisions now require Assistant settings (Appearance and Voice groups) and Settings (technical setup, one app timezone, background ducking). Both pages share the original SettingsRows implementation, not separately restyled cards. Reminder fields are inline in one right sheet; no nested date/time dialogs, no field background cards or permanent instructional footers. Repeat is horizontal, weekdays are circular toggles. Orb has independent cloud movement using two shared bitmaps at25fps while visible. Discussion, summary and Live model settings are wired to requests; text choices loaded from the subscription catalog in an on-device check.

Host tests and ARM32 builds pass, including date arithmetic, model catalog filtering/cycle order and bounded atomic synthetic WAV writing. AI Slop95/100,0errors,5 canonical OpenAI URL warnings (model catalog endpoint added). Voice sample preparation is **not yet verified**: tested calls acknowledged the context but did not produce output audio; no valid WAV was cached. Do not claim that local previews are working or that all listed voices were checked. Further debugging continues.

Concurrent main-thread builds have also updated the same TV. A test must verify lastUpdateTime/current UI before attributing results to a side-branch build; an installed APK is not stable evidence if another build replaced it during the test. Main working changes are left untouched.

## Дайджесты — ветка codex/news-digests

Реализованы локально новый раздел, источники RSS/Atom/web, фоновые расписания и типизированные голосовые команды; приложение само выполняет поиск и опциональную генерацию. Сначала публикуются тексты и фотографии исходных сайтов, затем при доступности — генерируемые иллюстрации. Imagegen не блокирует дайджесты.

Ручной полноэкранный просмотр и DreamService добавлены. Установка этой ветки, D-pad на TCL, реальный интернет/imagegen, фоновое выполнение в standby и автоматическая замена системной заставки пока не проверены на устройстве. SQLite assertions подготовлены для отдельного self-targeted тестового APK.
