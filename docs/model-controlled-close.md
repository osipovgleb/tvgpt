# Model-controlled voice conversation closing

18 October 2026. The user requires the Live model to decide whether the conversation
has ended by meaning and context, in any language. A deterministic list of user
phrases is not the product contract.

## Evidence and correction

The 09:56 call archive includes `Ну ладно, всё, хватит`, an assistant acknowledgement,
and a later user turn containing `Ладно, пока` and a question about the silence.
No local close was requested; the user finally used the remote button. The first
successful close in the available log released native media in 113 ms. This is
not evidence of a slow teardown in the failed call: no close action reached it.

The old `SpeechStop`/`EndPhrase` path guessed intent from transcript fragments and
final turns. The Live prompt also required an acknowledgement for any action,
conflicting with silent end-of-conversation delegation. The attempted expansion
of the Russian dictionary was reverted and never installed.

The current design removes transcript-based closing, its dictionary and timer.
Live decides from conversation context, and sends a client delegation containing
the canonical local action. The application routes this action directly to the
existing idempotent native close path. The canonical action is a model-to-client
interface, not a user's magic phrase or a second intent-recognition model.
Ordinary tasks still receive one short acknowledgement; closing is the explicit
silent exception. The remote assistant button remains an independent close input.

Only one Live call is used. There is no extra ASR, extra microphone capture, extra
cloud request for closing, or dependency on archived conversations.

## Sources and limits

The public Codex conversational prompt delegates actions to the executor; it does
not disclose how the proprietary desktop UI implements voice goodbye handling.
OpenAI's Live migration guide explicitly assigns farewell/closing intent and
end-call delegation to Live, and actual disconnection to the application:
https://developers.openai.com/api/docs/guides/live-migration

The recommended short prompt structure is documented at:
https://developers.openai.com/api/docs/guides/live-prompting

Host protocol tests verify that transcript events cannot close a call and that
only the model's client close action reaches the local router. They do not prove
multilingual intent recognition. Device model-action replay verifies native
lifecycle; it is not evidence that a real spoken request was understood.

## 18 October device check

Personal APK SHA-256: `713d323b87170b6195e5d93e926b94b6fc5c28f306841b4d2cc857fd36c2aa34`.
Streamed update at 10:10:22 retained UID 10161/account. The build includes Digests,
the original private startup OGG, and no SpeechStop or InputEndMonitor classes.

Three consecutive ready native calls in one instrumented process accepted the
injected model client action and closed in 377, 256 and 255 ms. No Agent request
was needed. This verifies the action-to-teardown path only. A real spoken test
of Live's semantic decision is requested separately; no multilingual recognition
success is claimed from the injected events.

The first instrumentation run was interrupted by another instrumentation process;
its result is not treated as an app crash or a test pass. A distinct temporary
closechecks package avoided collision with the shared test package. Services were
rebound while the test process was foreground; the temporary package was removed
and assistant/accessibility bindings restored afterward.

Host suite and ARM32/test APK builds passed. Quality: 93/100, zero errors and seven
canonical URL warnings in the combined news/assistant source; no checks disabled.

## Real speech regression, 10:15–10:20

The user reported both real calls closed poorly. Package lastUpdateTime remained
10:10:22, so this was the same installed build, not a later APK replacement.
Both calls eventually closed via `agent_end_tool`, not `live_end_delegation`:

- 10:18:29.652 delegation; 10:18:32.307 backend end tool; native release 116 ms.
- 10:20:33.355 delegation; 10:20:35.629 backend end tool; native release 100 ms.

In the second archive the user said `Окей, пока-пока`. Live replied `Пока` at
about 10:18:55, then later said it would close and falsely claimed the chat had
already closed. The archive shows the user's corrections and repeated requests.
The only saved backend request is the final repeated natural-language request;
the backend then invoked `end_conversation`. There was no canonical local close
delegation from Live before that. Roughly 100 seconds elapsed after the farewell
until actual closing. This is a model-to-action contract failure, not measured
native teardown latency.

The Live session currently exposes client delegation, not a direct Live-layer
function tool schema. Prompting it to emit the canonical close request has not
proved reliable. Do not treat the three injected action tests as verification of
natural-language closing. No production change was installed during this audit.

## Harness contract audit

The fast-path assumption itself is wrong: the Frameless Bidi delegation content
is parsed upstream as `RealtimeHandoffRequested.input_transcript`. Our observed
handoff contained the original repeated user request, exactly as stored in Agent
history. Treating that field as an independently authored canonical action string
is unsupported. The injected test created such a string by hand and therefore
never validated that Live can produce it through this contract.

Upstream parser:
https://github.com/openai/codex/blob/main/codex-rs/codex-api/src/endpoint/realtime_websocket/protocol_frameless_bidi.rs

Our actual `end_conversation` function schema is registered in AgentTools, and
executed by Agent.executeTool. Live currently receives prose capabilities and
client delegation, not that function schema. AssistantSkills are loaded by Agent;
the app-search/YouTube skills do not run before Live delegates. Moving more closing
synonyms into those skills would not repair the handoff contract.

The public Live docs describe separate voice/delegation guidance and backend tool
execution. A supported structured action path must be verified before again
claiming a direct model-to-local close tool. The earlier no-Agent-HTTP closing
requirement remains a design constraint; do not quietly introduce a new semantic
classifier, cloud request per transcript fragment, or another Live session.

## Repair decision

The user explicitly authorized the existing Agent executor for end_conversation. Delegation content is original user speech, so the canonical-text shortcut is removed. Live decides whether to delegate; Agent resolves the original natural request against current-call context and invokes the real end tool. No second Live session or local language dictionary. The end tool initiates local media/UI teardown directly and cancels pending work; summary/archive jobs remain independent. Repeated real speech calls are still a required device gate.
