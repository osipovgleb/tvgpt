#!/usr/bin/env python3
"""Read-only ADB snapshot, including renderers owned by TV GPT and interval CPU samples."""
import argparse
import datetime
import json
import os
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='Explicit TV ADB serial')
parser.add_argument('--label', required=True, help='For example: idle, live-listening, live-speaking')
parser.add_argument('--output', required=True, help='Local JSON report path')
parser.add_argument('--samples', type=int, default=4, choices=range(2, 16), metavar='2..15')
parser.add_argument('--interval', type=int, default=2, choices=range(1, 5), metavar='1..4')
args = parser.parse_args()


def read(*command, timeout=25):
    reply = subprocess.run([os.environ.get('ADB', 'adb'), '-s', args.serial, 'shell', *command],
                           capture_output=True, text=True, timeout=timeout, check=False)
    if reply.returncode:
        raise RuntimeError(f'ADB read failed: {command}: {reply.stderr.strip()}')
    return reply.stdout


owners = read('dumpsys', 'activity', 'processes')
pids = []
for block in re.split(r'(?=^\s*\*APP\*)', owners, flags=re.MULTILINE):
    if not re.search(r'packageList=\{[^}]*\blocal\.tvgpt\.assistant\b', block):
        continue
    match = re.search(r'ProcessRecord\{\S+\s+(\d+):', block)
    if match:
        pids.append(match[1])
if not pids:
    raise SystemExit('TV GPT is not running; no zero-load result will be reported')
report = {
    'label': args.label,
    'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'owned_pids': pids,
    'cpu_interval_seconds': args.interval,
    'cpu_note': 'Discard first top sample; 100% means one CPU core. Compare identical app states.',
    'cpu_top': read('top', '-b', '-n', str(args.samples), '-d', str(args.interval),
                    '-p', ','.join(pids), '-o', 'PID,%CPU,RES,NAME',
                    timeout=args.samples * args.interval + 20),
    'memory_by_pid': {pid: read('dumpsys', 'meminfo', pid) for pid in pids},
    'processes': read('ps', '-A', '-o', 'PID,PPID,RSS,NAME'),
}
if any('TOTAL PSS:' not in memory for memory in report['memory_by_pid'].values()):
    raise SystemExit('An owned process disappeared during measurement; discard this sample')
remaining = set(read('pidof', 'local.tvgpt.assistant').split())
if not remaining.intersection(pids):
    raise SystemExit('TV GPT restarted during measurement; discard this sample')
with open(args.output, 'w', encoding='utf-8') as target:
    json.dump(report, target, ensure_ascii=False, indent=2)
print(args.output)
