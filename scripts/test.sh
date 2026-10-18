#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
TVGPT_JAVA="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin"
mkdir -p build/test
if [[ ! -f build/deps/json-test.jar ]]; then
 python3 - <<'PY'
import urllib.request,hashlib
from pathlib import Path
url='https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
data=urllib.request.urlopen(url,timeout=30).read()
if hashlib.sha256(data).hexdigest()!='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':
 raise SystemExit('JSON test dependency checksum mismatch')
Path('build/deps/json-test.jar').write_bytes(data)
PY
fi
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/LiveStartupTrace.java tests/LiveStartupTraceTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.LiveStartupTraceTest
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/{Protocol,LiveProtocol,WakePhrase,AdbWire,SessionStore,AssistantSetup,AssistantInvocation,DirectInputRequest,AppSearchPolicy,AppNavigation}.java tests/{ProtocolTest,LiveProtocolTest,WakePhraseTest,AdbWireTest,ConversationPolicyTest,SessionStoreTest,AssistantSetupTest,AssistantInvocationTest,DirectInputRequestTest,AppSearchPolicyTest,AppNavigationTest}.java
for test in ProtocolTest LiveProtocolTest WakePhraseTest AdbWireTest ConversationPolicyTest SessionStoreTest AssistantSetupTest AssistantInvocationTest DirectInputRequestTest AppSearchPolicyTest AppNavigationTest; do
 "$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar "local.tvgpt.assistant.$test"
done

"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/{CancellationToken,ActionWait,ActionObservation,AudioFocusLease,PlaybackObservation,MediaSessionControl,RemoteSequence}.java tests/{ActionWaitTest,ActionObservationTest,AudioFocusLeaseTest,PlaybackObservationTest,MediaSessionControlTest,RemoteSequenceTest}.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.ActionWaitTest
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.ActionObservationTest

"$TVGPT_JAVA/java" -cp build/test local.tvgpt.assistant.AudioFocusLeaseTest
"$TVGPT_JAVA/java" -cp build/test local.tvgpt.assistant.PlaybackObservationTest
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.MediaSessionControlTest

"$TVGPT_JAVA/javac" -d build/test app/src/local/tvgpt/assistant/AccessibilityPolicy.java tests/AccessibilityPolicyTest.java
"$TVGPT_JAVA/java" -cp build/test local.tvgpt.assistant.AccessibilityPolicyTest

"$TVGPT_JAVA/javac" -d build/test app/src/local/tvgpt/assistant/AppSkillIndex.java tests/AppSkillIndexTest.java
"$TVGPT_JAVA/java" -cp build/test local.tvgpt.assistant.AppSkillIndexTest

mkdir -p build/agent-test
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/agent-test tests/agent-stubs/android/content/Context.java tests/agent-stubs/android/util/Log.java tests/agent-stubs/AgentFakes.java tests/agent-stubs/local/tvgpt/assistant/data/{KnowledgeRepository,ConversationRepository}.java app/src/local/tvgpt/assistant/{CancellationToken,TaskHarness,TaskEvidence,Agent,AgentTools,CodexRouting,ConversationTools,WebSources,LiveProtocol,SessionStore}.java tests/{AgentLoopTest,AgentHarnessTest,SessionConcurrencyTest,AgentConversationTest,ConversationToolsTest}.java
"$TVGPT_JAVA/java" -cp build/agent-test:build/deps/json-test.jar local.tvgpt.assistant.AgentLoopTest
"$TVGPT_JAVA/java" -cp build/agent-test:build/deps/json-test.jar local.tvgpt.assistant.AgentHarnessTest
"$TVGPT_JAVA/java" -cp build/agent-test:build/deps/json-test.jar local.tvgpt.assistant.SessionConcurrencyTest
"$TVGPT_JAVA/java" -cp build/agent-test:build/deps/json-test.jar local.tvgpt.assistant.AgentConversationTest
"$TVGPT_JAVA/java" -cp build/agent-test:build/deps/json-test.jar local.tvgpt.assistant.ConversationToolsTest

