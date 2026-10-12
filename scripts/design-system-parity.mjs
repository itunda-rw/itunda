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
if (!Array.isArray(contract.semanticRoles) || contract.semanticRoles.length === 0) {
  failures.push("contract: semanticRoles must be a non-empty array");
}
if (Array.isArray(contract.semanticRoles) && new Set(contract.semanticRoles).size !== contract.semanticRoles.length) {
  failures.push("contract: semanticRoles contains duplicate role names");
}
for (const [platform, expectedSource] of Object.entries(files)) {
  if (contract[platform]?.source !== expectedSource) {
    failures.push(`contract: ${platform}.source must be ${expectedSource}`);
  }
  const tokens = contract[platform]?.tokens;
  if (!tokens || typeof tokens !== "object" || Array.isArray(tokens)) {
    failures.push(`contract: ${platform}.tokens must be an object`);
    continue;
  }
  for (const role of contract.semanticRoles ?? []) {
    if (typeof tokens[role] !== "string" || tokens[role].length === 0) {
      failures.push(`contract: missing ${platform}.tokens.${role}`);
    }
  }
  for (const role of Object.keys(tokens)) {
    if (!(contract.semanticRoles ?? []).includes(role)) {
      failures.push(`contract: unexpected ${platform}.tokens.${role}; add it to semanticRoles or remove it`);
    }
  }
}
const escapeRegex = (value) => [...value].map((char) => "\\.^$*+?()[]{}|".includes(char) ? "\\" + char : char).join("");

const source = Object.fromEntries(
  Object.entries(files).map(([platform, file]) => [
    platform,
    fs.readFileSync(path.join(root, file), "utf8"),
  ]),
);

