"""The post-update helper must not steal the viewer's foreground app."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

with tempfile.TemporaryDirectory() as folder:
    root = Path(folder)
    adb = root / "adb"
    log = root / "calls.jsonl"
    adb.write_text('''#!/usr/bin/env python3
import json,os,shlex,sys
args=shlex.split(sys.argv[-1])
with open(os.environ['TVGPT_TEST_CALLS'],'a') as f:f.write(json.dumps(args)+'\\n')
component='local.tvgpt.assistant/.AssistantService'
if args[:3]==['settings','get','secure']:print(component)
elif args[:3]==['cmd','appops','get']:print(args[-1]+': allow')
elif args[:3]==['dumpsys','activity','services'] and os.environ['TVGPT_TEST_BOUND']=='1':
 for name in ['AssistantService','TvAccessibility']:
  print('* ServiceRecord{u0 local.tvgpt.assistant/.'+name+'}\\n app=ProcessRecord{running}')
''')
    adb.chmod(0o755)
    for bound, flags, success, starts in [(True, [], True, 0), (False, [], False, 0), (True, ['--foreground'], True, 1)]:
        log.write_text("")
        env = dict(os.environ, ADB=str(adb), TVGPT_TEST_CALLS=str(log), TVGPT_TEST_BOUND=str(int(bound)))
        result = subprocess.run(['python3', 'scripts/connect-tv.py', 'test-tv', *flags], env=env, capture_output=True, text=True)
        assert (result.returncode == 0) == success, result.stderr
        calls = [json.loads(line) for line in log.read_text().splitlines()]
        assert sum(call[:2] == ['am', 'start'] for call in calls) == starts, calls
        if not flags:
            assert not any(call[:2] in (['settings', 'put'], ['settings', 'delete'], ['am', 'force-stop']) for call in calls), calls
print('connect-tv: silent normal update, untouched failed bindings, explicit foreground setup passed')
