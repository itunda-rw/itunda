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

for (const role of contract.semanticRoles) {
  for (const [platform, file] of Object.entries(files)) {
    const source = fs.readFileSync(path.join(root, file), "utf8");
    const token = contract[platform].tokens[role];
    const pattern = platform === "web"
      ? new RegExp(token.replace(/[.*+?^$\{\}()|[\]\\]/g, "\\$&") + "\\s*:")
      : new RegExp("\\b" + token.replace(/[.*+?^$\{\}()|[\]\\]/g, "\\$&") + "\\b");
    if (!pattern.test(source)) failures.push(`${platform}: missing ${role} -> ${token}`);
  }
}

const web = fs.readFileSync(path.join(root, files.web), "utf8");
const android = fs.readFileSync(path.join(root, files.android), "utf8");
const ios = fs.readFileSync(path.join(root, files.ios), "utf8");
const sharedAndroid = fs.readFileSync(path.join(root, "client/design-system/android/IDSTheme.kt"), "utf8");
const sharedAndroidComponents = fs.readFileSync(path.join(root, "client/design-system/android/IDSComponents.kt"), "utf8");
const sharedIos = fs.readFileSync(path.join(root, "client/design-system/ios/IDS.swift"), "utf8");
const sharedIosComponents = fs.readFileSync(path.join(root, "client/design-system/ios/IDSComponents.swift"), "utf8");

