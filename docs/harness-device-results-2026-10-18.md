# Harness device observations ·18 October2026

Sanitized public record. Raw diagnostic dumps, account data and private conversation archives are not included.

- TCL Android12/ARM32, native WebRTC microphone capture, standalone VoiceInteractionService.
- A real user request to return Home delegated to navigate, followed by fresh screen observation and an observed result. The physical HOME callback retained the Live overlay.
- Runtime task diagnostics exposed self-cancellation: the current executor treated its own running task as a predecessor. A regression reproduced cancellation before navigation; self/alias cancellation is now rejected while cancellation of other tasks remains possible.
- Two independent workers had alternated navigation on the same screen. Screen ownership now spans a task and is released only after its executor unwinds. Reads and actions from another task return blocked; call closing/task controls bypass screen ownership.
- Late results were forced to speech even after newer user speech. Current results retain the speakable channel; older results use the default context channel based on the original user turn ID, including partial turn creation and delayed mirrored finals.
- Host tests and ARM32 packaging passed. One real speech/action/observation/reply path passed. This does not establish reliable Match TV routing, repeated voice closing or long-term stability.
- An attempted automated Live test using transient UI status as its startup predicate was inconclusive and is not counted as a pass. Test packages were removed and normal assistant bindings restored.
- Later, TCL Guard killed the assistant process and denied restarts because two OEM startup appops were ignore. Allowing those operations for this package restored immediate startup. Reboot and long-idle recovery remain unverified.

See [known issues](known-issues.md) and [development/setup notes](installation-and-development.md). Upstream protocol references: https://developers.openai.com/api/docs/guides/live-delegation and https://github.com/openai/codex/tree/main/codex-rs/codex-api/src/endpoint/realtime_websocket .
