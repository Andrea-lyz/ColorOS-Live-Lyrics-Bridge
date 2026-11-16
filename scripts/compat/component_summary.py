"""C-05 structural summary of a SystemUI / SystemUIPlugin APK (plan section 16.3).

Reads the APK's DEX files and writes only what the Bridge's resolvers look at:

* identity: package, versionName/Code (from the binary manifest), SHA-256 of the APK;
* member signatures (names, access flags, descriptor types; no code) of the classes the Bridge
  names in its sources, of classes matched by the DexKit anchor queries, and of their
  superclass chains inside the APK;
* the result of each DexKit-style query in the anchor spec: which classes use every string of an
  anchor set (DexKit ``usingEqStrings``) and which methods match an invoke query.

No APK bytes, code, resources or strings beyond the anchor results are written. The JVM test
``ComponentSampleContractTest`` rebuilds the classes from this summary and runs the Bridge's real
resolvers against them.

Usage: python -I component_summary.py <apk> <label> <bridge-repo> <out.json>
"""
import hashlib
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

SCHEMA = 1


def uleb(data, off):
    result = 0
    shift = 0
    while True:
        b = data[off]
        off += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, off
        shift += 7


def _insn_units():
    size = [1] * 256
    def put(lo, hi, n):
        for op in range(lo, hi + 1):
            size[op] = n
    put(0x02, 0x02, 2); put(0x03, 0x03, 3); put(0x05, 0x05, 2); put(0x06, 0x06, 3)
    put(0x08, 0x08, 2); put(0x09, 0x09, 3); put(0x13, 0x13, 2); put(0x14, 0x14, 3)
    put(0x15, 0x16, 2); put(0x17, 0x17, 3); put(0x18, 0x18, 5); put(0x19, 0x1A, 2)
    put(0x1B, 0x1B, 3); put(0x1C, 0x1C, 2); put(0x1F, 0x20, 2); put(0x22, 0x23, 2)
    put(0x24, 0x26, 3); put(0x29, 0x29, 2); put(0x2A, 0x2C, 3); put(0x2D, 0x3D, 2)
    put(0x44, 0x6D, 2); put(0x6E, 0x72, 3); put(0x74, 0x78, 3); put(0x90, 0xAF, 2)
    put(0xD0, 0xE2, 2); put(0xFA, 0xFB, 4); put(0xFC, 0xFD, 3); put(0xFE, 0xFF, 2)
    return size


UNITS = _insn_units()
INVOKES = set(range(0x6E, 0x73)) - {0x73} | set(range(0x74, 0x79))


