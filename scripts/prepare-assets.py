#!/usr/bin/env python3
"""Stage public resources and optional private audio in the ignored build directory."""
import os
import shutil
from pathlib import Path

VOICES = ('arbor', 'breeze', 'cove', 'ember', 'juniper', 'maple', 'sol', 'spruce', 'vale')
PRIVATE_FILES = ('voice-start.ogg',) + tuple('voice-samples/' + voice + '.m4a' for voice in VOICES)


def prepare_assets(source, private, output):
    source, private, output = (Path(path).resolve() for path in (source, private, output))
    if any(a == b or a in b.parents or b in a.parents
           for a, b in ((source, output), (private, output), (source, private))):
        raise ValueError('Source, private audio and staging directories must be separate')
    if any((source / name).exists() for name in PRIVATE_FILES):
        raise ValueError('Move private audio outside app/assets before building')
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(source, output)
    count = 0
    for name in PRIVATE_FILES:
        audio = private / name
        if audio.is_file():
            destination = output / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(audio, destination)
            count += 1
    return count


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    private = Path(os.environ.get('TVGPT_PRIVATE_ASSETS', str(Path.home() / '.local/share/tvgpt/private-assets'))).expanduser().resolve()
    if private == root or root in private.parents:
        raise SystemExit('TVGPT_PRIVATE_ASSETS must be outside the repository')
    count = prepare_assets(root / 'app/assets', private, root / 'build/assets')
    print(f'Assets staged with {count} optional local audio files; public builds omit proprietary audio.')
