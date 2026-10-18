#!/usr/bin/env python3
"""Fetch checksum-pinned Java and ARM32 native transport dependencies."""
import hashlib
import json
import zipfile
from pathlib import Path
from urllib.request import urlopen

root = Path(__file__).resolve().parents[1]
destination = root / 'build' / 'deps'
destination.mkdir(parents=True, exist_ok=True)
for item in json.loads((root / 'dependencies.json').read_text()):
    target = destination / (item['name'] + '.' + item.get('format','jar'))
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != item['sha256']:
        with urlopen(item['url'], timeout=120) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != item['sha256']:
            raise SystemExit('Checksum mismatch: ' + item['name'])
        target.write_bytes(data)
        print('Fetched ' + item['name'])
    if item.get('format') == 'aar':
        folder = destination / item['name']
        folder.mkdir(exist_ok=True)
        with zipfile.ZipFile(target) as archive:
            native = item.get('nativeLibraries', ['libvosk.so' if item['name'] == 'vosk' else 'libjnidispatch.so'])
            for name in ['classes.jar', *['jni/armeabi-v7a/' + library for library in native]]:
                archive.extract(name, folder)
