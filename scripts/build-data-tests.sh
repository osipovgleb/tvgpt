#!/usr/bin/env bash
# Independent test package: does not restart or replace the installed assistant.
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
export JAVA_HOME="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
TVGPT_BT="$TVGPT_TOOLCHAIN/android-15"
TVGPT_JAR="$TVGPT_TOOLCHAIN/android-35/android.jar"
TVGPT_KEYSTORE=${TVGPT_KEYSTORE:-build/debug.keystore}
mkdir -p build/data-classes build/data-dex
javac -encoding UTF-8 -source 8 -target 8 -classpath "$TVGPT_JAR:build/deps/okhttp.jar:build/deps/okio.jar" -d build/data-classes app/src/local/tvgpt/assistant/CancellationToken.java app/src/local/tvgpt/assistant/data/{AppDatabase,DigestSchema,DigestConfiguration,DigestArchive,DigestRepository,DataPolicy,ConversationRepository,KnowledgeRepository,ReminderRepository}.java app/src/local/tvgpt/assistant/reminders/SchedulePolicy.java app/src/local/tvgpt/assistant/settings/{AssistantPreferences,OrbPalette,SpeechPreset}.java tests/android/FeatureChecks.java app/src/local/tvgpt/assistant/digests/{DigestPolicy,DigestSchedulePolicy,DigestSlides,DigestCovers,DigestImages,DigestRunPolicy,ImageGenerator,NewsPolicy,NewsSourceClient}.java tests/isolated/{DataCheck,DigestDataChecks,DigestCoverChecks,ModelSettingsChecks}.java app/src/local/tvgpt/assistant/ModelSettings.java app/src/local/tvgpt/assistant/settings/ModelOptions.java
"$TVGPT_BT/d8" --lib "$TVGPT_JAR" --min-api 29 --output build/data-dex $(find build/data-classes -name '*.class') build/deps/okhttp.jar build/deps/okio.jar
"$TVGPT_BT/aapt2" link -o build/data-unsigned.apk -I "$TVGPT_JAR" --manifest tests/isolated/AndroidManifest.xml
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/data-unsigned.apk','a') as archive:
 archive.write('build/data-dex/classes.dex','classes.dex')
PY
"$TVGPT_BT/zipalign" -f 4 build/data-unsigned.apk build/data-aligned.apk
"$TVGPT_BT/apksigner" sign --ks "$TVGPT_KEYSTORE" --ks-pass pass:android --key-pass pass:android --out build/tvgpt-data-tests.apk build/data-aligned.apk
