import { readFile, readdir } from 'node:fs/promises';
import { join, relative } from 'node:path';

const root = process.cwd();
const targets = [
  'services/micro-frontends',
  'packages/design-system-web/src',
];
const extensions = new Set(['.ts', '.tsx', '.css']);
const banned = [
  /#[0-9a-fA-F]{3,8}\b/,
  /rgba?\(/,
  /hsla?\(/,
];
const allow = new Set([
  'packages/design-system-web/src/styles.css',
  'packages/design-tokens',
]);

const contractPath = 'design-system/components/contract-manifest.json';
async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path));
    else if (extensions.has(path.slice(path.lastIndexOf('.')))) files.push(path);
  }
  return files;
}

const failures = [];
const manifest = JSON.parse(await readFile(join(root, contractPath), 'utf8'));

const implementationChecks = {
  button: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Button'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsButton'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsButton'] },
  },
  'text-field': {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['TextField'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsTextField'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsTextField'] },
  },
  select: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Select'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsSelect'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsSelect'] },
  },
  checkbox: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Checkbox'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsCheckbox'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsCheckbox'] },
  },
  radio: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Radio'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsRadio'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsRadio'] },
  },
  switch: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Switch'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsSwitch'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsSwitch'] },
  },
  tabs: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Tabs'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsTabs'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsTabs'] },
  },
  'empty-state': {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['EmptyState'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsEmptyState'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/Components.swift', symbols: ['IdsEmptyState'] },
  },
};
const tokenCssPath = 'packages/design-tokens/tokens.css';
const expectedBrand = '#7472F4';
const tokenCss = await readFile(join(root, tokenCssPath), 'utf8');
if (!new RegExp('\\b--itunda-indigo\\s*:\s*' + expectedBrand + '\\s*;').test(tokenCss)) {
  failures.push(`${tokenCssPath}: canonical Itunda brand must be ${expectedBrand}`);
}
if (!new RegExp('\\b--itunda-brand\\s*:\s*var\\(--itunda-indigo\\)').test(tokenCss)) {
  failures.push(`${tokenCssPath}: semantic brand must resolve from --itunda-indigo`);
}

const typographyFoundation = tokens.foundations?.typography;
if (!typographyFoundation || typographyFoundation.minimumScale !== 1 || typographyFoundation.largeTextScale !== 1.5) {
  failures.push(`${tokensPath}: typography foundation must define 100% baseline and 150% large-text scale`);
}
for (const role of ['body', 'label', 'caption', 'title', 'display']) {
  const spec = typographyFoundation?.[role];
  if (!spec || !Number.isFinite(spec.size) || !Number.isFinite(spec.lineHeight) || spec.lineHeight < spec.size) {
    failures.push(`${tokensPath}: typography.${role} must define size and lineHeight with lineHeight >= size`);
  }
}

const components = manifest.components;

if (manifest.version !== '3.0.0') failures.push(`${contractPath}: expected version 3.0.0`);
if (!Array.isArray(components) || components.length !== 8) failures.push(`${contractPath}: expected exactly 8 components`);

