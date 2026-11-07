#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Generate test-only expectations without rooting resources in production code."""
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
required = set()
for folder in res.iterdir():
    if not folder.is_dir():
        continue
    kind = folder.name.split('-')[0]
    for path in folder.iterdir():
        if kind in ('xml', 'raw') and path.name != 'keep.xml':
            required.add((kind, path.name.split('.')[0]))
        if kind == 'values' and path.suffix == '.xml':
            for entry in ET.parse(path).getroot():
                if entry.tag == 'string' and entry.get('name', '').startswith(('subtype_', 'spoken_', 'label_')):
                    required.add(('string', entry.attrib['name']))
for source in (res, root / 'tools/make-keyboard-text/src/main/resources'):
    for path in source.rglob('*.xml'):
        for name in re.findall(r'!string/([A-Za-z0-9_]+)', path.read_text()):
            required.add(('string', name))
output = root / 'app/build/generated/resource-smoke-assets/resources.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(sorted(required)))
print(f'Generated {len(required)} runtime resource expectations')
