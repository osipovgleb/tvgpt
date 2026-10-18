#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=/home/gleb/.cache/tvgpt-builds/repair-data
java_bin=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}/usr/lib/jvm/java-17-openjdk-amd64/bin
mkdir -p "$out/db" "$out/summary"
"$java_bin/javac" -cp build/deps/json-test.jar -d "$out/db" $(find tests/data-stubs -name "*.java") tests/digest-stubs/android/content/ContentValues.java tests/digest-stubs/android/database/sqlite/SQLiteOpenHelper.java app/src/local/tvgpt/assistant/CancellationToken.java app/src/local/tvgpt/assistant/data/{AppDatabase,DigestSchema,ReminderRepository,KnowledgeRepository,ConversationRepository,DataPolicy}.java app/src/local/tvgpt/assistant/reminders/{SchedulePolicy,ReminderDispatcher,DeliveryOutcome}.java tests/DataRepairTest.java tests/DataMutationCancellationTest.java tests/ConversationDeletionTest.java
"$java_bin/java" -cp "$out/db":build/deps/json-test.jar local.tvgpt.assistant.DataMutationCancellationTest
"$java_bin/java" -cp "$out/db":build/deps/json-test.jar local.tvgpt.assistant.DataRepairTest
"$java_bin/java" -cp "$out/db":build/deps/json-test.jar local.tvgpt.assistant.ConversationDeletionTest
"$java_bin/javac" -cp build/deps/json-test.jar -d "$out/summary" tests/summary-stubs/android/{content/Context,util/Log}.java tests/summary-stubs/SummaryFakes.java tests/summary-stubs/local/tvgpt/assistant/data/ConversationRepository.java app/src/local/tvgpt/assistant/{ConversationSummarizer,CodexRouting}.java tests/ConversationSummarizerTest.java
"$java_bin/java" -cp "$out/summary":build/deps/json-test.jar local.tvgpt.assistant.ConversationSummarizerTest

"$java_bin/javac" -d "$out/db" app/src/local/tvgpt/assistant/reminders/DeliveryOutcome.java tests/ReminderDeliveryTest.java
"$java_bin/java" -cp "$out/db" local.tvgpt.assistant.reminders.ReminderDeliveryTest

python3 tests/test_data_api_compatibility.py "$out/summary" "$out/db"
