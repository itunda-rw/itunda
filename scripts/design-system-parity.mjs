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

const source = Object.fromEntries(
  Object.entries(files).map(([platform, file]) => [
    platform,
    fs.readFileSync(path.join(root, file), "utf8"),
  ]),
);

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

for (const role of contract.semanticRoles) {
  for (const [platform, file] of Object.entries(files)) {
    const token = contract[platform]?.tokens?.[role];
    if (typeof token !== "string" || token.length === 0) {
      failures.push(`contract: missing ${platform}.tokens.${role}`);
      continue;
    }
    const pattern = platform === "web"
      ? new RegExp(escapeRegex(token) + "\\s*:")
      : new RegExp("\\b" + escapeRegex(token) + "\\b");
    if (!pattern.test(source[platform])) failures.push(`${platform}: missing ${role} -> ${token} (${file})`);
  }
}

// Presence-only checks can miss real drift. Enforce the declared brand anchors
// against the actual light/dark semantic assignments on each platform.
const lightBrand = String(contract.brand.light).toUpperCase();
const darkBrand = String(contract.brand.dark).toUpperCase();

function cssBrandValues(css) {
  const lightMatch = css.match(/:root\s*\{[^}]*--itunda-color-brand\s*:\s*(#[0-9a-f]{6})/i);
  const darkBlock = css.match(/\[data-theme=["']dark["']\]\s*\{([^}]*)\}/i)?.[1] ?? "";
  const darkMatch = darkBlock.match(/--itunda-color-brand\s*:\s*(#[0-9a-f]{6})/i);
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

function swiftBrandValues(swift) {
  const match = swift.match(/public\s+static\s+let\s+brand\s*=\s*Color\(\s*light:\s*0x([0-9a-f]{6})\s*,\s*dark:\s*0x([0-9a-f]{6})\s*\)/i);
  return match
    ? { light: `#${match[1].toUpperCase()}`, dark: `#${match[2].toUpperCase()}` }
    : {};
}

const brandValues = {
  web: cssBrandValues(source.web),
  android: kotlinBrandValues(source.android),
  ios: swiftBrandValues(source.ios),
};

for (const [platform, values] of Object.entries(brandValues)) {
  if (values.light !== lightBrand) failures.push(`${platform}: light brand is ${values.light ?? "missing"}, expected ${lightBrand}`);
  if (values.dark !== darkBrand) failures.push(`${platform}: dark brand is ${values.dark ?? "missing"}, expected ${darkBrand}`);
}

if (failures.length) {
  console.error("IDS semantic parity: FAIL");
  failures.forEach((failure) => console.error(" - " + failure));
  process.exit(1);
}

console.log("IDS semantic parity: PASS");
console.log(`Checked ${contract.semanticRoles.length} semantic roles across Web, Android and iOS.`);
console.log(`Verified exact light/dark brand anchors (${lightBrand} / ${darkBrand}) across all three platforms.`);