const brand = contract.brand;
const pressedSurface = contract.pressedSurface;
const toAndroidColor = value => `0xFF${value.replace("#", "")}`;
const toIosColor = value => `0x${value.replace("#", "")}`;
const expect = (label, source, pattern, expected) => {
  const match = source.match(pattern);
  if (!match || match[1].toUpperCase() !== expected.toUpperCase()) {
    failures.push(`${label}: expected ${expected}, found ${match?.[1] ?? "missing"}`);
  }
};
const webLight = web.split('[data-theme="dark"]')[0];
const webDark = web.split('[data-theme="dark"]')[1] ?? "";
expect("web light brand", webLight, /--itunda-color-brand:\s*(#[0-9A-Fa-f]{6})/i, brand.light);
expect("web on-brand foreground", web, /--itunda-color-on-brand:\s*(#[0-9A-Fa-f]{6})/i, "#121216");
expect("web dark on-brand foreground", webDark, /--itunda-color-on-brand:\s*(#[0-9A-Fa-f]{6})/i, "#121216");
expect("web dark brand", webDark, /--itunda-color-brand:\s*(#[0-9A-Fa-f]{6})/i, brand.dark);
expect("web light pressed surface", webLight, /--itunda-color-pressed:\s*(#[0-9A-Fa-f]{6})/i, pressedSurface.light);
expect("web dark pressed surface", webDark, /--itunda-color-pressed:\s*(#[0-9A-Fa-f]{6})/i, pressedSurface.dark);
expect("android light brand", android, /val IdsLightSemanticColors[\s\S]*?brand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(brand.light));
expect("android on-brand foreground", android, /onBrand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor("#121216"));
expect("android dark on-brand foreground", android, /val IdsDarkSemanticColors[\s\S]*?onBrand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor("#121216"));
expect("android dark brand", android, /val IdsDarkSemanticColors[\s\S]*?brand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(brand.dark));
expect("android light pressed surface", android, /val IdsLightSemanticColors[\s\S]*?pressed = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(pressedSurface.light));
expect("android dark pressed surface", android, /val IdsDarkSemanticColors[\s\S]*?pressed = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(pressedSurface.dark));
expect("ios brand light", ios, /public static let brand = Color\(light:\s*(0x[0-9A-Fa-f]+), dark:/, toIosColor(brand.light));
expect("ios on-brand foreground", ios, /public static let onBrand = Color\(light:\s*(0x[0-9A-Fa-f]+), dark:/, toIosColor("#121216"));
expect("ios dark on-brand foreground", ios, /public static let onBrand = Color\(light:\s*0x[0-9A-Fa-f]+, dark:\s*(0x[0-9A-Fa-f]+)/, toIosColor("#121216"));
expect("shared Android on-brand token", sharedAndroid, /val onBrand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor("#121216"));
expect("shared iOS on-brand token", sharedIos, /public static let onBrand = Color\(red:\s*(0x[0-9A-Fa-f]+)\/255\.0/, toIosColor("#121216"));
if (!/contentColor=IDSColors\.onBrand/.test(sharedAndroidComponents)) failures.push("shared Android IDSPrimaryButton: missing onBrand content color");
if (!/foregroundStyle\(IDSColor\.onBrand\)/.test(sharedIosComponents)) failures.push("shared iOS IDSPrimaryButton: missing onBrand foreground style");
expect("ios brand dark", ios, /public static let brand = Color\(light:\s*0x[0-9A-Fa-f]+, dark:\s*(0x[0-9A-Fa-f]+)/, toIosColor(brand.dark));
expect("ios pressed surface light", ios, /public static let pressed = Color\(light:\s*(0x[0-9A-Fa-f]+), dark:/, toIosColor(pressedSurface.light));
expect("ios pressed surface dark", ios, /public static let pressed = Color\(light:\s*0x[0-9A-Fa-f]+, dark:\s*(0x[0-9A-Fa-f]+)/, toIosColor(pressedSurface.dark));


const generatedTokens = JSON.parse(fs.readFileSync(path.join(root, "client/design-system/tokens.json"), "utf8"));
const canonicalCss = fs.readFileSync(path.join(root, "client/design-system/tokens.css"), "utf8");
for (const [group, steps] of Object.entries(generatedTokens.color.primitive)) {
  for (const [step, expected] of Object.entries(steps)) {
    const tokenName = `--itunda-${group}-${step}`;
    const match = canonicalCss.match(new RegExp(tokenName + "\\s*:\\s*(#[0-9A-Fa-f]{6})", "i"));
    if (!match) {
      failures.push(`primitive CSS source: missing ${tokenName}`);
    } else if (match[1].toUpperCase() !== expected.toUpperCase()) {
      failures.push(`primitive parity ${tokenName}: CSS has ${match[1]}, JSON has ${expected}`);
    }
  }
}

const generatedOutputs = {
  android: {
    file: "client/design-system/android/IDSTheme.kt",
    expected: {
      indigo500: generatedTokens.color.primitive.indigo["500"],
      indigo600: generatedTokens.color.primitive.indigo["600"],
    },
    patterns: {
      indigo500: /val indigo500 = Color\(0xFF([0-9A-Fa-f]{6})\)/,
      indigo600: /val indigo600 = Color\(0xFF([0-9A-Fa-f]{6})\)/,
    },
  },
  ios: {
    file: "client/design-system/ios/IDS.swift",
    expected: {
      indigo500: generatedTokens.color.primitive.indigo["500"],
      indigo600: generatedTokens.color.primitive.indigo["600"],
    },
    patterns: {
      indigo500: /public static let indigo500 = Color\(red: 0x([0-9A-Fa-f]{2})\/255\.0, green: 0x([0-9A-Fa-f]{2})\/255\.0, blue: 0x([0-9A-Fa-f]{2})\/255\.0\)/,
      indigo600: /public static let indigo600 = Color\(red: 0x([0-9A-Fa-f]{2})\/255\.0, green: 0x([0-9A-Fa-f]{2})\/255\.0, blue: 0x([0-9A-Fa-f]{2})\/255\.0\)/,
    },
  },
};
for (const [platform, output] of Object.entries(generatedOutputs)) {
  const source = fs.readFileSync(path.join(root, output.file), "utf8");
  for (const role of ["indigo500", "indigo600"]) {
    const match = source.match(output.patterns[role]);
    const actual = match
      ? (platform === "android" ? `#${match[1]}` : `#${match.slice(1, 4).join("")}`)
      : null;
    if (!actual || actual.toUpperCase() !== output.expected[role].toUpperCase()) {
      failures.push(`${platform} generated primitive ${role}: expected ${output.expected[role]}, found ${actual ?? "missing"}`);
    }
  }
}

if (failures.length) {
  console.error("IDS semantic parity: FAIL");
  failures.forEach(f => console.error(" - " + f));
  process.exit(1);
}
console.log("IDS semantic parity: PASS");
console.log(`Checked ${contract.semanticRoles.length} semantic roles, exact brand and pressed-surface values across Web, Android and iOS, plus generated indigo500/indigo600 outputs against client/design-system/tokens.json.`);
