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
const evidenceSchemaPath = 'design-system/qa/evidence-schema.json';
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
const evidenceSchema = JSON.parse(await readFile(join(root, evidenceSchemaPath), 'utf8'));
if (evidenceSchema.schema !== 'ids-qa-evidence/v6') failures.push('QA evidence schema must be ids-qa-evidence/v6');
for (const field of ['scenario','componentId','contractSignature','platformContractSignature','componentPlatformContractSignature','scenarioPlatformContractSignature','platform','verifiedAt','context']) {
  if (!evidenceSchema.evidenceRequired?.includes(field)) failures.push('QA evidence schema missing required evidence field: '+field);
}
for (const field of ['theme','scale','motion','viewport']) {
  if (!evidenceSchema.contextRequired?.includes(field)) failures.push('QA evidence schema missing required context field: '+field);
}
for (const platform of ['web','android','ios']) {
  if (!evidenceSchema.platforms?.includes(platform)) failures.push('QA evidence schema missing platform: '+platform);
}
for (const field of ['scenario','componentId','requiredPlatforms','scenarioPlatformContractSignature','platforms']) {
  if (!evidenceSchema.coverageRequired?.includes(field)) failures.push('QA evidence schema missing coverage field: '+field);
}

const webApiChecks = {
  button: ['interface ButtonProps','function Button','loadingLabel'],
  'text-field': ['interface TextFieldProps','function TextField','clearable','multiline'],
  select: ['interface SelectProps','function Select','SelectOption'],
  checkbox: ['interface CheckboxProps','function Checkbox','indeterminate'],
  radio: ['interface RadioProps','function Radio'],
  switch: ['interface SwitchProps','function Switch','role="switch"'],
  tabs: ['interface TabsProps','function Tabs','role="tab"'],
  'empty-state': ['interface EmptyStateProps','function EmptyState','EmptyStateAction'],
};
const requiredQaScenarios = {
  button: ['default','pressed','disabled','loading','long'],
  field: ['default','focus','error','success','long'],
  select: ['placeholder','focus','disabled','error','success','option-disabled'],
  checkbox: ['checked','indeterminate','disabled','error'],
  radio: ['checked','disabled','error','group'],
  switch: ['on','off','loading','disabled','error'],
  tabs: ['selected','disabled','controls','roving-focus'],
  empty: ['default','error','success','long'],
};
const platformAccessibilityMarkers = {
  web: { keyboard:['keydown','onKeyDown','tabIndex','focus'], screenReader:['aria-','role='], largeText:['rem','font-size','line-height'], reducedMotion:['prefers-reduced-motion','motion'] },
  android: { keyboard:['onKeyEvent','focusable','focusRequester'], screenReader:['semantics','contentDescription'], largeText:['sp','fontSize'], reducedMotion:['animation','motion'] },
  ios: { keyboard:['focus','keyboard'], screenReader:['accessibility','accessibilityLabel'], largeText:['dynamicTypeSize','font'], reducedMotion:['reduceMotion','accessibilityReduceMotion'] },
};
const platformContentMarkers = {
  web: ['overflow-wrap','word-break','white-space','min-width','max-width'],
  android: ['Text','maxLines','softWrap','wrap'],
  ios: ['Text','lineLimit','fixedSize','multilineTextAlignment'],
};
const platformSemanticMarkers = {
  web: {
    states: { disabled:['disabled'], loading:['aria-busy'], error:['aria-invalid','role="alert"'], success:['success'] },
    a11y: ['aria-', 'focus'],
    motion: ['motion','transition'],
  },
  android: {
    states: { disabled:['enabled = false','enabled=false','disabled'], loading:['loading','progress'], error:['error'], success:['success'] },
    a11y: ['semantics','contentDescription'],
    motion: ['animate','animation','motion'],
  },
  ios: {
    states: { disabled:['disabled'], loading:['loading','ProgressView'], error:['error'], success:['success'] },
    a11y: ['accessibility','accessibilityLabel','accessibilityHint'],
    motion: ['animation','withAnimation','transition'],
  },
};
const implementationChecks = {
  button: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Button'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt', symbols: ['IdsButton'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSButton'] },
  },
  'text-field': {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['TextField'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsTextField.kt', symbols: ['IdsTextField'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSTextField'] },
  },
  select: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Select'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsSelect.kt', symbols: ['IdsSelect'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSSelect'] },
  },
  checkbox: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Checkbox'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsSelectionControls.kt', symbols: ['IdsCheckbox'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSCheckbox'] },
  },
  radio: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Radio'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsSelectionControls.kt', symbols: ['IdsRadioButton'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSRadio'] },
  },
  switch: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Switch'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsSelectionControls.kt', symbols: ['IdsSwitch'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSSwitch'] },
  },
  tabs: {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['Tabs'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsTabs.kt', symbols: ['IdsTabs'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSTabs'] },
  },
  'empty-state': {
    web: { file: 'packages/design-system-web/src/index.ts', symbols: ['EmptyState'] },
    android: { file: 'android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsEmptyState.kt', symbols: ['IdsEmptyState'] },
    ios: { file: 'ios/Core/DesignSystem/Sources/Components/IDSCoreComponents.swift', symbols: ['IDSEmptyState'] },
  },
};
const platformContractPath = 'design-system/platform-contract.md';
const platformContract = await readFile(join(root, platformContractPath), 'utf8');
for (const platform of ['Web', 'Android', 'iOS']) {
  if (!new RegExp('\\b' + platform + '\\b', 'i').test(platformContract)) failures.push('Platform contract missing '+platform+' coverage');
}
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

