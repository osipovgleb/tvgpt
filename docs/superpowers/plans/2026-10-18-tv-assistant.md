# TV Assistant Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task in this session.

**Goal:** Install an autonomous ARM32 TV assistant with remote-button overlay, device-code login, live voice and device controls.
**Architecture:** Android platform services provide the window, microphone and controls. Reuse Codex's subscription device-auth and WebRTC voice protocol; first determine whether the full CLI runtime is necessary or a small protocol client is sufficient. Isolate authentication, voice transport and command execution.
**Tech Stack:** Android Java / WebView, optional Rust native component where required and ARM32-compatible.
**Spec:** docs/superpowers/specs/2026-10-18-tv-voice-assistant-design.md

## Global Constraints
- Android API31, armeabi-v7a; no permanent computer runtime.
- User requests immediate implementation, installation and device-code verification; proceed inline without further routine approval stops.
- Do not copy desktop account credentials. Account login is completed by the user.
- No container builds, image publishing, release scripts or deployment workflows.

## Review Focus
- Expired and cancelled device codes must not persist incomplete credentials.
- A successful RPC is not proof of a working voice call.
- Closing an overlay must release the microphone.
- Device actions must report actual success or failure.
- Desktop ADB cannot become a hidden runtime dependency.

### Task 1: Establish headless login and ARM32 architecture
- [ ] Inspect existing open-source device-code and live implementations, establish minimal dependencies and exact protocol.
- [ ] Add authentication parsing tests for pending/expired/success responses before implementation.
- [ ] Implement device-code flow in an app-private account store; no secrets in logs.
- [ ] Verify account status after the user completes the code.

### Task 2: Install assistant window and controls
- [ ] Add manifest/service contract validation, then implement VoiceInteractionService/Session and TV launcher.
- [ ] Implement accessible navigation, app launching, volume and close controls using Android APIs.
- [ ] Build/sign an APK locally; install it and open the window on the TV.
- [ ] Verify remote invocation and window close lifecycle on device.

### Task 3: Connect Live and tool delegation
- [ ] Add protocol parsing tests for session creation, signalling and delegation boundaries.
- [ ] Implement WebRTC audio and authenticated control transport using the account's available voice route.
- [ ] Connect supported device tools; report failures honestly.
- [ ] Verify actual conversation and at least one app-control action on TV.

### Task 4: Device acceptance
- [ ] Test cancellation, disconnect and microphone cleanup.
- [ ] Test without a development-host runtime.
- [ ] Record exact working features and blockers in README and verification report.
