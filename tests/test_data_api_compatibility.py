"""Check actual production bytecode references against the bundled Android API history."""
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

toolchain = Path(os.environ.get("TVGPT_TOOLCHAIN", "/tmp/tvgpt-launcher/toolchain"))
classes = {}
for entry in ET.parse(toolchain / "android-35/data/api-versions.xml").getroot().findall("class"):
    classes[entry.get("name")] = entry
errors = []
files = [str(file) for directory in sys.argv[1:] for file in Path(directory).rglob("*.class")
         if not file.name.endswith("Test.class")]
bytecode = subprocess.check_output([
    str(toolchain / "usr/lib/jvm/java-17-openjdk-amd64/bin/javap"), "-c", "-p", *files
], text=True)
for owner, name, descriptor in re.findall(
        r"(?:InterfaceMethod|Method) ([\w/$]+)\.([^:]+):(\S+)", bytecode):
    api_class = classes.get(owner)
    if api_class is None:
        continue
    since = int(api_class.get("since", "1"))
    for method in api_class.findall("method"):
        if method.get("name") == name.strip('"') + descriptor:
            since = max(since, int(method.get("since", "1")))
    if since > 31:
        errors.append(f"{owner}.{name}{descriptor} requires API {since}")
if errors:
    raise AssertionError("\n".join(sorted(set(errors))))
print("Production bytecode Android API31 compatibility passed")
