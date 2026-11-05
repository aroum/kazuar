#!/usr/bin/env python3
"""Run on CI: prove that normal preBuild regenerates both declared outputs after input edits."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
text_template = next((root / 'tools/make-keyboard-text/src/main/resources').rglob('KeyboardTextsTable.tmpl'))
emoji_template = root / 'tools/make-emoji-keys/src/main/resources/values/emoji-categories.tmpl'
text_output = root / 'tools/make-keyboard-text/build/generated/java/io/github/aroum/kazuar/inputmethod/keyboard/internal/KeyboardTextsTable.java'
emoji_output = root / 'tools/make-emoji-keys/build/generated/res/values/emoji-categories.xml'

def generate():
    subprocess.run(['./gradlew', ':app:preBuild', '--console=plain'], cwd=root, check=True)

generate()
originals = {path: path.read_bytes() for path in (text_template, emoji_template)}
marker = 'GENERATOR_INPUT_REGRESSION_CHECK'
try:
    text_template.write_bytes(originals[text_template] + ('\n// ' + marker + '\n').encode())
    emoji_template.write_bytes(originals[emoji_template] + ('\n<!-- ' + marker + ' -->\n').encode())
    generate()
    assert marker in text_output.read_text(), 'Java output did not follow template edit'
    assert marker in emoji_output.read_text(), 'Emoji output did not follow template edit'
finally:
    for path, content in originals.items():
        path.write_bytes(content)
    generate()

import re
locales = re.findall(r'private static final String\[\] TEXTS_(\w+)\s*=', text_output.read_text())
assert locales and all(name == 'DEFAULT' or name.split('_')[0] in ('en', 'ru') for name in locales), locales
assert marker not in text_output.read_text() and marker not in emoji_output.read_text()
subprocess.run(['git', 'diff', '--exit-code'], cwd=root, check=True)
print('Both generators follow input changes; outputs are clean and language tables are English/Russian only.')
