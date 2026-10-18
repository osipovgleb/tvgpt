#!/usr/bin/env python3
"""Enforce Java boundaries not handled by AI Slop 0.14's import analyser."""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = root / 'app/src/local/tvgpt/assistant'
errors = []
manifest = ET.parse(root / 'app/AndroidManifest.xml').getroot()
permissions = {item.get('{http://schemas.android.com/apk/res/android}name')
               for item in manifest.findall('uses-permission')}
for permission in ('INTERNET', 'ACCESS_NETWORK_STATE', 'RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS'):
    if 'android.permission.' + permission not in permissions:
        errors.append('Native WebRTC requires manifest permission: ' + permission)
for path in source.rglob('*.java'):
    content = path.read_text(encoding='utf-8')
    forbidden = [r'\bandroid\.webkit\.']
    if path.name in ('VoiceSession.java', 'OrbMotion.java'):
        forbidden.extend([r'\bandroid\.', r'\borg\.webrtc\.', r'\bokhttp3\.'])
    if path.name == 'VoicePanel.java':
        forbidden.extend([r'\borg\.webrtc\.', r'\bokhttp3\.', r'\bnew\s+LiveConnection\b'])
    if path.parent.name == 'voice':
        forbidden.extend([r'\bandroid\.(?:app|view|widget)\.',
                          r'\blocal\.tvgpt\.assistant\.(?!voice\b)'])
    for pattern in forbidden:
        for match in re.finditer(pattern, content):
            line = content.count('\n', 0, match.start()) + 1
            errors.append(f'{path.relative_to(root)}:{line}: forbidden dependency {match.group()}')
if errors:
    raise SystemExit('\n'.join(errors))
print('Java architecture: native voice, presentation and controller boundaries passed')
