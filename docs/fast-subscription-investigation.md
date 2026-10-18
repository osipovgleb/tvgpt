# Fast through the Codex subscription

18 October2026. TV GPT is a custom subscription protocol client, not a Codex CLI runtime. Do not switch it to API-key billing, silently substitute a different model, or claim that a requested tier was actually served.

## Observations already collected on the TV

- Selected discussion model: gpt-6.1-sol. The fresh authenticated subscription catalog advertises priority/Fast for this Pro account.
- Production sends service_tier=priority and matching x-codex-routing-hint=model=gpt-6.1-sol;tier=priority. Successful responses report default.
- Sending service_tier=fast to this subscription endpoint returned HTTP400 unsupported_service_tier. Public API aliases do not establish subscription endpoint behavior.
- CLI-compatible request headers and the Responses Lite shape both reported default. These probes do not prove a cause or establish that changing subscriptions would help.
- Reproducing Hermes' subscription request shape, including underscore session_id, prompt cache/request headers, residency and encrypted reasoning, also returned default. Standard and Fast both completed successfully. See tests/android/HermesFastChecks.java. Credentials stayed on the TV.

## Upstream evidence

The official [Codex speed guide](https://learn.chatgpt.com/docs/agent-configuration/speed) documents subscription Fast and supported models. The public [API Fast guide](https://developers.openai.com/api/docs/guides/fast-mode) documents the priority/fast aliases and default response tier; that guide describes the billing API, not every behavior of the custom ChatGPT backend.

Current upstream [client.rs](https://github.com/openai/codex/blob/main/codex-rs/core/src/client.rs) resolves catalog-supported service tiers, serializes the tier in the request and includes the model/tier routing hint. Its Responses WebSocket handshake uses OpenAI-Beta: responses_websockets=2026-02-06. No additional documented mandatory Fast switch was found there.

Open reports [#30413](https://github.com/openai/codex/issues/30413) and [#32191](https://github.com/openai/codex/issues/32191) contain similar observations from ChatGPT-authenticated official clients, including routing-hint requests. They have no maintainer-confirmed root cause or supported workaround in the comments inspected on18 October. Reporters disagree about whether the subscription response-tier echo alone authoritatively establishes a downgrade; do not promote either assumption into a proven backend fact. No external bug report or comment was sent.

## On-device comparison

`fast-transport` in DeviceCheck runs tests/android/FastTransportChecks.java:

1. Balanced Standard/Fast/Fast/Standard HTTP requests with identical synthetic output (integers0001–0100).
2. Collect only requested/created/completed tiers, allowed tier response headers, first-output/generation timing, output/reasoning token counts and exact-output match. Unequal output lengths or unfinished streams cannot substantiate a speed comparison.
3. Make a fresh priority Responses WebSocket request with the beta/header shape from upstream and report its handshake/terminal metadata.

The diagnostic does not change saved models, Fast selection, history, microphone or account. It uses the existing TV credentials only on the canonical TLS endpoint, disables redirects, bounds calls/output and closes its HTTP/WebSocket resources. It is test-only. A short A/B comparison is a diagnostic signal, not a production latency guarantee or a statistically strong benchmark.

**Completed after the user authorized installation/testing:** instrumentation restarts the assistant and must wait for an authorized test window. Production remains on its existing transport until a demonstrated fix justifies a change.


## Measured result,18 October09:11 (TV local time)

The balanced HTTP comparison completed with exact matching outputs and zero reasoning tokens:

| Output | Standard generation ms | Fast generation ms | Generation speedup |
| --- | --- | --- | --- |
|303 tokens |3779 /3413 |2132 /2156 |1.68× |
|903 tokens |9939 /11949 |6637 /6739 |1.64× |

All eight completed responses echoed `service_tier=default`; HTTP200, complete terminal events, and exact synthetic output were checked. The longer `fast-benchmark` retains the production OpenAI-Alpha header and explicitly sends default/priority with matching routing hints. Its original diagnostic report mislabeled the explicit default request as "omitted"; the wire body did contain default, and the diagnostic label is corrected. No saved model/reasoning/Fast preferences or conversation history were changed. Credentials remained on the television.

This is reproducible acceleration in these bounded comparisons, not a latency guarantee or proof of a specific backend hardware tier. It refutes our earlier inference that this subscription echo alone proves Fast was declined. UI now keeps "Fast запрошен" for default/unknown echoes; raw returned tier remains available in diagnostics. Explicit priority/fast acknowledgement still displays Fast. No unsupported API alias, account change or API key is needed for this correction.

The separate WebSocket probe upgraded with HTTP101 but did not produce a terminal response within the probe and is not used in production. Keep the working HTTP transport; do not infer WebSocket Fast support from the handshake.
