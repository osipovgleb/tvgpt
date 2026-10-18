# Native voice and resource audit

This change replaces the WebView audio client at the user's request. It was developed in isolation and rebased onto `e4e6f02` before integration. Device measurements and verification are recorded separately.

## Runtime and boundaries

The system assistant, permissions and views remain Java/Android. The launcher opens settings only; voice appears in the separate compact system assistant popup. Voice recording, playback, WebRTC codec/transport and acoustic processing use the native C++ WebRTC engine. Wake recognition already uses native Vosk. A full Rust rewrite would duplicate Android bindings and voice/media integration; it would not remove these native engines or cloud latency. Reconsider Rust only for a measured compute hotspot that the current native libraries do not cover.

- `VoicePanel`: Android presentation, controls and startup cue; no browser, signalling or peer-connection API.
- `VoiceSession`: hardware-independent call owner, event routing, one ready notification, mute, failure and asynchronous release completion.
- `LiveSignalling`: adapts the existing account/subscription protocol; workspace identity remains independent from each voice connection.
- `voice/NativeWebRtcTransport`: audio-only peer connection, SDP/data channel, bounded connection setup, low-frequency statistics, serialized ownership and teardown.
- `voice/NativeAudioDevice`: mono native recording/playback, communication routing, hardware AEC/NS when available and restoring the prior audio mode.
- `OrbTexture` / `OrbMotion` / `VoiceOrb`: cached artwork, bounded motion and Android rendering.

No desktop agent, Node, API key or Rust runtime was added to the TV. The native library is prebuilt for ARMv7, pinned by SHA-256, and used directly; this is not a Codex CLI port.

## Work removed from the TV

| Area | Earlier implementation | This branch | What remains to measure |
| --- | --- | --- | --- |
| Live audio | Hidden WebView and browser WebRTC | Native audio-only WebRTC | Total PSS including renderer, idle/live CPU, connection latency |
| Legacy audio | Unused PCM ring, Base64 bridge and WebAudio code remained in tree | Removed | Native microphone and routing compatibility on TCL |
| Orb | Per-frame gradient/shader setup | One shared 256×256 ARGB texture (256 KiB), cached draw objects | GPU load and actual frame delivery on TV |
| Animation | Continuous draw scheduling | 25 fps maximum, stops when hidden/detached | Frame timing while video plays |
| Wake model | Kept allocated when wake listening was explicitly switched off | Released after its recognizer exits; retained between normal conversations | Actual model PSS and cold restart time |
| Lifecycle | UI owned browser audio resources | One serialized native worker; wake resumes after release completion | Repeated start/stop and background/screen-off cases |
| Statistics | Browser-side audio processing/bridge diagnostics | Native stats once per second, byte counters every five seconds | Whether lower sampling is needed |

The native engine adds an ARMv7 shared library (~6.5 MiB uncompressed). APK size, app-process RSS and total system PSS are different measurements. This branch is not claimed to use a particular amount less CPU/RAM before device comparison. Vosk remains a likely significant idle cost; replacing it with an unverified keyword model would risk losing Russian wake support.

Acoustic echo cancellation is requested through WebRTC, preferring device hardware support. This is not a guarantee that a TCL firmware provides a usable reference for **other applications'** playback. Native duplex audio, software fallback cost and TV speaker echo must be checked on hardware. Existing audio-focus ducking is preserved; this branch does not overwrite the main task's background-volume work.

## Checks and reproducibility

`npm run quality` runs AI Slop and a separate Java dependency-boundary check. Standard Java formatting is reproducible and checksum-pinned. Long request/turn/setup handlers were separated into smaller methods; no rules were disabled to hide findings. AI Slop's remaining canonical OpenAI URL warnings are deliberate, documented decisions rather than configurable arbitrary destinations for bearer tokens.

`bash scripts/test.sh` checks protocol handling, action continuation, persistent session identity, concurrency, invocation policy, native-session routing, mute, late callbacks, one-time ready notification and release ordering. It also verifies bounded orb motion and resuming after a long hidden interval. `bash scripts/build.sh` compiles the native adapters against the real WebRTC AAR and builds/signs an ARM32 APK. Neither is a hardware audio test.

The isolated build has its own debug signing key. Do not install it as an update over the main development APK: integrate the source changes and rebuild with the existing installation's signing key. Do not uninstall the existing package just to get around a signature mismatch; that would delete its account and session context.

## Device acceptance and resource comparison

After integration, compare the old and new versions with the same TV settings, video content, wake configuration and account. Let model loading settle first. Capture at least three samples per condition: wake disabled idle, wake enabled idle, Live listening, Live speaking and Live over playing video. The helper is read-only and does not install, start or stop applications:

```sh
python3 scripts/profile-tv.py --serial TV_ADB_SERIAL --label native-live-listening --output build/native-live-listening.json
```

Compare total system PSS across app and browser-renderer processes, not just the Java heap. `dumpsys cpuinfo` covers a preceding sampling interval; do not compare unrelated intervals as if they measured one identical request. Measure invocation-to-cue and speech-to-first-audio latency independently from CPU/RAM.

Required functional checks: real microphone speech and audible response; delegated TV command; remote-button toggle; wake→Live→wake handoff; repeated open/close/retry; network loss/reconnect; close while connecting; mute/unmute; return to video; Google HOTWORD capture contention; hardware microphone switch. Confirm recorders stop after closing and that the persisted working session still reuses its ID within 12 hours of activity. Check native stats for both sent and received bytes, but do not treat byte counters alone as proof of intelligible speech.

Initial on-device results are recorded in [native-device-results.md](native-device-results.md). They cover a short live test and resource samples, not the full acceptance matrix. Keep the previous APK available while broader checks continue.
