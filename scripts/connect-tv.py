#!/usr/bin/env python3
"""Rebind this app after an APK update; preserve unrelated accessibility services."""
import argparse,os,re,shlex,subprocess,time
adb=os.environ.get('ADB','/home/gleb/.local/share/android/platform-tools/adb')
parser=argparse.ArgumentParser(description='Check bindings without opening the app; explicit --foreground permits setup/rebinding.')
parser.add_argument('serial',nargs='?',default='192.168.3.5:5555')
parser.add_argument('--foreground',action='store_true',help='Allow opening settings and rebinding during an authorized setup window')
options=parser.parse_args()
serial=options.serial
def shell(*args):
 return subprocess.check_output([adb,'-s',serial,'shell',shlex.join(args)],text=True).strip()
def bound_services():
 records=shell('dumpsys','activity','services','local.tvgpt.assistant').split('* ServiceRecord')
 return all(any('/.'+name+'}' in record.split('\n',1)[0] and 'app=ProcessRecord{' in record for record in records) for name in ('AssistantService','TvAccessibility'))
# Only change OEM operations that the firmware actually exposes for this package.
startup_changed=False
for operation in ('APP_AUTO_START','APP_ASSOC_START'):
 try: current=shell('cmd','appops','get','--user','0','local.tvgpt.assistant',operation)
 except subprocess.CalledProcessError: continue
 if not re.search(r'\b'+operation+r':\s+(allow|ignore|deny|default)\b',current):continue
 if operation+': allow' not in current:
  shell('cmd','appops','set','--user','0','local.tvgpt.assistant',operation,'allow')
  if operation+': allow' not in shell('cmd','appops','get','--user','0','local.tvgpt.assistant',operation):
   raise SystemExit('OEM startup permission did not apply: '+operation)
  startup_changed=True
if startup_changed:shell('cmd','appops','write-settings')
component='local.tvgpt.assistant/.AssistantService'
if (shell('settings','get','secure','assistant')==component
    and shell('settings','get','secure','voice_interaction_service')==component and bound_services()):
 if options.foreground:shell('am','start','-n','local.tvgpt.assistant/.MainActivity','--es','section','SETTINGS')
 print('TV GPT assistant and accessibility already bound; existing bindings preserved; no automatic screen switch.')
 raise SystemExit(0)
if not options.foreground:
 raise SystemExit('Bindings are not ready. No app window was opened and bindings were left intact. Use --foreground only during an authorized setup window.')
# Never cancel a newly queued foreground start during an APK handover: TCL can retain its timeout.
for attempt in range(40):
 service=shell('dumpsys','activity','services','local.tvgpt.assistant/.AudioKeepAlive')
 if 'fgRequired=true' not in service or 'isForeground=true' in service:break
 time.sleep(.1)
else:raise SystemExit('Audio foreground start is still pending; bindings left intact. Retry when ready.')
shell('am','force-stop','com.google.android.katniss')
original=shell('settings','get','secure','enabled_accessibility_services')
others=[p for p in original.split(':') if p and p!='null' and p!='local.tvgpt.assistant/.TvAccessibility']
shell('settings','put','secure','enabled_accessibility_services',':'.join(others))
shell('settings','delete','secure','voice_interaction_service')
time.sleep(.5)
shell('am','start','-n','local.tvgpt.assistant/.MainActivity','--ez','setup','true')
shell('cmd','role','add-role-holder','--user','0','android.app.role.ASSISTANT','local.tvgpt.assistant','0')
shell('settings','put','secure','assistant','local.tvgpt.assistant/.AssistantService')
shell('settings','put','secure','voice_interaction_service','local.tvgpt.assistant/.AssistantService')
shell('settings','put','secure','enabled_accessibility_services',':'.join(others+['local.tvgpt.assistant/.TvAccessibility']))
shell('settings','put','secure','accessibility_enabled','1')
for attempt in range(24):
 services=shell('dumpsys','activity','services','local.tvgpt.assistant')
 records=services.split('* ServiceRecord')
 bound=all(any('/.'+name+'}' in record.split('\n',1)[0] and 'app=ProcessRecord{' in record for record in records) for name in ('AssistantService','TvAccessibility'))
 if bound:break
 time.sleep(.25)
else:raise SystemExit('Assistant/accessibility services did not bind; leave settings open and retry')
shell('am','start','-n','local.tvgpt.assistant/.MainActivity','--es','section','SETTINGS')
print('TV GPT assistant and accessibility services rebound and verified.')