mkdir -p build/voice-test
"$TVGPT_JAVA/javac" -d build/voice-test app/src/local/tvgpt/assistant/voice/VoiceTransport.java app/src/local/tvgpt/assistant/{VoiceSession,OrbMotion}.java tests/voice/VoiceSessionTest.java tests/OrbMotionTest.java
"$TVGPT_JAVA/java" -cp build/voice-test local.tvgpt.assistant.VoiceSessionTest
"$TVGPT_JAVA/java" -cp build/voice-test local.tvgpt.assistant.OrbMotionTest
mkdir -p build/feature-test
"$TVGPT_JAVA/javac" -d build/feature-test app/src/local/tvgpt/assistant/settings/{OrbPalette,SpeechPreset}.java tests/PreferencesPolicyTest.java
"$TVGPT_JAVA/java" -cp build/feature-test local.tvgpt.assistant.PreferencesPolicyTest
"$TVGPT_JAVA/javac" -d build/feature-test app/src/local/tvgpt/assistant/data/DataPolicy.java tests/DataPolicyTest.java
"$TVGPT_JAVA/java" -cp build/feature-test local.tvgpt.assistant.DataPolicyTest
"$TVGPT_JAVA/javac" -d build/feature-test app/src/local/tvgpt/assistant/reminders/SchedulePolicy.java tests/SchedulePolicyTest.java
"$TVGPT_JAVA/java" -cp build/feature-test local.tvgpt.assistant.SchedulePolicyTest
mkdir -p build/summary-test
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/summary-test tests/summary-stubs/android/{content/Context,util/Log}.java tests/summary-stubs/SummaryFakes.java tests/summary-stubs/local/tvgpt/assistant/data/ConversationRepository.java app/src/local/tvgpt/assistant/{ConversationSummarizer,CodexRouting}.java tests/ConversationSummarizerTest.java
"$TVGPT_JAVA/java" -cp build/summary-test:build/deps/json-test.jar local.tvgpt.assistant.ConversationSummarizerTest
mkdir -p build/preview-test
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar -d build/preview-test tests/summary-stubs/android/content/Context.java tests/summary-stubs/android/util/Log.java tests/preview-stubs/android/os/{Handler,Looper}.java tests/preview-stubs/PreviewFakes.java tests/preview-stubs/local/tvgpt/assistant/voice/NativeWebRtcTransport.java app/src/local/tvgpt/assistant/voice/{VoiceTransport,PlaybackRecording}.java app/src/local/tvgpt/assistant/settings/SpeechPreset.java app/src/local/tvgpt/assistant/{VoicePreview,PreviewFailure}.java tests/VoicePreviewTest.java
"$TVGPT_JAVA/java" -cp build/preview-test:build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar local.tvgpt.assistant.VoicePreviewTest

"$TVGPT_JAVA/javac" -d build/feature-test app/src/local/tvgpt/assistant/reminders/RemoteDateTime.java tests/RemoteDateTimeTest.java
"$TVGPT_JAVA/java" -cp build/feature-test local.tvgpt.assistant.RemoteDateTimeTest

"$TVGPT_JAVA/javac" -d build/feature-test app/src/local/tvgpt/assistant/voice/PreviewWave.java tests/PreviewWaveTest.java
"$TVGPT_JAVA/java" -cp build/feature-test local.tvgpt.assistant.PreviewWaveTest

"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/feature-test app/src/local/tvgpt/assistant/settings/ModelOptions.java tests/ModelOptionsTest.java
"$TVGPT_JAVA/java" -cp build/feature-test:build/deps/json-test.jar local.tvgpt.assistant.ModelOptionsTest

