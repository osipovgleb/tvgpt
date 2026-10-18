"""On-device regression check. Requires an open TV GPT conversation; briefly rebinds its service."""
import os
import shlex
import subprocess
import sys
import time
from pathlib import Path

adb = os.environ.get('ADB', '/home/gleb/.local/share/android/platform-tools/adb')
serial = sys.argv[1] if len(sys.argv) > 1 else '192.168.3.5:5555'
base = [adb, '-s', serial]

def shell(*args):
    return subprocess.check_output(base + ['shell', shlex.join(args)], text=True)

component = 'local.tvgpt.assistant/.AssistantService'
assert shell('settings', 'get', 'secure', 'voice_interaction_service').strip() == component
assert 'TYPE_ACCESSIBILITY_OVERLAY' in shell('dumpsys', 'accessibility'), 'Open a conversation first'
path = Path('build/rebind-check.log')
with path.open('w') as output:
    log = subprocess.Popen(base + ['logcat', '-T', '1', '-s', 'TVGPTAudio:I', 'TVGPTWake:I', 'AndroidRuntime:E', '*:S'], stdout=output)
    try:
        shell('settings', 'delete', 'secure', 'voice_interaction_service')
        time.sleep(.7)
        shell('settings', 'put', 'secure', 'voice_interaction_service', component)
        time.sleep(12)
    finally:
        # Preserve the selected assistant even if a check is interrupted.
        shell('settings', 'put', 'secure', 'voice_interaction_service', component)
        log.terminate()
        log.wait(timeout=5)
text = path.read_text()
assert 'Listening locally:' not in text, 'Idle capture restarted during an active conversation'
assert '"state":"connected"' in text and '"mic":["live"]' in text, 'Live audio did not survive rebind'
assert 'FATAL EXCEPTION' not in text, 'Application crashed'
print('Assistant rebind passed: Live capture survives, idle microphone stays stopped')
