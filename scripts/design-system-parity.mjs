#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), "utf8");
const contract = JSON.parse(read("client/design-system/token-parity.json"));
const sourceMap = JSON.parse(read("client/design-system/source-map.json"));
const files = {
  web: "client/design-system/tokens.css",
  android: "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/theme/IdsSemanticColors.kt",
  ios: "ios/Core/DesignSystem/Sources/IDS.swift",
};
const sources = Object.fromEntries(Object.entries(files).map(([platform, file]) => [platform, read(file)]));
const failures = [];
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

for (const role of contract.semanticRoles) {
  for (const [platform] of Object.entries(files)) {
    const token = contract[platform]?.tokens?.[role];
    if (!token) {
      failures.push(`${platform}: contract has no token mapping for semantic role ${role}`);
      continue;
    }
    const pattern = platform === "web"
      ? new RegExp(escapeRegExp(token) + "\\s*:")
      : new RegExp("\\b" + escapeRegExp(token) + "\\b");
    if (!pattern.test(sources[platform])) failures.push(`${platform}: missing ${role} -> ${token}`);
  }
}

const expectedLight = contract.brand?.light?.toUpperCase();
const expectedDark = contract.brand?.dark?.toUpperCase();
if (!expectedLight || !expectedDark) {
  failures.push("token-parity contract must define brand.light and brand.dark");
}
if (sourceMap.identity?.brand?.toUpperCase() !== expectedLight) {
  failures.push(`source map identity brand ${sourceMap.identity?.brand ?? "(missing)"} does not match contract light brand ${expectedLight}`);
}

function cssDeclaration(block, token) {
  const match = block.match(new RegExp("(?:^|[;\\n])\\s*" + escapeRegExp(token) + "\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;?"));
  return match?.[1]?.toUpperCase();
}
const webRootBlocks = [...sources.web.matchAll(/:root\s*\{([\s\S]*?)\}/g)].map((match) => match[1]);
const webLight = webRootBlocks.find((block) => /--itunda-color-brand\s*:/.test(block));
const webDarkMatch = sources.web.match(/\[data-theme=["']dark["']\]\s*\{([\s\S]*?)\}/);
const webDark = webDarkMatch?.[1];
const webLightBrand = webLight && cssDeclaration(webLight, "--itunda-color-brand");
const webDarkBrand = webDark && cssDeclaration(webDark, "--itunda-color-brand");
if (!webLightBrand) failures.push("web light theme: cannot resolve --itunda-color-brand to a literal hex value");
else if (webLightBrand !== expectedLight) failures.push(`web light theme: brand is ${webLightBrand}, expected ${expectedLight}`);
if (!webDarkBrand) failures.push("web dark theme: cannot resolve --itunda-color-brand to a literal hex value");
else if (webDarkBrand !== expectedDark) failures.push(`web dark theme: brand is ${webDarkBrand}, expected ${expectedDark}`);

function kotlinBrand(blockName) {
  const block = sources.android.match(new RegExp("val " + blockName + "\\s*=\\s*IdsSemanticColors\\(([\\s\\S]*?)\\n\\)"));
  if (!block) return undefined;
  const brand = block[1].match(/\bbrand\s*=\s*Color\(0xFF([0-9a-fA-F]{6})\)/);
  return brand?.[1]?.toUpperCase();
}
const androidLightBrand = kotlinBrand("IdsLightSemanticColors");
const androidDarkBrand = kotlinBrand("IdsDarkSemanticColors");
if (!androidLightBrand) failures.push("android light theme: cannot resolve semantic brand value");
else if (androidLightBrand !== expectedLight.replace("#", "")) failures.push(`android light theme: brand is #${androidLightBrand}, expected ${expectedLight}`);
if (!androidDarkBrand) failures.push("android dark theme: cannot resolve semantic brand value");
else if (androidDarkBrand !== expectedDark.replace("#", "")) failures.push(`android dark theme: brand is #${androidDarkBrand}, expected ${expectedDark}`);

const iosBrand = sources.ios.match(/public static let brand\s*=\s*Color\(light:\s*0x([0-9a-fA-F]{6}),\s*dark:\s*0x([0-9a-fA-F]{6})\)/);
if (!iosBrand) {
  failures.push("iOS: cannot resolve IDS.Colors.brand light/dark values");
} else {
  if (iosBrand[1].toUpperCase() !== expectedLight.replace("#", "")) failures.push(`iOS light theme: brand is #${iosBrand[1].toUpperCase()}, expected ${expectedLight}`);
  if (iosBrand[2].toUpperCase() !== expectedDark.replace("#", "")) failures.push(`iOS dark theme: brand is #${iosBrand[2].toUpperCase()}, expected ${expectedDark}`);
}

if (failures.length) {
  console.error("IDS semantic parity: FAIL");
  failures.forEach((failure) => console.error(" - " + failure));
  process.exit(1);
}

console.log("IDS semantic parity: PASS");
console.log(`Checked ${contract.semanticRoles.length} semantic-role mappings across Web, Android and iOS.`);
console.log(`Verified Brand v3 light/dark values (${expectedLight} / ${expectedDark}) and source-map identity.`);
