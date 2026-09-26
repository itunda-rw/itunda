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
const platformImplementations = {
  button: [
    ['web', 'packages/design-system-web/src/index.ts', 'Button'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsButton'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsButton'],
  ],
  'text-field': [
    ['web', 'packages/design-system-web/src/index.ts', 'TextField'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsTextField'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsTextField'],
  ],
  select: [
    ['web', 'packages/design-system-web/src/index.ts', 'Select'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsSelect'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsSelect'],
  ],
  checkbox: [
    ['web', 'packages/design-system-web/src/index.ts', 'Checkbox'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsCheckbox'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsCheckbox'],
  ],
  radio: [
    ['web', 'packages/design-system-web/src/index.ts', 'Radio'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsRadio'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsRadio'],
  ],
  switch: [
    ['web', 'packages/design-system-web/src/index.ts', 'Switch'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsSwitch'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsSwitch'],
  ],
  tabs: [
    ['web', 'packages/design-system-web/src/index.ts', 'Tabs'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsTabs'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsTabs'],
  ],
  'empty-state': [
    ['web', 'packages/design-system-web/src/index.ts', 'EmptyState'],
    ['android', 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', 'IdsEmptyState'],
    ['ios', 'ios/Core/DesignSystem/Sources/Components/Components.swift', 'IdsEmptyState'],
  ],
};

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
const components = manifest.components;

if (manifest.version !== '3.0.0') failures.push(`${contractPath}: expected version 3.0.0`);
if (!Array.isArray(components) || components.length !== 8) failures.push(`${contractPath}: expected exactly 8 components`);

const ids = new Set();
const required = ['states', 'content', 'a11y', 'motion', 'platforms'];
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
  if (!component.validation?.required || !required.every(key => component.validation.required.includes(key))) {
    failures.push(`${prefix}: validation.required must include ${required.join(', ')}`);
  }
}

for (const [id, entries] of Object.entries(platformImplementations)) {
  for (const [platform, file, symbol] of entries) {
    try {
      const source = await readFile(join(root, file), 'utf8');
      if (!new RegExp(`(?:export\\s+)?(?:function|const|class|struct|@Composable|struct)\\s+${symbol}\\b`).test(source) && !source.includes(symbol)) {
        failures.push(`${id}:${platform}: missing implementation symbol ${symbol} in ${file}`);
      }
    } catch {
      failures.push(`${id}:${platform}: missing implementation file ${file}`);
    }
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

console.log('IDS audit passed: tokens + contract manifest + platform implementation mapping.');
