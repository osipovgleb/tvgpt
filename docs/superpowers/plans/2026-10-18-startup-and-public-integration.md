# TCL assistant recovery and public integration

> Execution: superpowers:executing-plans, inline. User authorized repair, integration, GitHub publication with known issues, and TV installation.

**Goal:** Restore assistant button/wake operation after TCL Guard termination; combine latest source and publish an honest experimental preview.

**Architecture:** Retain native VoiceInteractionService/WebRTC and six-row Digests UI. Configure only detected per-app OEM startup appops during existing handover and developer rebinding; unsupported devices remain unchanged. Integrate sanitized current source onto the existing public history, never merge private audio history.

**Constraints:** ARM32/API31; no containers/deploy, uninstall/clear data or hook bypass. Private cues/voice binaries remain outside repository and public APK. Personal TV build preserves them. Only streamed --no-incremental updates with UID/account/binding verification.

- [x] Reproduce and document OEM kill/restart denial using exit-info and appops, not screenshots.
- [x] Add RED/GREEN regression for supported/unsupported startup policies and readback failure. Extend existing handover and connect-tv.py.
- [x] Compare other development/public branches, preserve latest UI/data/voice fixes and public documentation/assets.
- [x] Run host/quality/build checks, install immutable personal APK and verify startup/button/native close and system bindings. Long-term/reboot behavior stays unverified unless measured.
- [x] Import combined sanitized source onto public history, scan for private binaries/credentials/local runtime data, list remaining defects in known-issues documentation.
- [x] Build public artifact without private assets, push normal Git hooks, publish release/PR only if needed for review; attach every created PR.

Ruling: supported startup appops alone did not prevent subsequent TGuard killing. Add only this package to the documented runtime kill whitelist, read back, and reapply once per process startup. Do not disable the global cleaner; persistence across Guard restarts is not guaranteed.
Ruling: post-rebind BadTokenException is a separate confirmed crash; invalidate disconnected accessibility instances and fall back to the existing native system session window, preserving orb-only presentation.
Review: fresh-context reviewer approved the unsupported-operation filter, stale-token cleanup/fallback and scoped runtime whitelist; host tests do not prove all device paths.

Completion: combined preview published at https://github.com/osipovgleb/tvgpt/releases/tag/v0.1.1-preview with ARMv7 APK and SHA256SUMS; GitHub asset digest matches local public build. Personal build installed with original private cue and UID10161 retained. Public history excludes private development ancestry. Remaining first-invocation, natural closing, Fast and long-idle/reboot limitations are documented rather than claimed resolved.
