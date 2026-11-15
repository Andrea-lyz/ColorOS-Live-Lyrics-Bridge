#!/usr/bin/env python3
"""Method-body equivalence check for pure-move refactors (plan §15.3, E-0 ④).

Compares every method/constructor body under app/src/main/java between two git refs (or a ref
and the working tree). A method is identified by name + parameter types, wherever it lives, so a
method moved to another class or file still matches. Bodies are compared with comments removed,
whitespace collapsed and selected qualifiers stripped ("this.", "LockscreenLyricsModule." by
default), which is what the plan calls "ignoring indentation and qualified-name prefixes".

Usage (from the Bridge repository root):
    python scripts/guard/method-bodies.py --base HEAD                 # HEAD vs working tree
    python scripts/guard/method-bodies.py --base a8b177f --head 87b7645
    python scripts/guard/method-bodies.py --base HEAD --strip host.    # also ignore "host."

Exit code 0 when no method body changed or disappeared; 1 otherwise (added methods are allowed:
a move usually adds small accessors). Changed bodies are printed as unified diffs.
"""
import argparse
import difflib
import os
import re
import subprocess
import sys
from collections import defaultdict

ROOT = 'app/src/main/java'
TYPE_HEADER = re.compile(r'\b(class|interface|enum|record)\s+([A-Za-z_$][\w$]*)')
ANNOTATION = re.compile(r'@[\w$.]+\s*(\([^()]*\))?')
LAST_IDENT = re.compile(r'([A-Za-z_$][\w$]*)\s*$')


def git(*args):
    return subprocess.run(['git', *args], check=True, capture_output=True).stdout


def sources(ref):
    """{path: text} of every .java file under ROOT at ref ('WORKTREE' = files on disk)."""
    out = {}
    if ref == 'WORKTREE':
        for base, _, files in os.walk(ROOT):
            for name in files:
                if name.endswith('.java'):
                    path = os.path.join(base, name).replace(os.sep, '/')
                    with open(path, 'rb') as f:
                        out[path] = f.read().decode('utf-8')
        return out
    for path in git('ls-tree', '-r', '--name-only', ref, '--', ROOT).decode().split('\n'):
        if path.endswith('.java'):
            out[path] = git('show', f'{ref}:{path}').decode('utf-8')
    return out


def blank(s):
    """Comments and literal contents -> spaces, same length (mirrors JavaOutline.blank)."""
    out = list(s)
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        nxt = s[i + 1] if i + 1 < n else ''
        if c == '/' and nxt == '/':
            while i < n and s[i] != '\n':
                out[i] = ' '; i += 1
        elif c == '/' and nxt == '*':
            while i < n and not (s[i] == '*' and i + 1 < n and s[i + 1] == '/'):
                if s[i] != '\n': out[i] = ' '
                i += 1
            if i < n:
                out[i] = ' '; out[i + 1] = ' '; i += 2
        elif s.startswith('"""', i):
            i += 3
            while i < n and not s.startswith('"""', i):
                if s[i] == '\\': out[i] = ' '; i += 1
                if i < n and s[i] != '\n': out[i] = ' '
                i += 1
            i += 3
        elif c in '"\'':
            i += 1
            while i < n and s[i] != c and s[i] != '\n':
                if s[i] == '\\' and i + 1 < n: out[i] = ' '; i += 1
                out[i] = ' '; i += 1
            i += 1
        else:
            i += 1
    return ''.join(out)


def strip_comments(s):
    """Source with comments removed but literals kept (bodies compare literal text too)."""
    code = blank(s)
    out = []
    i, n = 0, len(s)
    while i < n:
        if s.startswith('//', i) and code[i] == ' ' and code.startswith('  ', i):
            while i < n and s[i] != '\n': i += 1
        elif s.startswith('/*', i) and code.startswith('  ', i):
            end = s.find('*/', i + 2)
            i = n if end < 0 else end + 2
        else:
            out.append(s[i]); i += 1
    return ''.join(out)


