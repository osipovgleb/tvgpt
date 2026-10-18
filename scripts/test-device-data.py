#!/usr/bin/env python3
"""Fresh SQLite/preferences checks in a self-targeted package; never restarts TV GPT."""
import argparse
import os
from pathlib import Path
import subprocess

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial',required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
adb=os.environ.get('ADB','/home/gleb/.local/share/android/platform-tools/adb')
package='local.tvgpt.sectionschecks'
logs=root/'build/data-device-results'
logs.mkdir(parents=True,exist_ok=True)
def run(*command,check=True):
 return subprocess.run([adb,'-s',args.serial,*command],text=True,capture_output=True,check=check,timeout=45)
# This package exists only for these tests. A fresh install exercises onCreate, not just migration.
run('uninstall',package,check=False)
try:
 run('install','--no-incremental','-r',str(root/'build/tvgpt-data-tests.apk'))
 for mode in ['features','write','verify','preferences']:
  result=run('shell','am','instrument','-w','-e','mode',mode,package+'/local.tvgpt.assistant.DataCheck')
  (logs/(mode+'.log')).write_text(result.stdout+result.stderr)
  if 'INSTRUMENTATION_CODE: -1' not in result.stdout or 'INSTRUMENTATION_RESULT: error=' in result.stdout:
   raise SystemExit(mode+' failed: '+result.stdout)
  print(mode+': passed')
finally:
 run('uninstall',package,check=False)
