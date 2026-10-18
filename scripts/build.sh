#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
export JAVA_HOME="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
TVGPT_BT="$TVGPT_TOOLCHAIN/android-15"
TVGPT_KEYSTORE=${TVGPT_KEYSTORE:-build/debug.keystore}
TVGPT_JAR="$TVGPT_TOOLCHAIN/android-35/android.jar"
rm -rf build/classes build/dex build/compiled
mkdir -p build/classes build/dex build/compiled
python3 scripts/check-architecture.py
python3 scripts/bootstrap.py
python3 scripts/prepare-assets.py
javac -encoding UTF-8 -source 8 -target 8 -classpath "$TVGPT_JAR:build/deps/okhttp.jar:build/deps/okio.jar:build/deps/vosk/classes.jar:build/deps/jna/classes.jar:build/deps/webrtc/classes.jar" -d build/classes $(find app/src -name '*.java')
"$TVGPT_BT/aapt2" compile --dir app/res -o build/resources.zip
"$TVGPT_BT/aapt2" link -o build/unsigned.apk -I "$TVGPT_JAR" --manifest app/AndroidManifest.xml --auto-add-overlay build/resources.zip -A build/assets
"$TVGPT_BT/d8" --lib "$TVGPT_JAR" --min-api 29 --output build/dex $(find build/classes -name '*.class') build/deps/okhttp.jar build/deps/okio.jar build/deps/vosk/classes.jar build/deps/jna/classes.jar build/deps/webrtc/classes.jar
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a') as z:
 z.write('build/dex/classes.dex','classes.dex')
 z.write('build/deps/wake-model.zip','assets/wake-model.zip')
 for dependency,library in [('vosk','libvosk.so'),('jna','libjnidispatch.so'),('webrtc','libjingle_peerconnection_so.so')]:
  z.write('build/deps/'+dependency+'/jni/armeabi-v7a/'+library,'lib/armeabi-v7a/'+library)
PY
if [[ ! -f "$TVGPT_KEYSTORE" ]]; then
 keytool -genkeypair -keystore "$TVGPT_KEYSTORE" -storepass android -keypass android -alias tvgpt -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=TV GPT Development'
fi
"$TVGPT_BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$TVGPT_BT/apksigner" sign --ks "$TVGPT_KEYSTORE" --ks-pass pass:android --key-pass pass:android --out build/tvgpt.apk build/aligned.apk
"$TVGPT_BT/apksigner" verify build/tvgpt.apk
echo 'Built build/tvgpt.apk'