def methods(text):
    """[(name, params, type, body, declaration)] for methods/constructors directly in type bodies."""
    code = blank(text)
    result = []
    frames = []          # 'TYPE' | 'ENUM' | 'OTHER' | 'INIT'
    owners = []          # (start, name, params, type) for method frames, else None
    types = []
    stmt = 0
    enum_pending = False
    for i, c in enumerate(code):
        top = frames[-1] if frames else None
        in_type = top in (None, 'TYPE', 'ENUM')
        if c == '{':
            header = ANNOTATION.sub(' ', code[stmt:i]).strip()
            m = TYPE_HEADER.search(header)
            if in_type and '=' not in header and 'new ' not in header and m:
                frames.append('ENUM' if m.group(1) == 'enum' else 'TYPE'); owners.append(None)
                types.append(m.group(2)); enum_pending = m.group(1) == 'enum'; stmt = i + 1
            elif top == 'ENUM' and enum_pending:
                frames.append('OTHER'); owners.append(None)
            elif in_type and '=' in header:
                frames.append('INIT'); owners.append(None)
            elif in_type and '(' in header:
                before, after = header.split('(', 1)
                name = LAST_IDENT.search(before)
                params = after.rsplit(')', 1)[0]
                start = stmt + len(code[stmt:i]) - len(code[stmt:i].lstrip())
                frames.append('OTHER'); owners.append((start, i, name.group(1) if name else '?', params, types[-1] if types else '?'))
            else:
                frames.append('OTHER'); owners.append(None)
        elif c == '}':
            if not frames: continue
            closed = frames.pop(); owner = owners.pop()
            if owner:
                start, brace, name, params, typ = owner
                # Compare from the opening brace: a move may change modifiers, never the body.
                result.append((name, params, typ, text[brace:i + 1], text[start:i + 1]))
            if closed in ('TYPE', 'ENUM'): types.pop()
            now = frames[-1] if frames else None
            if now in (None, 'TYPE', 'ENUM') and closed != 'INIT': stmt = i + 1
        elif c == ';' and in_type:
            if top == 'ENUM' and enum_pending: enum_pending = False
            stmt = i + 1
    return result


def param_types(params):
    """'final String a, List<T> b' -> 'String,List<T>' (names and modifiers dropped)."""
    parts, depth, cur = [], 0, ''
    for ch in params:
        if ch in '<(': depth += 1
        if ch in '>)': depth -= 1
        if ch == ',' and depth == 0: parts.append(cur); cur = ''
        else: cur += ch
    if cur.strip(): parts.append(cur)
    out = []
    for p in parts:
        p = ANNOTATION.sub(' ', p).replace('final ', ' ').strip()
        p = re.sub(r'\s+[A-Za-z_$][\w$]*$', '', p)  # drop the parameter name
        out.append(re.sub(r'\s+', '', p))
    return ','.join(out)


def normalize(body, strip):
    body = strip_comments(body)
    for prefix in strip:
        body = re.sub(r'(?<![\w$.])' + re.escape(prefix), '', body)
    return re.sub(r'\s+', ' ', body).strip()


def index(ref, strip):
    table = defaultdict(list)
    for path, text in sorted(sources(ref).items()):
        for name, params, typ, body, declaration in methods(text):
            key = f'{name}({param_types(params)})'
            table[key].append((normalize(body, strip), f'{path}:{typ}', declaration))
    return table


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--base', required=True, help='git ref before the move')
    ap.add_argument('--head', default='WORKTREE', help='git ref after the move (default: working tree)')
    ap.add_argument('--strip', action='append', default=[], help='extra qualifier prefix to ignore, e.g. host.')
    args = ap.parse_args()
    strip = ['this.', 'LockscreenLyricsModule.'] + args.strip
    before, after = index(args.base, strip), index(args.head, strip)
    unchanged = moved = 0
    changed, removed = [], []
    for key, olds in sorted(before.items()):
        news = list(after.get(key, []))
        for norm, where, body in olds:
            match = next((n for n in news if n[0] == norm), None)
            if match:
                news.remove(match)
                if match[1] == where: unchanged += 1
                else: moved += 1
            elif news:
                new = news.pop(0)
                changed.append((key, where, new[1], body, new[2]))
            else:
                removed.append((key, where))
    added = sum(max(0, len(after[k]) - len(before.get(k, []))) for k in after)
    print(f'methods: {unchanged} unchanged, {moved} moved unchanged, {len(changed)} changed, '
          f'{len(removed)} removed, {added} added  ({args.base} -> {args.head})')
    for key, old_where, new_where, old, new in changed:
        print(f'\n## changed {key}  {old_where} -> {new_where}')
        sys.stdout.writelines(difflib.unified_diff(
            old.splitlines(True), new.splitlines(True), 'before', 'after', n=1))
    for key, where in removed:
        print(f'## removed {key}  {where}')
    return 1 if changed or removed else 0


if __name__ == '__main__':
    sys.exit(main())
