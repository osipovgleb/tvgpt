#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
export JAVA_HOME="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
TVGPT_BT="$TVGPT_TOOLCHAIN/android-15"
TVGPT_KEYSTORE=${TVGPT_KEYSTORE:-build/debug.keystore}
TVGPT_JAR="$TVGPT_TOOLCHAIN/android-35/android.jar"
mkdir -p build/test-classes build/test-dex
javac -encoding UTF-8 -source 8 -target 8 -classpath "$TVGPT_JAR:build/classes:build/deps/okhttp.jar:build/deps/okio.jar" -d build/test-classes $(find tests/android -name '*.java')
"$TVGPT_BT/d8" --lib "$TVGPT_JAR" --min-api 29 --output build/test-dex $(find build/test-classes -name '*.class')
"$TVGPT_BT/aapt2" link -o build/tests-unsigned.apk -I "$TVGPT_JAR" --manifest tests/android/AndroidManifest.xml
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/tests-unsigned.apk','a') as z:z.write('build/test-dex/classes.dex','classes.dex')
PY
"$TVGPT_BT/zipalign" -f 4 build/tests-unsigned.apk build/tests-aligned.apk
"$TVGPT_BT/apksigner" sign --ks "$TVGPT_KEYSTORE" --ks-pass pass:android --key-pass pass:android --out build/tvgpt-tests.apk build/tests-aligned.apk
