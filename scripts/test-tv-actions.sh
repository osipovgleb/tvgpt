#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
TVGPT_JAVA="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin"
TVGPT_ACTION_TEST_BUILD=${TVGPT_ACTION_TEST_BUILD:-${XDG_CACHE_HOME:-$HOME/.cache}/tvgpt-builds/repair-tv}
TVGPT_ANDROID="$TVGPT_TOOLCHAIN/android-35/android.jar"
mkdir -p "$TVGPT_ACTION_TEST_BUILD"/{android-classes,host,command-stubs}
mapfile -t action_sources < <(rg --files app/src -g '*.java')
"$TVGPT_JAVA/javac" -encoding UTF-8 -source 8 -target 8 \
  -classpath "$TVGPT_ANDROID:build/deps/okhttp.jar:build/deps/okio.jar:build/deps/vosk/classes.jar:build/deps/jna/classes.jar:build/deps/webrtc/classes.jar" \
  -d "$TVGPT_ACTION_TEST_BUILD/android-classes" "${action_sources[@]}"
mapfile -t action_stubs < <(rg --files tests/tv-stubs -g '*.java')
"$TVGPT_JAVA/javac" -cp "build/deps/json-test.jar:$TVGPT_ANDROID:$TVGPT_ACTION_TEST_BUILD/android-classes" \
  -d "$TVGPT_ACTION_TEST_BUILD/host" "${action_stubs[@]}" \
  tests/{ActionDispatchTest,AdbDispatchTest,TvMutationQueueTest,TvAccessibilitySafetyTest,TvNavigationResourceTest,TvSearchTargetTest,MediaSessionControlTest,DirectInputRequestTest,RemoteSequenceTest}.java
TVGPT_ACTION_CP="$TVGPT_ACTION_TEST_BUILD/host:$TVGPT_ACTION_TEST_BUILD/android-classes:build/deps/json-test.jar:$TVGPT_ANDROID"
for test in ActionDispatchTest AdbDispatchTest TvMutationQueueTest TvAccessibilitySafetyTest TvNavigationResourceTest TvSearchTargetTest MediaSessionControlTest DirectInputRequestTest RemoteSequenceTest; do
  "$TVGPT_JAVA/java" -cp "$TVGPT_ACTION_CP" "local.tvgpt.assistant.$test"
done
"$TVGPT_JAVA/javac" -cp "$TVGPT_ACTION_CP" -d "$TVGPT_ACTION_TEST_BUILD/command-stubs" \
  tests/tv-command-stubs/AdbRemote.java tests/TvRemoteFallbackTest.java
"$TVGPT_JAVA/java" -cp "$TVGPT_ACTION_TEST_BUILD/command-stubs:$TVGPT_ACTION_CP" \
  local.tvgpt.assistant.TvRemoteFallbackTest
