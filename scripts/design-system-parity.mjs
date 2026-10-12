#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const contract = JSON.parse(fs.readFileSync(path.join(root, "client/design-system/token-parity.json"), "utf8"));
const files = {
  web: "client/design-system/tokens.css",
  android: "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/theme/IdsSemanticColors.kt",
  ios: "ios/Core/DesignSystem/Sources/IDS.swift",
};
const failures = [];
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\const failures = [];
");

const source = Object.fromEntries(
  Object.entries(files).map(([platform, file]) => [
    platform,
    fs.readFileSync(path.join(root, file), "utf8"),
  ]),
);

for (const role of contract.semanticRoles) {
  for (const [platform, file] of Object.entries(files)) {
    const token = contract[platform]?.tokens?.[role];
    if (typeof token !== "string" || token.length === 0) {
      failures.push(`contract: missing ${platform}.tokens.${role}`);
      continue;
    }
    const escapedToken = escapeRegex(token);
    const pattern = platform === "web"
      ? new RegExp(escapedToken + "\\s*:")
      : new RegExp("\\b" + escapedToken + "\\b");
    if (!pattern.test(source[platform])) failures.push(`${platform}: missing ${role} -> ${token} (${file})`);
  }
}

// Presence-only checks can miss real drift. Enforce the declared brand anchors
// against the actual light/dark semantic assignments on each platform.
const lightBrand = String(contract.brand.light).toUpperCase();
const darkBrand = String(contract.brand.dark).toUpperCase();
const lightTextBrand = String(contract.brandText?.light ?? "").toUpperCase();
const darkTextBrand = String(contract.brandText?.dark ?? "").toUpperCase();
const minimumTextContrast = Number(contract.brandText?.minimumContrast ?? 4.5);
const hexColor = /^#[0-9A-F]{6}$/;
for (const [label, color] of Object.entries({
  "brand.light": lightBrand,
  "brand.dark": darkBrand,
  "brandText.light": lightTextBrand,
  "brandText.dark": darkTextBrand,
})) {
  if (!hexColor.test(color)) failures.push(`contract: ${label} must be a six-digit hex color, got ${color || "(empty)"}`);
}
const lightTextBackground = String(contract.brandText?.background?.light ?? "#FFFFFF").toUpperCase();
const darkTextBackground = String(contract.brandText?.background?.dark ?? "#202027").toUpperCase();
for (const [label, color] of Object.entries({
  "brandText.background.light": lightTextBackground,
  "brandText.background.dark": darkTextBackground,
})) {
  if (!hexColor.test(color)) failures.push(`contract: ${label} must be a six-digit hex color, got ${color || "(empty)"}`);
}

