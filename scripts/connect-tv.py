#!/usr/bin/env python3
"""Rebind this app after an APK update; preserve unrelated accessibility services."""
import os,shlex,subprocess,sys,time
adb=os.environ.get('ADB','/home/gleb/.local/share/android/platform-tools/adb')
serial=sys.argv[1] if len(sys.argv)>1 else '192.168.3.5:5555'
def shell(*args):
 return subprocess.check_output([adb,'-s',serial,'shell',shlex.join(args)],text=True).strip()
def bound_services():
 records=shell('dumpsys','activity','services','local.tvgpt.assistant').split('* ServiceRecord')
 return all(any('/.'+name+'}' in record.split('\n',1)[0] and 'app=ProcessRecord{' in record for record in records) for name in ('AssistantService','TvAccessibility'))
component='local.tvgpt.assistant/.AssistantService'
if (shell('settings','get','secure','assistant')==component
    and shell('settings','get','secure','voice_interaction_service')==component and bound_services()):
 shell('am','start','-n','local.tvgpt.assistant/.MainActivity','--es','section','SETTINGS')
 print('TV GPT assistant and accessibility already bound; existing bindings preserved.')
 raise SystemExit(0)
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
