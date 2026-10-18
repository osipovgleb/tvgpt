#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
TVGPT_JAVA="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin"
TVGPT_DIGEST_CANCEL_BUILD=${TVGPT_DIGEST_CANCEL_BUILD:-${XDG_CACHE_HOME:-$HOME/.cache}/tvgpt-builds/repair-digest-cancellation}
mkdir -p "$TVGPT_DIGEST_CANCEL_BUILD"
mapfile -t digest_stubs < <(rg --files tests/digest-stubs -g '*.java')
TVGPT_DIGEST_CANCEL_CP="build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar"
"$TVGPT_JAVA/javac" -cp "$TVGPT_DIGEST_CANCEL_CP" -d "$TVGPT_DIGEST_CANCEL_BUILD" \
  "${digest_stubs[@]}" app/src/local/tvgpt/assistant/CancellationToken.java \
  app/src/local/tvgpt/assistant/data/{AppDatabase,DigestSchema,DigestConfiguration,DigestArchive,DigestRepository}.java \
  app/src/local/tvgpt/assistant/digests/{DigestPolicy,DigestSchedulePolicy,DigestSlides,DigestImages,DigestRunPolicy,NewsPolicy,NewsSourceClient}.java \
  tests/{DigestRepositoryTest,DigestCancellationTest}.java
"$TVGPT_JAVA/java" -cp "$TVGPT_DIGEST_CANCEL_BUILD:$TVGPT_DIGEST_CANCEL_CP" local.tvgpt.assistant.digests.DigestCancellationTest
"$TVGPT_JAVA/java" -cp "$TVGPT_DIGEST_CANCEL_BUILD:$TVGPT_DIGEST_CANCEL_CP" local.tvgpt.assistant.digests.DigestRepositoryTest