class Dex:
    def __init__(self, data):
        self.d = data
        (self.string_ids_size, self.string_ids_off, self.type_ids_size, self.type_ids_off,
         self.proto_ids_size, self.proto_ids_off, self.field_ids_size, self.field_ids_off,
         self.method_ids_size, self.method_ids_off, self.class_defs_size,
         self.class_defs_off) = struct.unpack_from('<12I', data, 56)
        self.strings = []
        for i in range(self.string_ids_size):
            off = struct.unpack_from('<I', data, self.string_ids_off + 4 * i)[0]
            _, off = uleb(data, off)
            self.strings.append(data[off:data.index(b'\x00', off)].decode('utf-8', 'replace'))
        self.types = [self.strings[struct.unpack_from('<I', data, self.type_ids_off + 4 * i)[0]]
                      for i in range(self.type_ids_size)]
        self.protos = []
        for i in range(self.proto_ids_size):
            _, ret, params_off = struct.unpack_from('<III', data, self.proto_ids_off + 12 * i)
            params = []
            if params_off:
                n = struct.unpack_from('<I', data, params_off)[0]
                params = [self.types[struct.unpack_from('<H', data, params_off + 4 + 2 * j)[0]]
                          for j in range(n)]
            self.protos.append((self.types[ret], tuple(params)))
        self.fields = [struct.unpack_from('<HHI', data, self.field_ids_off + 8 * i)
                       for i in range(self.field_ids_size)]
        self.methods = [struct.unpack_from('<HHI', data, self.method_ids_off + 8 * i)
                        for i in range(self.method_ids_size)]

    def method_ref(self, idx):
        cls, proto, name = self.methods[idx]
        ret, params = self.protos[proto]
        return self.types[cls], self.strings[name], params, ret

    def scan_code(self, off):
        """Strings loaded and methods invoked by one code item."""
        d = self.d
        insns_size = struct.unpack_from('<I', d, off + 12)[0]
        base = off + 16
        strings, invokes = set(), []
        pc = 0
        while pc < insns_size:
            unit = struct.unpack_from('<H', d, base + 2 * pc)[0]
            op = unit & 0xFF
            if op == 0x00 and unit != 0:
                if unit == 0x0100:
                    n = struct.unpack_from('<H', d, base + 2 * pc + 2)[0]
                    pc += n * 2 + 4
                elif unit == 0x0200:
                    n = struct.unpack_from('<H', d, base + 2 * pc + 2)[0]
                    pc += n * 4 + 2
                elif unit == 0x0300:
                    width = struct.unpack_from('<H', d, base + 2 * pc + 2)[0]
                    n = struct.unpack_from('<I', d, base + 2 * pc + 4)[0]
                    pc += (n * width + 1) // 2 + 4
                else:
                    pc += 1
                continue
            if op == 0x1A:
                strings.add(self.strings[struct.unpack_from('<H', d, base + 2 * pc + 2)[0]])
            elif op == 0x1B:
                strings.add(self.strings[struct.unpack_from('<I', d, base + 2 * pc + 2)[0]])
            elif op in INVOKES:
                invokes.append(self.method_ref(struct.unpack_from('<H', d, base + 2 * pc + 2)[0]))
            pc += UNITS[op]
        return strings, invokes

    def classes(self):
        d = self.d
        for i in range(self.class_defs_size):
            cd = struct.unpack_from('<8I', d, self.class_defs_off + 32 * i)
            name = self.types[cd[0]]
            sup = self.types[cd[2]] if cd[2] != 0xFFFFFFFF else None
            interfaces = []
            if cd[3]:
                n = struct.unpack_from('<I', d, cd[3])[0]
                interfaces = [self.types[struct.unpack_from('<H', d, cd[3] + 4 + 2 * j)[0]]
                              for j in range(n)]
            entry = {'access': cd[1], 'super': sup, 'interfaces': interfaces,
                     'fields': [], 'methods': [], '_code': []}
            if cd[6]:
                off = cd[6]
                sf, off = uleb(d, off); inf, off = uleb(d, off)
                dm, off = uleb(d, off); vm, off = uleb(d, off)
                for count in (sf, inf):
                    idx = 0
                    for _ in range(count):
                        diff, off = uleb(d, off); acc, off = uleb(d, off); idx += diff
                        _, ftype, fname = self.fields[idx]
                        entry['fields'].append([self.strings[fname], self.types[ftype], acc])
                for count in (dm, vm):
                    idx = 0
                    for _ in range(count):
                        diff, off = uleb(d, off); acc, off = uleb(d, off); code, off = uleb(d, off)
                        idx += diff
                        _, mname, params, ret = self.method_ref(idx)
                        entry['methods'].append([mname, list(params), ret, acc])
                        entry['_code'].append(code)
            yield name, entry


def load(apk):
    classes = {}
    with zipfile.ZipFile(apk) as z:
        for n in sorted(z.namelist()):
            if re.fullmatch(r'classes\d*\.dex', n):
                dex = Dex(z.read(n))
                for name, entry in dex.classes():
                    if name not in classes:
                        entry['_dex'] = dex
                        classes[name] = entry
    return classes


