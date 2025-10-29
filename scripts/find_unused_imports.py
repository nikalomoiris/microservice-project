#!/usr/bin/env python3
import re
import os
import json

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

IMPORT_RE = re.compile(r'^\s*import\s+([^;]+);', re.MULTILINE)
PACKAGE_RE = re.compile(r'^\s*package\s+[^;]+;', re.MULTILINE)
BLOCK_COMMENT_RE = re.compile(r'/\*.*?\*/', re.DOTALL)
LINE_COMMENT_RE = re.compile(r'//.*?$', re.MULTILINE)
STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"', re.DOTALL)

candidates = []

for dirpath, dirnames, filenames in os.walk(ROOT):
    # skip build output and .git
    if any(part in ('target', '.git', 'node_modules', 'build') for part in dirpath.split(os.sep)):
        continue
    for fname in filenames:
        if not fname.endswith('.java'):
            continue
        fpath = os.path.join(dirpath, fname)
        try:
            with open(fpath, 'r', encoding='utf-8') as f:
                src = f.read()
        except Exception:
            continue
        imports = IMPORT_RE.findall(src)
        if not imports:
            continue
        # prepare searchable source: remove import lines, package, comments and strings
        src_no_imports = IMPORT_RE.sub('', src)
        src_no_imports = PACKAGE_RE.sub('', src_no_imports)
        src_no_imports = BLOCK_COMMENT_RE.sub('', src_no_imports)
        src_no_imports = LINE_COMMENT_RE.sub('', src_no_imports)
        src_no_imports = STRING_RE.sub('', src_no_imports)
        for imp in imports:
            imp = imp.strip()
            if imp.startswith('static '):
                continue
            if imp.endswith('.*'):
                continue
            simple = imp.split('.')[-1]
            # If aliasing via as is not Java; ignore
            # Search for word boundary occurrences of simple name
            if re.search(r'\b' + re.escape(simple) + r'\b', src_no_imports):
                continue
            # not found -> candidate unused
            candidates.append({'file': fpath, 'import': imp, 'simple': simple})

# Group by file
by_file = {}
for c in candidates:
    by_file.setdefault(c['file'], []).append(c['import'])

out = {'candidates': candidates, 'by_file': by_file}
print(json.dumps(out, indent=2))

# Also write to file for inspection
with open(os.path.join(ROOT, 'scripts', 'unused_imports_report.json'), 'w', encoding='utf-8') as f:
    json.dump(out, f, indent=2)

print('\nReport written to scripts/unused_imports_report.json')
