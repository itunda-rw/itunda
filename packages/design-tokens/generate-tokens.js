#!/usr/bin/env node
// Real single-source-of-truth codegen for itunda's primitive color scale.
// Reads tokens.json (this repo's real, sourced-from-Toss's-own-docs canonical
// values) and regenerates the GENERATED:BEGIN/GENERATED:END-marked block in
// each target file. Everything outside those markers -- semantic aliases,
// dark-mode mappings, doc comments -- is hand-authored and never touched.
//
// Usage: node packages/design-tokens/generate-tokens.js
//
// Closes the "no single source of truth" gap found in a 2026-07-13 design-token
// audit (see docs/ARCHITECTURE.md's "Design system" row): four independent,
// hand-authored copies of substantially the same palette existed with no
// build-time or codegen link between them, converging only by manual
// reconciliation passes. This does not attempt spacing/elevation/typography --
// those either aren't confirmed sourced from a real place (spacing/elevation)
// or involve real per-platform hand-tuning this pass deliberately didn't
// override (typography -- see IdsTypography.kt/IdsTheme.swift's own comments).

const fs = require('fs');
const path = require('path');

const tokensPath = path.join(__dirname, 'tokens.json');
const tokens = JSON.parse(fs.readFileSync(tokensPath, 'utf8')).colors;

const SCALE_ORDER = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900'];

function replaceMarked(filePath, generatedBody) {
  const original = fs.readFileSync(filePath, 'utf8');
  const beginIdx = original.indexOf('GENERATED:BEGIN');
  const endIdx = original.indexOf('GENERATED:END');
  if (beginIdx === -1 || endIdx === -1) {
    throw new Error(`${filePath}: GENERATED:BEGIN/GENERATED:END markers not found`);
  }
  // Find the end of the line containing GENERATED:BEGIN, and the start of the
  // line containing GENERATED:END, so we replace only the body between them,
  // preserving both marker comment lines themselves.
  const beginLineEnd = original.indexOf('\n', beginIdx) + 1;
  const endLineStart = original.lastIndexOf('\n', endIdx) + 1;
  const updated = original.slice(0, beginLineEnd) + generatedBody + original.slice(endLineStart);
  fs.writeFileSync(filePath, updated);
  console.log(`Updated ${path.relative(process.cwd(), filePath)}`);
}

// --- Android: IdsColors.kt ---
function generateKotlin() {
  const lines = [];
  for (const family of ['grey', 'blue', 'red']) {
    for (const step of SCALE_ORDER) {
      const name = family.charAt(0).toUpperCase() + family.slice(1) + step;
      lines.push(`    val ${name} = Color(0xFF${tokens[family][step]})`);
    }
    lines.push('');
  }
  lines.push(`    val Green500 = Color(0xFF${tokens.green500})`);
  lines.push(`    val White = Color(0xFF${tokens.white})`);
  return lines.join('\n') + '\n';
}

// --- iOS: IdsTheme.swift ---
function generateSwift() {
  const lines = [];
  for (const family of ['grey', 'blue', 'red']) {
    const swiftName = family === 'grey' ? 'gray' : family;
    for (const step of SCALE_ORDER) {
      lines.push(`    public static let ${swiftName}${step} = Color(hex: 0x${tokens[family][step]})`);
    }
    lines.push('');
  }
  lines.push(`    public static let green500 = Color(hex: 0x${tokens.green500})`);
  lines.push(`    public static let white = Color(hex: 0x${tokens.white})`);
  return lines.join('\n') + '\n';
}

const androidFile = path.join(
  __dirname, '..', '..', 'android', 'core', 'designsystem', 'src', 'main', 'java',
  'rw', 'itunda', 'core', 'designsystem', 'theme', 'IdsColors.kt',
);
const iosFile = path.join(
  __dirname, '..', '..', 'ios', 'Core', 'DesignSystem', 'Sources', 'Theme', 'IdsTheme.swift',
);

replaceMarked(androidFile, generateKotlin());
replaceMarked(iosFile, generateSwift());

console.log('Done. Diff the two files to confirm the regenerated block matches what was already there (or intentionally differs after a tokens.json edit).');