const validScenarioPlatforms = new Set(['web','android','ios']);
for (const component of components || []) {
  const prefix = contractPath + ':' + (component?.id || '<missing-id>');
  const scenarios = component?.qa?.scenarios;
  if (!scenarios || typeof scenarios !== 'object' || Array.isArray(scenarios)) {
    failures.push(prefix + ': qa.scenarios must be an object');
    continue;
  }
  const kind = component.id === 'text-field' ? 'field' : component.id === 'empty-state' ? 'empty' : component.id;
  const expected = requiredQaScenarios[kind] || [];
  const declaredIds = Object.keys(scenarios);
  for (const scenarioId of expected) {
    const scenario = scenarios[scenarioId];
    if (!scenario || typeof scenario !== 'object' || Array.isArray(scenario)) {
      failures.push(prefix + ': qa.scenarios.' + scenarioId + ' must be an object');
      continue;
    }
    if (!Array.isArray(scenario.platforms) || scenario.platforms.length === 0) {
      failures.push(prefix + ': qa.scenarios.' + scenarioId + '.platforms must be a non-empty array');
      continue;
    }
    const invalidPlatforms = scenario.platforms.filter(platform => !validScenarioPlatforms.has(String(platform)));
    if (invalidPlatforms.length) failures.push(prefix + ': qa.scenarios.' + scenarioId + '.platforms contains invalid platform(s): ' + invalidPlatforms.join(', '));
    const unsupportedPlatforms = scenario.platforms.filter(platform => !(component.platforms || []).includes(platform));
    if (unsupportedPlatforms.length) failures.push(prefix + ': qa.scenarios.' + scenarioId + '.platforms must be a subset of component platforms: ' + unsupportedPlatforms.join(', '));
    if (new Set(scenario.platforms).size !== scenario.platforms.length) failures.push(prefix + ': qa.scenarios.' + scenarioId + '.platforms must not contain duplicates');
  }
  for (const scenarioId of declaredIds) {
    if (!expected.includes(scenarioId)) failures.push(prefix + ': qa.scenarios contains non-canonical scenario ' + scenarioId);
  }
}

for (const [componentId, scenarioIds] of Object.entries(requiredQaScenarios)) {
  const component = (components || []).find(item => item.id === componentId);
  if (!component) {
    failures.push(`\${contractPath}:\${componentId}: missing canonical component for QA scenario coverage`);
    continue;
  }
  const declared = [
    ...(component.states || []),
    ...(component.content || []),
    ...(component.a11y || []),
  ].map(item => String(item).toLowerCase());
  for (const scenarioId of scenarioIds) {
    const normalized = scenarioId.replace(/-/g, ' ');
    if (!declared.some(item => item.includes(normalized) || normalized.split(' ').every(token => item.includes(token)))) {
      failures.push(`\${contractPath}:\${componentId}: QA scenario \${scenarioId} is not represented by the component contract`);
    }
  }
}


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

