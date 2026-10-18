#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
java_bin="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin"
out="${XDG_CACHE_HOME:-$HOME/.cache}/tvgpt-builds/reminder-tools"
mkdir -p "$out/db" "$out/tools"
"$java_bin/javac" -cp build/deps/json-test.jar -d "$out/db" \
  $(find tests/data-stubs -name '*.java') tests/digest-stubs/android/content/ContentValues.java \
  tests/digest-stubs/android/database/sqlite/SQLiteOpenHelper.java \
  app/src/local/tvgpt/assistant/CancellationToken.java \
  app/src/local/tvgpt/assistant/data/{AppDatabase,DigestSchema,ReminderRepository,ReminderChanges,DataPolicy}.java \
  app/src/local/tvgpt/assistant/reminders/{SchedulePolicy,ReminderDispatcher,DeliveryOutcome}.java
# Production repositories use real SQLite; platform time, broadcasts and scheduling are isolated.
"$java_bin/javac" -cp "$out/db:build/deps/json-test.jar" -d "$out/tools" \
  $(find tests/reminder-tools-stubs -name '*.java') \
  app/src/local/tvgpt/assistant/ReminderTools.java tests/ReminderToolMutationTest.java
"$java_bin/java" -cp "$out/tools:$out/db:build/deps/json-test.jar" \
  local.tvgpt.assistant.ReminderToolMutationTest