def manifest_identity(apk):
    """package/versionName/versionCode from the binary AndroidManifest.xml (AXML)."""
    with zipfile.ZipFile(apk) as z:
        data = z.read('AndroidManifest.xml')
    pool_off = 8
    _, _, chunk = struct.unpack_from('<HHI', data, pool_off)
    count, _, flags, strings_start, _ = struct.unpack_from('<IIIII', data, pool_off + 8)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from('<%dI' % count, data, pool_off + 28)
    pool = []
    for o in offsets:
        p = pool_off + strings_start + o
        if utf8:
            n = data[p]; p += 2 if n & 0x80 else 1
            n2 = data[p]
            if n2 & 0x80:
                n2 = ((n2 & 0x7F) << 8) | data[p + 1]; p += 2
            else:
                p += 1
            pool.append(data[p:p + n2].decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', data, p)[0]; p += 2
            pool.append(data[p:p + 2 * n].decode('utf-16-le', 'replace'))
    p = pool_off + chunk
    while p < len(data):
        ctype, header, size = struct.unpack_from('<HHI', data, p)
        if ctype == 0x0102:
            name = pool[struct.unpack_from('<I', data, p + 20)[0]]
            if name == 'manifest':
                attr_count = struct.unpack_from('<H', data, p + 28)[0]
                out = {}
                for a in range(attr_count):
                    q = p + 36 + 20 * a
                    _, aname, raw, _, dtype, value = struct.unpack_from('<IIIHxBI', data, q)
                    key = pool[aname]
                    if key in ('package', 'versionName', 'versionCode'):
                        out[key] = pool[raw] if raw != 0xFFFFFFFF else value
                return out
        p += size
    raise ValueError('manifest element not found')


def bridge_class_literals(repo):
    found = set()
    root = Path(repo) / 'app/src/main/java'
    for path in root.rglob('*.java'):
        for lit in re.findall(r'"((?:[a-zA-Z_][\w$]*\.)+[\w$]+)"', path.read_text(encoding='utf-8')):
            found.add('L' + lit.replace('.', '/') + ';')
    return found


def summarize(apk, label, repo):
    spec = json.loads((Path(repo) / 'app/src/test/resources/compat/anchor-spec.json')
                      .read_text(encoding='utf-8'))
    ident = manifest_identity(apk)
    kind = {'com.android.systemui': 'systemui', 'com.oplus.systemui.plugins': 'plugin'}[ident['package']]
    classes = load(apk)
    include = {c for c in bridge_class_literals(repo) if c in classes}
    anchors = {}
    invoke_hits = {}
    queries = spec[kind]
    scanned = {}

    def usage(name):
        if name not in scanned:
            entry = classes[name]
            strings, invokes = set(), []
            for code in entry['_code']:
                if code:
                    s, inv = entry['_dex'].scan_code(code)
                    strings |= s
                    invokes.append(inv)
                else:
                    invokes.append([])
            scanned[name] = (strings, invokes)
        return scanned[name]

    for q in queries.get('anchors', []):
        prefix = 'L' + q.get('package', '').replace('.', '/')
        hits = []
        for name in classes:
            if q.get('package') and not name.startswith(prefix + '/'):
                continue
            if all(s in usage(name)[0] for s in q['strings']):
                hits.append(name)
        anchors[q['key']] = sorted(hits)
        include.update(hits)
    for q in queries.get('invokes', []):
        target = q['invoke']
        hits = []
        for name, entry in classes.items():
            if not any(m[1] == q['params'] and m[2] == q['ret'] for m in entry['methods']):
                continue
            _, invokes = usage(name)
            for m, inv in zip(entry['methods'], invokes):
                if m[1] != q['params'] or m[2] != q['ret']:
                    continue
                if any(c == target['class'] and p == tuple(target['params']) and r == target['ret']
                       for c, _, p, r in inv):
                    hits.append([name, m[0]])
        invoke_hits[q['key']] = sorted(hits)
        include.update(h[0] for h in hits)
    # Superclass chains inside the APK.
    pending = list(include)
    while pending:
        name = pending.pop()
        sup = classes[name]['super']
        if sup in classes and sup not in include:
            include.add(sup)
            pending.append(sup)
    out_classes = {}
    for name in sorted(include):
        e = classes[name]
        out_classes[name] = {'access': e['access'], 'super': e['super'],
                             'interfaces': e['interfaces'], 'fields': e['fields'],
                             'methods': e['methods']}
    digest = hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    return {
        'schema': SCHEMA,
        'component': kind,
        'package': ident['package'],
        'versionName': ident['versionName'],
        'versionCode': ident['versionCode'],
        'sha256': digest,
        'label': label,
        'anchorSpec': queries,
        'anchors': anchors,
        'invokes': invoke_hits,
        'classes': out_classes,
    }


def main():
    apk, label, repo, out = sys.argv[1:5]
    summary = summarize(apk, label, repo)
    Path(out).write_text(json.dumps(summary, ensure_ascii=False, indent=0, sort_keys=True) + '\n',
                         encoding='utf-8', newline='\n')
    print(summary['component'], summary['versionName'], summary['sha256'][:12],
          'classes', len(summary['classes']),
          'anchors', {k: len(v) for k, v in summary['anchors'].items()},
          'invokes', {k: len(v) for k, v in summary['invokes'].items()})


if __name__ == '__main__':
    main()