for (const [id, requiredApi] of Object.entries(webApiChecks)) {
  const webCheck = implementationChecks[id]?.web;
  if (!webCheck) {
    failures.push(`${id}:web: implementation mapping is missing`);
    continue;
  }
  try {
    const source = await readFile(join(root, webCheck.file), 'utf8');
    for (const marker of requiredApi) {
      if (!source.includes(marker)) failures.push(`${id}:web: API/implementation marker missing: ${marker}`);
    }
  } catch {
    failures.push(`${id}:web: cannot validate API surface: ${webCheck.file}`);
  }
}

for (const [id, platforms] of Object.entries(implementationChecks)) {
  const contractComponent = (components || []).find(component => component.id === id || component.name === id);
  for (const [platform, check] of Object.entries(platforms)) {
    try {
      const source = await readFile(join(root, check.file), 'utf8');
      for (const symbol of check.symbols) {
        if (!source.includes(symbol)) failures.push(`${id}:${platform}: required symbol ${symbol} is not implemented`);
      }
      if (!contractComponent) {
        failures.push(`${id}:${platform}: no matching contract component`);
      } else {
        for (const dimension of ['states','content','a11y','motion','platforms']) {
          const value = contractComponent[dimension];
          if (!Array.isArray(value) || value.length === 0) failures.push(`${id}:${platform}: contract dimension ${dimension} is empty`);
        }
        const markers = platformSemanticMarkers[platform];
        if (markers) {
          for (const [state, requiredMarkers] of Object.entries(markers.states || {})) {
            if ((contractComponent.states || []).some(item => String(item).toLowerCase().includes(state))) {
              const missingMarkers = requiredMarkers.filter(marker => !source.includes(marker));
              if (missingMarkers.length) failures.push(`${id}:${platform}: state ${state} missing semantic marker(s): ${missingMarkers.join(', ')}`);
            }
          }
          for (const marker of markers.a11y || []) {
            if (marker === 'aria-' ? !/aria-[a-z-]+/.test(source) : !source.toLowerCase().includes(marker.toLowerCase())) failures.push(`${id}:${platform}: accessibility marker missing: ${marker}`);
          }
          for (const marker of markers.motion || []) {
            if (!source.toLowerCase().includes(marker.toLowerCase())) failures.push(`${id}:${platform}: motion marker missing: ${marker}`);
          }
          const contentRequirements = (contractComponent.content || []).map(item => String(item).toLowerCase()).join(' ');
          const localizationRequired = /long|local|korean|english|wrap|multiline|overflow|content/.test(contentRequirements);
          if (localizationRequired) {
            const contentMarkers = platformContentMarkers[platform] || [];
            if (platform === 'web' && !contentMarkers.some(marker => source.toLowerCase().includes(marker.toLowerCase()))) {
              failures.push(`${id}:${platform}: localization/content contract has no wrapping or sizing implementation marker`);
            }
            if (platform !== 'web' && !contentMarkers.some(marker => source.toLowerCase().includes(marker.toLowerCase()))) {
              failures.push(`${id}:${platform}: localization/content contract has no text-layout implementation marker`);
            }
          }
          const accessibilityRequirements = (contractComponent.a11y || []).map(item => String(item).toLowerCase()).join(' ');
          const accessibilityMarkers = platformAccessibilityMarkers[platform];
          if (accessibilityMarkers) {
            for (const [dimension, markers] of Object.entries(accessibilityMarkers)) {
              const relevant = new RegExp(dimension === 'screenReader' ? 'screen|reader|aria|talkback|voiceover|accessib' : dimension === 'largeText' ? 'large|text|type|font|scale' : dimension === 'reducedMotion' ? 'motion|animation|reduce' : 'keyboard|focus').test(accessibilityRequirements);
              if (relevant && !markers.some(marker => source.toLowerCase().includes(marker.toLowerCase()))) {
                failures.push(`${id}:${platform}: ${dimension} contract has no implementation marker`);
              }
            }
          }
        }
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

console.log('IDS audit passed: tokens + contract manifest + API surface + platform implementation mapping + cross-platform contract dimensions + key Web accessibility contracts.');