"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/ImageGenerationProtocol.java tests/ImageGenerationProtocolTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.ImageGenerationProtocolTest
"$TVGPT_JAVA/javac" -cp build/test:build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/SubscriptionImageProtocol.java tests/SubscriptionImageProtocolTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.SubscriptionImageProtocolTest
"$TVGPT_JAVA/javac" -cp build/deps/okhttp.jar:build/deps/okio.jar -d build/test app/src/local/tvgpt/assistant/digests/SubscriptionImageTransport.java tests/SubscriptionImageTransportTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/okhttp.jar:build/deps/okio.jar local.tvgpt.assistant.digests.SubscriptionImageTransportTest
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/DigestPolicy.java tests/DigestPolicyTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.DigestPolicyTest
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/{DigestSchedulePolicy,DigestSlides}.java tests/{DigestSchedulePolicyTest,DigestSlidesTest}.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.DigestSchedulePolicyTest
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.DigestSlidesTest
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/NewsPolicy.java tests/NewsPolicyTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.NewsPolicyTest
"$TVGPT_JAVA/javac" -cp build/test:build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/DigestRunPolicy.java tests/DigestRunPolicyTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.DigestRunPolicyTest
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/digests/{DigestPolicy,DigestToolContract}.java tests/DigestToolsTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.digests.DigestToolsTest
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar -d build/test app/src/local/tvgpt/assistant/digests/{NewsPolicy,NewsSourceClient,DigestResearchProtocol}.java tests/DigestResearchTest.java
"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar local.tvgpt.assistant.digests.DigestResearchTest
mkdir -p build/digest-db-test
"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar -d build/digest-db-test $(find tests/digest-stubs -name '*.java') app/src/local/tvgpt/assistant/CancellationToken.java app/src/local/tvgpt/assistant/data/{AppDatabase,DigestSchema,DigestConfiguration,DigestArchive,DigestRepository}.java app/src/local/tvgpt/assistant/digests/{DigestPolicy,DigestSchedulePolicy,DigestSlides,DigestImages,DigestRunPolicy,NewsPolicy,NewsSourceClient}.java tests/DigestRepositoryTest.java
"$TVGPT_JAVA/java" -cp build/digest-db-test:build/deps/json-test.jar:build/deps/okhttp.jar:build/deps/okio.jar local.tvgpt.assistant.digests.DigestRepositoryTest
"$TVGPT_JAVA/javac" -d build/test app/src/local/tvgpt/assistant/digests/DigestJobHandoff.java tests/DigestJobHandoffTest.java
"$TVGPT_JAVA/java" -cp build/test local.tvgpt.assistant.digests.DigestJobHandoffTest

"$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar local.tvgpt.assistant.RemoteSequenceTest

python3 tests/test_prepare_assets.py

"$TVGPT_JAVA/javac" -cp build/deps/json-test.jar -d build/test app/src/local/tvgpt/assistant/{CancellationToken,CallContext,TaskHarness,TaskEvidence,LiveProtocol,LiveDelivery,Http,RefreshFlight,WakeRecovery,VoicePreflight}.java app/src/local/tvgpt/assistant/voice/ClosingWork.java tests/{CancellationTokenTest,CallContextTest,TaskHarnessTest,TaskEvidenceTest,LiveDeliveryTest,HttpCancellationTest,AuthVoiceLifecycleTest,VoiceQueuePreflightTest}.java
for test in CancellationTokenTest CallContextTest TaskHarnessTest TaskEvidenceTest LiveDeliveryTest HttpCancellationTest AuthVoiceLifecycleTest VoiceQueuePreflightTest; do
 "$TVGPT_JAVA/java" -cp build/test:build/deps/json-test.jar "local.tvgpt.assistant.$test"
done
bash scripts/test-data-repair.sh
bash scripts/test-tv-actions.sh
"$TVGPT_JAVA/javac" -cp "$TVGPT_TOOLCHAIN/android-35/android.jar:build/deps/json-test.jar" -d build/wake-test $(find tests/wake-stubs -name '*.java') app/src/local/tvgpt/assistant/{WakeDetector,WakeRecovery,WakePhrase}.java tests/WakeDetectorRecoveryTest.java
"$TVGPT_JAVA/java" -cp "build/wake-test:$TVGPT_TOOLCHAIN/android-35/android.jar:build/deps/json-test.jar" local.tvgpt.assistant.WakeDetectorRecoveryTest

bash scripts/test-digest-cancellation.sh

bash scripts/test-session-lifecycle.sh
