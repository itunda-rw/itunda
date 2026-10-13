export type SaroniteMiniAppManifest = {
  schemaVersion: 1;
  id: string;
  name: string;
  version: string;
  entrypoint: string;
  platforms: Array<'android' | 'ios'>;
  permissions?: string[];
  bundle?: {
    maxBytes?: number;
    entryBytes?: number;
  };
};

export type ReleaseFinding = {
  severity: 'error' | 'warning' | 'info';
  code: string;
  message: string;
};

const ID_PATTERN = /^[a-z][a-z0-9-]{1,62}$/;
const SEMVER_PATTERN = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;
const KNOWN_PERMISSIONS = new Set([
  'identity:read',
  'location:read',
  'navigation:open',
  'navigation:close',
]);

/** Validate the public manifest contract without performing file or network access. */
export function validateMiniAppManifest(input: unknown): ReleaseFinding[] {
  const findings: ReleaseFinding[] = [];
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    return [{ severity: 'error', code: 'manifest.invalid-shape', message: 'Manifest must be an object.' }];
  }
  const manifest = input as Partial<SaroniteMiniAppManifest>;
  if (manifest.schemaVersion !== 1) findings.push({ severity: 'error', code: 'manifest.schema-version', message: 'schemaVersion must be 1.' });
  if (typeof manifest.id !== 'string' || !ID_PATTERN.test(manifest.id)) findings.push({ severity: 'error', code: 'manifest.invalid-id', message: 'id must be 2–63 lowercase letters, digits, or hyphens and start with a letter.' });
  if (typeof manifest.name !== 'string' || manifest.name.trim().length < 2) findings.push({ severity: 'error', code: 'manifest.invalid-name', message: 'name must contain at least 2 non-whitespace characters.' });
  if (typeof manifest.version !== 'string' || !SEMVER_PATTERN.test(manifest.version)) findings.push({ severity: 'error', code: 'manifest.invalid-version', message: 'version must use semantic versioning (for example, 1.2.3).' });
  const entrypoint = manifest.entrypoint;
  const unsafeEntrypoint = typeof entrypoint !== 'string'
    || !entrypoint.trim()
    || entrypoint.startsWith('/')
    || entrypoint.includes('\\\\')
    || /[\\u0000-\\u001f\\u007f]/.test(entrypoint)
    || /^[a-z][a-z0-9+.-]*:/i.test(entrypoint)
    || entrypoint.split('/').some((segment) => segment === '' || segment === '.' || segment === '..');
  if (unsafeEntrypoint) {
    findings.push({ severity: 'error', code: 'manifest.invalid-entrypoint', message: 'entrypoint must be a clean POSIX relative path without empty, dot, parent, URL-scheme, backslash, or control-character segments.' });
  }
  const platforms = manifest.platforms;
  if (!Array.isArray(platforms) || platforms.length === 0 || platforms.some((p) => p !== 'android' && p !== 'ios')) {
    findings.push({ severity: 'error', code: 'manifest.invalid-platforms', message: 'platforms must include android, ios, or both.' });
  } else if (new Set(platforms).size !== platforms.length) {
    findings.push({ severity: 'error', code: 'manifest.duplicate-platform', message: 'platforms must not contain duplicates.' });
  }
  if (manifest.permissions !== undefined) {
    if (!Array.isArray(manifest.permissions) || manifest.permissions.some((p) => typeof p !== 'string' || !KNOWN_PERMISSIONS.has(p))) {
      findings.push({ severity: 'error', code: 'manifest.unknown-permission', message: 'permissions contains an unknown permission; add it to the reviewed Saronite capability contract first.' });
    } else if (new Set(manifest.permissions).size !== manifest.permissions.length) {
      findings.push({ severity: 'error', code: 'manifest.duplicate-permission', message: 'permissions must not contain duplicates.' });
    }
  }
  if (manifest.bundle !== undefined) {
    if (!manifest.bundle || typeof manifest.bundle !== 'object' || Array.isArray(manifest.bundle)) {
      findings.push({ severity: 'error', code: 'bundle.invalid-shape', message: 'bundle must be an object containing size-budget metadata.' });
    } else {
      const { maxBytes, entryBytes } = manifest.bundle;
      if (maxBytes !== undefined && (!Number.isSafeInteger(maxBytes) || maxBytes <= 0)) findings.push({ severity: 'error', code: 'bundle.invalid-budget', message: 'bundle.maxBytes must be a positive safe integer.' });
      if (entryBytes !== undefined && (!Number.isSafeInteger(entryBytes) || entryBytes < 0)) findings.push({ severity: 'error', code: 'bundle.invalid-size', message: 'bundle.entryBytes must be a non-negative safe integer.' });
      if (maxBytes !== undefined && entryBytes !== undefined && entryBytes > maxBytes) findings.push({ severity: 'error', code: 'bundle.budget-exceeded', message: `Bundle entry size ${entryBytes} bytes exceeds the ${maxBytes}-byte budget.` });
      if (maxBytes !== undefined && entryBytes !== undefined && entryBytes > maxBytes * 0.9 && entryBytes <= maxBytes) findings.push({ severity: 'warning', code: 'bundle.near-budget', message: 'Bundle entry is above 90% of its configured size budget.' });
    }
  }
  if (platforms && Array.isArray(platforms) && platforms.includes('android') !== platforms.includes('ios')) {
    findings.push({ severity: 'warning', code: 'platform.partial-parity', message: 'This manifest targets only one native platform; confirm the product intentionally supports a single platform.' });
  }
  if (!manifest.permissions?.length) findings.push({ severity: 'info', code: 'permissions.none-declared', message: 'No permissions declared. Confirm this mini-app genuinely needs no privileged host capabilities.' });
  return findings;
}

export function isReleaseReady(findings: ReleaseFinding[]): boolean {
  return !findings.some((finding) => finding.severity === 'error');
}
