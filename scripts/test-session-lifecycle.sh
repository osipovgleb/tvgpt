#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
TVGPT_SESSION_TEST_BUILD=${TVGPT_SESSION_TEST_BUILD:-${XDG_CACHE_HOME:-$HOME/.cache}/tvgpt-builds/session-lifecycle}
mkdir -p "$TVGPT_SESSION_TEST_BUILD"
mapfile -t session_stubs < <(rg --files tests/session-stubs -g '*.java')
"$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin/javac" -cp "$TVGPT_TOOLCHAIN/android-35/android.jar" -d "$TVGPT_SESSION_TEST_BUILD" "${session_stubs[@]}" tests/tv-stubs/android/util/Log.java app/src/local/tvgpt/assistant/{SessionService,AssistantInvocation}.java tests/PhysicalHomeSessionTest.java
"$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin/java" -cp "$TVGPT_SESSION_TEST_BUILD:$TVGPT_TOOLCHAIN/android-35/android.jar" local.tvgpt.assistant.PhysicalHomeSessionTest