const ids = new Set();
const required = ['states', 'content', 'a11y', 'motion', 'platforms'];
const canonicalImplementationFiles = [
  'packages/design-system-web/src/index.ts',
  'packages/design-system-web/src/styles.css',
  'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt',
  'ios/Core/DesignSystem/Sources/Components/Components.swift',
];
for (const component of components || []) {
  const prefix = `${contractPath}:${component.id || '<missing-id>'}`;
  if (!component.id || ids.has(component.id)) failures.push(`${prefix}: ids must be present and unique`);
  ids.add(component.id);
  if (!['flat', 'compound'].includes(component.api)) failures.push(`${prefix}: api must be flat or compound`);
  for (const key of ['states', 'content', 'a11y', 'platforms']) {
    if (!Array.isArray(component[key]) || component[key].length === 0) failures.push(`${prefix}: ${key} must be a non-empty array`);
  }
  if (!Array.isArray(component.platforms) || !['web', 'android', 'ios'].every(platform => component.platforms.includes(platform))) {
    failures.push(`${prefix}: platforms must include web, android, ios`);
  }
  if (!component.a11y.includes('motion')) {
    failures.push(`${prefix}: a11y must include motion for release-gate coverage`);
  }
  if (!component.guide || typeof component.guide !== 'object') failures.push(`${prefix}: guide metadata is required`);
  else {
    for (const key of ['purpose', 'worstCase']) {
      if (typeof component.guide[key] !== 'string' || !component.guide[key].trim()) failures.push(`${prefix}: guide.${key} must be a non-empty string`);
    }
    if (!Array.isArray(component.guide.tokens) || component.guide.tokens.length === 0) failures.push(`${prefix}: guide.tokens must be non-empty`);
    if (!Array.isArray(component.guide.checklist) || component.guide.checklist.length < 2) failures.push(`${prefix}: guide.checklist must contain at least 2 checks`);
  }

  if (!component.validation?.required || !required.every(key => component.validation.required.includes(key))) {
    failures.push(`${prefix}: validation.required must include ${required.join(', ')}`);
  }
}

for (const [id, platforms] of Object.entries(implementationChecks)) {
  for (const [platform, check] of Object.entries(platforms)) {
    try {
      const source = await readFile(join(root, check.file), 'utf8');
      for (const symbol of check.symbols) {
        if (!source.includes(symbol)) failures.push(`${id}:${platform}: required symbol ${symbol} is not implemented`);
      }
      if (platform === 'web' && id === 'button' && !/aria-busy/.test(source)) failures.push('button:web: loading accessibility contract is not represented');
      if (platform === 'web' && id === 'text-field' && !/aria-invalid/.test(source)) failures.push('text-field:web: invalid-state accessibility contract is not represented');
      if (platform === 'web' && id === 'tabs' && !/role="tab"/.test(source)) failures.push('tabs:web: tab role contract is not represented');
      if (platform === 'web' && id === 'switch' && !/role="switch"/.test(source)) failures.push('switch:web: switch role contract is not represented');
    } catch {
      failures.push(`${id}:${platform}: implementation file missing: ${check.file}`);
    }
  }
}

for (const file of canonicalImplementationFiles) {
  try {
    const source = (await readFile(join(root, file), 'utf8'))
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/(^|\s)\/\/.*$/gm, '$1');
    if (file.endsWith('.css')) {
      const componentCss = source.replace(/--itunda-[^;{}]+;/g, '');
      if (/#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/.test(componentCss)) failures.push(file + ': canonical IDS CSS must use semantic tokens, not raw color values/functions');
    } else if (file.endsWith('.kt')) {
      if (/Color\(0x[0-9a-fA-F]{6,8}\)/.test(source)) failures.push(file + ': canonical Android components must consume IDS semantic colors, not raw Color literals');
    } else if (file.endsWith('.swift')) {
      if (/Color\(hex:|Color\(red:|Color\(white:/.test(source)) failures.push(file + ': canonical iOS components must consume IDS semantic colors, not raw color constructors');
    }
  } catch {
    failures.push(file + ': canonical implementation file missing');
  }
}

for (const target of targets) {
  for (const file of await walk(join(root, target))) {
    const rel = relative(root, file).replaceAll('\\\\', '/');
    if ([...allow].some(prefix => rel === prefix || rel.startsWith(prefix + '/'))) continue;
    const source = (await readFile(file, 'utf8'))
      .replace(/\\/\\*[\\s\\S]*?\\*\\//g, '')
      .replace(/(^|\\s)\\/\\/.*$/gm, '$1');
    for (const pattern of banned) {
      if (pattern.test(source)) {
        failures.push(`${rel}: raw color value/function`);
        break;
      }
    }
  }
}

if (failures.length) {
  console.error('IDS audit failed.');
  for (const failure of [...new Set(failures)].sort()) console.error(' - ' + failure);
  process.exit(1);
}

console.log('IDS audit passed: tokens + contract manifest + platform implementation mapping + key Web accessibility contracts.');
