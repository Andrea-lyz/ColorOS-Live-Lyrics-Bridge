// Independent verifier for the cll-source-v1 build manifest; requires only Node built-ins.
import { readFile } from 'node:fs/promises';
import { resolve, sep } from 'node:path';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const sha = bytes => createHash('sha256').update(bytes).digest('hex');
function digest(inputs, parameters) {
  return sha('cll-source-v1\n' + Object.keys(inputs).sort().map(key => key + '\0' + inputs[key] + '\n').join('') +
    'parameters\0' + JSON.stringify(Object.fromEntries(Object.entries(parameters).sort(([a], [b]) => a.localeCompare(b)))) + '\n');
}
if (process.argv.includes('--self-test')) {
  const a = { 'src/main/A.java': sha('A') };
  const added = { ...a, 'src/main/Untracked.java': sha('one') };
  const modified = { ...added, 'src/main/Untracked.java': sha('two') };
  assert.notEqual(digest(a, {}), digest(added, {}));
  assert.notEqual(digest(added, {}), digest(modified, {}));
  assert.equal(digest(a, {}), digest({ ...a }, {}));
  assert.notEqual(digest(a, {}), digest(a, { releaseTag: 'v5' }));
  assert.equal(digest(added, {}), digest(Object.fromEntries(Object.entries(added).reverse()), {}));
  console.log('SOURCE_IDENTITY_INVARIANTS_PASS: add, untracked-edit, remove, order, parameters');
} else {
  const root = resolve(process.argv[2] || '.');
  const reportPath = resolve(root, process.argv[3] || 'build/reports/cll-source-identity.json');
  const report = JSON.parse(await readFile(reportPath, 'utf8'));
  assert.equal(report.schema, 1);
  assert.equal(report.algorithm, 'cll-source-v1');
  assert.ok(['clean', 'dirty', 'unknown'].includes(report.state));
  if (report.commit === 'unknown') assert.equal(report.state, 'unknown');
  const observed = {};
  for (const key of Object.keys(report.inputs).sort()) {
    const absolute = resolve(root, key);
    assert.ok(absolute.startsWith(root + sep), 'input escaped selected repository');
    observed[key] = sha(await readFile(absolute));
    assert.equal(observed[key], report.inputs[key], 'source bytes changed: ' + key);
  }
  assert.equal(digest(observed, report.parameters || {}), report.sourceDigest);
  console.log(JSON.stringify({ result: 'SOURCE_IDENTITY_VERIFIED', files: Object.keys(observed).length,
    state: report.state, commit: report.commit, sourceDigest: report.sourceDigest }));
}
