#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
import hashlib
import urllib.request
from pathlib import Path
path = Path('build/google-java-format.jar')
checksum = '16b2a1ee938686c8b1d88abf19eb83dfd0d623cee9de6fc6d09980214f816d3f'
if not path.exists():
    path.parent.mkdir(parents=True, exist_ok=True)
    url = 'https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.22.0/google-java-format-1.22.0-all-deps.jar'
    path.write_bytes(urllib.request.urlopen(url, timeout=30).read())
if hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
    raise SystemExit('Java formatter checksum mismatch')
PY
TVGPT_TOOLCHAIN=${TVGPT_TOOLCHAIN:-/tmp/tvgpt-launcher/toolchain}
TVGPT_FORMAT_JAVA="$TVGPT_TOOLCHAIN/usr/lib/jvm/java-17-openjdk-amd64/bin/java"
TVGPT_FORMAT_MODE=(--replace)
if [[ "${1:-}" == "--check" ]]; then
 TVGPT_FORMAT_MODE=(--dry-run --set-exit-if-changed)
elif [[ $# -gt 0 ]]; then
 echo 'Usage: bash scripts/format-java.sh [--check]' >&2
 exit 2
fi
find app/src tests -name '*.java' -print0 | xargs -0 "$TVGPT_FORMAT_JAVA" -jar build/google-java-format.jar "${TVGPT_FORMAT_MODE[@]}"
