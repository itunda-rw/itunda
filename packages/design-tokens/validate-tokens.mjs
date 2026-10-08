#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const tokens = JSON.parse(await readFile(resolve(new URL('./tokens.json', import.meta.url)), 'utf8'));
const fail = (message) => {
  console.error(`Itunda design-token validation failed: ${message}`);
  process.exit(1);
};

const hex = /^#[0-9A-Fa-f]{6}$/;
const requiredFamilies = ['grey', 'blue', 'red'];
const requiredSteps = ['50','100','200','300','400','500','600','700','800','900'];

if (tokens.version !== '3.1.0') fail('unexpected token schema version');
if (tokens.colors?.green500 !== '04C065') fail('green500 primitive drifted');
if (tokens.colors?.white !== 'FFFFFF') fail('white primitive drifted');

for (const family of requiredFamilies) {
  if (!tokens.colors?.[family]) fail(`missing primitive family: ${family}`);
  for (const step of requiredSteps) {
    if (!/^[0-9A-Fa-f]{6}$/.test(tokens.colors[family][step])) {
      fail(`invalid ${family}.${step} primitive`);
    }
  }
}

const light = tokens.semantic?.light;
const dark = tokens.semantic?.dark;
if (!light || !dark) fail('light and dark semantic themes are required');

const walk = (value, path = []) => {
  if (typeof value === 'string') return [path.join('.')];
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(`invalid semantic token at ${path.join('.')}`);
  return Object.entries(value).flatMap(([key, child]) => walk(child, [...path, key]));
};

const lightKeys = new Set(walk(light));
const darkKeys = new Set(walk(dark));
if (lightKeys.size !== darkKeys.size || [...lightKeys].some((key) => !darkKeys.has(key))) {
  fail('light and dark themes must expose identical semantic roles');
}

for (const key of lightKeys) {
  const lightValue = key.split('.').reduce((v, k) => v?.[k], light);
  const darkValue = key.split('.').reduce((v, k) => v?.[k], dark);
  if (!hex.test(lightValue) || !hex.test(darkValue)) fail(`semantic role ${key} must use six-digit hex values`);
}

if (light.intent?.brand !== '#7472F4') fail('Itunda brand token must remain #7472F4');
if (dark.intent?.brand !== '#9B98FF') fail('dark brand role drifted unexpectedly');

const luminance = (value) => {
  const rgb = value.slice(1).match(/../g).map((pair) => parseInt(pair, 16) / 255);
  const linear = rgb.map((channel) => channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
};
const contrast = (a, b) => {
  const l1 = luminance(a);
  const l2 = luminance(b);
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
};

const checks = [
  ['light text.primary on surface.default', light.text.primary, light.surface.default, 4.5],
  ['light text.secondary on surface.default', light.text.secondary, light.surface.default, 4.5],
  ['dark text.primary on surface.default', dark.text.primary, dark.surface.default, 4.5],
  ['dark text.secondary on surface.default', dark.text.secondary, dark.surface.default, 4.5],
];
for (const [name, foreground, background, minimum] of checks) {
  if (contrast(foreground, background) < minimum) fail(`${name} contrast is below ${minimum}:1`);
}

console.log(`Itunda design tokens valid: ${tokens.version}; ${lightKeys.size} semantic roles; accessibility checks passed.`);