// Legacy component-token aliases must resolve to declared IDS tokens.
// This catches silent CSS fallbacks caused by misspelled or removed foundations.
const webVariableDeclarations = new Map();
for (const match of source.web.matchAll(/(--[\\w-]+)\\s*:\\s*([^;]+);/g)) {
  webVariableDeclarations.set(match[1], match[2].trim());
}
const tdsAliases = [...webVariableDeclarations.keys()].filter((name) => name.startsWith("--tds-"));
function inspectAlias(name, trail = []) {
  if (trail.includes(name)) {
    failures.push(`web: cyclic component-token alias ${[...trail, name].join(" -> ")}`);
    return;
  }
  const value = webVariableDeclarations.get(name);
  if (typeof value !== "string") return;
  for (const match of value.matchAll(/var\\((--[\\w-]+)/g)) {
    const target = match[1];
    if (!webVariableDeclarations.has(target)) {
      failures.push(`web: component token ${name} references undefined token ${target}`);
      continue;
    }
    if (target.startsWith("--tds-")) inspectAlias(target, [...trail, name]);
  }
}
for (const alias of tdsAliases) inspectAlias(alias);

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
if (!Number.isFinite(minimumTextContrast) || minimumTextContrast < 4.5) {
  failures.push(`contract: brandText.minimumContrast must be a finite number >= 4.5, got ${contract.brandText?.minimumContrast}`);
}
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

// Compare every semantic role's resolved color values, not just whether its token name exists.
function cssVariableMap(css, theme) {
  const map = new Map();
  const blocks = [...css.matchAll(/:root\s*\{([^}]*)\}/gi)].map((match) => match[1]);
  if (theme === "dark") {
    blocks.push(css.match(/\[data-theme=["']dark["']\]\s*\{([^}]*)\}/i)?.[1] ?? "");
  }
  for (const block of blocks) {
    for (const match of block.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
      map.set(match[1], match[2].trim());
    }
  }
  return map;
}

function resolveCssColor(name, map, seen = new Set()) {
  if (seen.has(name) || !map.has(name)) return undefined;
  seen.add(name);
  const value = map.get(name);
  const alias = value.match(/^var\((--[\w-]+)\)$/i);
  if (alias) return resolveCssColor(alias[1], map, seen);
  return /^#[0-9a-f]{6}$/i.test(value) ? value.toUpperCase() : undefined;
}

function kotlinRoleValues(kotlin, role) {
  const lightBlock = kotlin.match(/val IdsLightSemanticColors\s*=\s*IdsSemanticColors\(([\s\S]*?)\n\)/)?.[1] ?? "";
  const darkBlock = kotlin.match(/val IdsDarkSemanticColors\s*=\s*IdsSemanticColors\(([\s\S]*?)\n\)/)?.[1] ?? "";
  const extract = (block) => {
    const escaped = escapeRegex(role);
    const match = block.match(new RegExp("\\b" + escaped + "\\s*=\\s*Color\\(0xFF([0-9a-f]{6})\\)", "i"));
    return match ? "#" + match[1].toUpperCase() : undefined;
  };
  return { light: extract(lightBlock), dark: extract(darkBlock) };
}

function swiftRoleValues(swift, role) {
  const escaped = escapeRegex(role);
  const match = swift.match(new RegExp("public\\s+static\\s+let\\s+" + escaped + "\\s*=\\s*Color\\(\\s*light:\\s*0x([0-9a-f]{6})\\s*,\\s*dark:\\s*0x([0-9a-f]{6})\\s*\\)", "i"));
  return match
    ? { light: "#" + match[1].toUpperCase(), dark: "#" + match[2].toUpperCase() }
    : {};
}

const cssRoleMaps = {
  light: cssVariableMap(source.web, "light"),
  dark: cssVariableMap(source.web, "dark"),
};
for (const role of contract.semanticRoles ?? []) {
  const expectedWebName = contract.web?.tokens?.[role];
  const webName = expectedWebName?.startsWith("--") ? expectedWebName : undefined;
  const androidName = contract.android?.tokens?.[role];
  const iosName = contract.ios?.tokens?.[role];
  const actual = {
    web: {
      light: webName ? resolveCssColor(webName, cssRoleMaps.light) : undefined,
      dark: webName ? resolveCssColor(webName, cssRoleMaps.dark) : undefined,
    },
    android: androidName ? kotlinRoleValues(source.android, androidName) : {},
    ios: iosName ? swiftRoleValues(source.ios, iosName) : {},
  };
  for (const theme of ["light", "dark"]) {
    const expected = actual.web[theme];
    if (!expected) {
      failures.push(`web: ${theme} semantic color for ${role} could not be resolved from ${webName ?? "missing token mapping"}`);
      continue;
    }
    for (const platform of ["android", "ios"]) {
      const value = actual[platform][theme];
      if (!value) {
        failures.push(`${platform}: ${theme} semantic color for ${role} could not be extracted from ${platform === "android" ? androidName : iosName}`);
      } else if (value !== expected) {
        failures.push(`semantic drift: ${role} ${theme} web=${expected}, ${platform}=${value}`);
      }
    }
  }
}


const textContrast = contract.textContrast ?? {
  roles: ["textPrimary", "textSecondary", "textTertiary", "textBrand"],
  backgroundRoles: ["background", "surface"],
  minimum: 4.5,
};
if (!Array.isArray(textContrast.roles) || textContrast.roles.length === 0) {
  failures.push("contract: textContrast.roles must be a non-empty array");
}
if (!Array.isArray(textContrast.backgroundRoles) || textContrast.backgroundRoles.length === 0) {
  failures.push("contract: textContrast.backgroundRoles must be a non-empty array");
}
const textContrastMinimum = Number(textContrast.minimum ?? 4.5);
if (!Number.isFinite(textContrastMinimum) || textContrastMinimum < 4.5) {
  failures.push(`contract: textContrast.minimum must be a finite number >= 4.5, got ${textContrast.minimum}`);
}

function semanticValue(platform, role, theme) {
  if (platform === "web") {
    const token = contract.web?.tokens?.[role];
    return token?.startsWith("--") ? resolveCssColor(token, cssRoleMaps[theme]) : undefined;
  }
  if (platform === "android") {
    const token = contract.android?.tokens?.[role];
    return token ? kotlinRoleValues(source.android, token)[theme] : undefined;
  }
  const token = contract.ios?.tokens?.[role];
  return token ? swiftRoleValues(source.ios, token)[theme] : undefined;
}

// Enforce normal-text WCAG AA against every declared text surface on every platform,
// not only the special brand-text token. This catches accessible-looking values that
// drift or become unreadable when a background/surface token changes.
for (const platform of ["web", "android", "ios"]) {
  for (const theme of ["light", "dark"]) {
    for (const textRole of textContrast.roles ?? []) {
      const foreground = semanticValue(platform, textRole, theme);
      if (!foreground) {
        failures.push(`${platform}: ${theme} contrast foreground ${textRole} could not be resolved`);
        continue;
      }
      for (const backgroundRole of textContrast.backgroundRoles ?? []) {
        const background = semanticValue(platform, backgroundRole, theme);
        if (!background) {
          failures.push(`${platform}: ${theme} contrast background ${backgroundRole} could not be resolved`);
          continue;
        }
        const ratio = contrastRatio(foreground, background);
        if (!Number.isFinite(ratio) || ratio < textContrastMinimum) {
          failures.push(`contrast failure: ${platform} ${theme} ${textRole} on ${backgroundRole} is ${ratio.toFixed(2)}:1; minimum ${textContrastMinimum}:1 (${foreground} on ${background})`);
        }
      }
    }
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
console.log(`Verified ${textContrast.roles.length} text roles against ${textContrast.backgroundRoles.length} surfaces in both themes on all three platforms.`);
