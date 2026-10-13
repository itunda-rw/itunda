import assert from 'node:assert/strict';
import { isReleaseReady, validateMiniAppManifest } from './release-check.ts';

const valid = {
  schemaVersion: 1,
  id: 'wallet-balance',
  name: 'Wallet Balance',
  version: '1.2.3',
  entrypoint: 'pages/index.tsx',
  platforms: ['android', 'ios'],
  permissions: ['identity:read'],
  bundle: { maxBytes: 200_000, entryBytes: 150_000 },
};

const validFindings = validateMiniAppManifest(valid);
assert.equal(validFindings.some((finding) => finding.severity === 'error'), false);
assert.equal(isReleaseReady(validFindings), true);

const invalid = validateMiniAppManifest({
  ...valid,
  id: '../wallet',
  version: 'latest',
  entrypoint: '../secrets',
  platforms: ['android', 'android'],
  permissions: ['admin:all'],
  bundle: { maxBytes: 1000, entryBytes: 1200 },
});
assert.ok(invalid.some((finding) => finding.code === 'manifest.invalid-id'));
assert.ok(invalid.some((finding) => finding.code === 'manifest.invalid-version'));
assert.ok(invalid.some((finding) => finding.code === 'manifest.invalid-entrypoint'));
for (const entrypoint of [
  String.raw\`pages\\index.tsx\`,
  'https://example.invalid/app.js',
  'pages//index.tsx',
  './pages/index.tsx',
  'pages/\u0000index.tsx',
]) {
  const findings = validateMiniAppManifest({ ...valid, entrypoint });
  assert.ok(
    findings.some((finding) => finding.code === 'manifest.invalid-entrypoint'),
    \`Expected unsafe entrypoint to be rejected: \${JSON.stringify(entrypoint)}\`,
  );
}
assert.ok(invalid.some((finding) => finding.code === 'manifest.duplicate-platform'));
assert.ok(invalid.some((finding) => finding.code === 'manifest.unknown-permission'));
assert.ok(invalid.some((finding) => finding.code === 'bundle.budget-exceeded'));
assert.equal(isReleaseReady(invalid), false);

const nearBudget = validateMiniAppManifest({ ...valid, bundle: { maxBytes: 1000, entryBytes: 950 } });
assert.ok(nearBudget.some((finding) => finding.code === 'bundle.near-budget'));
assert.equal(validateMiniAppManifest(null)[0]?.code, 'manifest.invalid-shape');

const malformedBundle = validateMiniAppManifest({ ...valid, bundle: 'not-an-object' });
assert.ok(malformedBundle.some((finding) => finding.code === 'bundle.invalid-shape'));
assert.equal(isReleaseReady(malformedBundle), false);

const arrayBundle = validateMiniAppManifest({ ...valid, bundle: [] });
assert.ok(arrayBundle.some((finding) => finding.code === 'bundle.invalid-shape'));

console.log('Saronite release-check contract tests passed');