function cssBrandValues(css) {
  const lightMatch = css.match(/:root\s*\{[^}]*--itunda-color-brand\s*:\s*(#[0-9a-f]{6})/i);
  const darkBlock = css.match(/\[data-theme=["']dark["']\]\s*\{([^}]*)\}/i)?.[1] ?? "";
  const darkMatch = darkBlock.match(/--itunda-color-brand\s*:\s*(#[0-9a-f]{6})/i);
  return { light: lightMatch?.[1]?.toUpperCase(), dark: darkMatch?.[1]?.toUpperCase() };
}

function cssTextBrandValues(css) {
  const lightMatch = css.match(/:root\s*\{[^}]*--itunda-color-text-brand\s*:\s*(#[0-9a-f]{6})/i);
  const darkBlock = css.match(/\[data-theme=["']dark["']\]\s*\{([^}]*)\}/i)?.[1] ?? "";
  const darkMatch = darkBlock.match(/--itunda-color-text-brand\s*:\s*(#[0-9a-f]{6})/i);
  return { light: lightMatch?.[1]?.toUpperCase(), dark: darkMatch?.[1]?.toUpperCase() };
}

function kotlinBrandValues(kotlin) {
  const lightBlock = kotlin.match(/val IdsLightSemanticColors\s*=\s*IdsSemanticColors\(([\s\S]*?)\n\)/)?.[1] ?? "";
  const darkBlock = kotlin.match(/val IdsDarkSemanticColors\s*=\s*IdsSemanticColors\(([\s\S]*?)\n\)/)?.[1] ?? "";
  const extract = (block) => block.match(/\bbrand\s*=\s*Color\(0xFF([0-9a-f]{6})\)/i)?.[1];
  return {
    light: extract(lightBlock) ? `#${extract(lightBlock).toUpperCase()}` : undefined,
    dark: extract(darkBlock) ? `#${extract(darkBlock).toUpperCase()}` : undefined,
  };
}

function kotlinTextBrandValues(kotlin) {
  const lightBlock = kotlin.match(/val IdsLightSemanticColors\s*=\s*IdsSemanticColors\(([\s\S]*?)\n\)/)?.[1] ?? "";
  const darkBlock = kotlin.match(/val IdsDarkSemanticColors\s*=\s*IdsSemanticColors\(([\s\S]*?)\n\)/)?.[1] ?? "";
  const extract = (block) => block.match(/\btextBrand\s*=\s*Color\(0xFF([0-9a-f]{6})\)/i)?.[1];
  return {
    light: extract(lightBlock) ? `#${extract(lightBlock).toUpperCase()}` : undefined,
    dark: extract(darkBlock) ? `#${extract(darkBlock).toUpperCase()}` : undefined,
  };
}

function swiftBrandValues(swift) {
  const match = swift.match(/public\s+static\s+let\s+brand\s*=\s*Color\(\s*light:\s*0x([0-9a-f]{6})\s*,\s*dark:\s*0x([0-9a-f]{6})\s*\)/i);
  return match
    ? { light: `#${match[1].toUpperCase()}`, dark: `#${match[2].toUpperCase()}` }
    : {};
}

function swiftTextBrandValues(swift) {
  const match = swift.match(/public\s+static\s+let\s+textBrand\s*=\s*Color\(\s*light:\s*0x([0-9a-f]{6})\s*,\s*dark:\s*0x([0-9a-f]{6})\s*\)/i);
  return match
    ? { light: `#${match[1].toUpperCase()}`, dark: `#${match[2].toUpperCase()}` }
    : {};
}

function channel(hex, offset) {
  const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
}

function luminance(hex) {
  return 0.2126 * channel(hex, 1) + 0.7152 * channel(hex, 3) + 0.0722 * channel(hex, 5);
}

function contrastRatio(foreground, background) {
  const a = luminance(foreground);
  const b = luminance(background);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

const brandValues = {
  web: cssBrandValues(source.web),
  android: kotlinBrandValues(source.android),
  ios: swiftBrandValues(source.ios),
};
const textBrandValues = {
  web: cssTextBrandValues(source.web),
  android: kotlinTextBrandValues(source.android),
  ios: swiftTextBrandValues(source.ios),
};

for (const [platform, values] of Object.entries(brandValues)) {
  if (values.light !== lightBrand) failures.push(`${platform}: light brand is ${values.light ?? "missing"}, expected ${lightBrand}`);
  if (values.dark !== darkBrand) failures.push(`${platform}: dark brand is ${values.dark ?? "missing"}, expected ${darkBrand}`);
}

for (const [platform, values] of Object.entries(textBrandValues)) {
  if (values.light !== lightTextBrand) failures.push(`${platform}: light textBrand is ${values.light ?? "missing"}, expected ${lightTextBrand}`);
  if (values.dark !== darkTextBrand) failures.push(`${platform}: dark textBrand is ${values.dark ?? "missing"}, expected ${darkTextBrand}`);
}

for (const [theme, foreground, background] of [
  ["light", lightTextBrand, lightTextBackground],
  ["dark", darkTextBrand, darkTextBackground],
]) {
  const ratio = contrastRatio(foreground, background);
  if (!Number.isFinite(ratio) || ratio < minimumTextContrast) {
    failures.push(`textBrand ${theme} contrast is ${ratio.toFixed(2)}:1, expected at least ${minimumTextContrast}:1 (${foreground} on ${background})`);
  }
}

if (failures.length) {
  console.error("IDS semantic parity: FAIL");
  failures.forEach((failure) => console.error(" - " + failure));
  process.exit(1);
}

console.log("IDS semantic parity: PASS");
console.log(`Checked ${contract.semanticRoles.length} semantic roles across Web, Android and iOS.`);
console.log(`Verified exact light/dark brand anchors (${lightBrand} / ${darkBrand}) across all three platforms.`);
console.log(`Verified textBrand contrast (${contrastRatio(lightTextBrand, lightTextBackground).toFixed(2)}:1 light; ${contrastRatio(darkTextBrand, darkTextBackground).toFixed(2)}:1 dark; minimum ${minimumTextContrast}:1).`);
