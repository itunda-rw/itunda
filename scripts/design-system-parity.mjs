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
expect("web dark brand", webDark, /--itunda-color-brand:\s*(#[0-9A-Fa-f]{6})/i, brand.dark);
expect("web light pressed surface", webLight, /--itunda-color-pressed:\s*(#[0-9A-Fa-f]{6})/i, pressedSurface.light);
expect("web dark pressed surface", webDark, /--itunda-color-pressed:\s*(#[0-9A-Fa-f]{6})/i, pressedSurface.dark);
expect("android light brand", android, /val IdsLightSemanticColors[\s\S]*?brand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(brand.light));
expect("android dark brand", android, /val IdsDarkSemanticColors[\s\S]*?brand = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(brand.dark));
expect("android light pressed surface", android, /val IdsLightSemanticColors[\s\S]*?pressed = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(pressedSurface.light));
expect("android dark pressed surface", android, /val IdsDarkSemanticColors[\s\S]*?pressed = Color\((0x[0-9A-Fa-f]{8})\)/, toAndroidColor(pressedSurface.dark));
expect("ios brand light", ios, /public static let brand = Color\(light:\s*(0x[0-9A-Fa-f]+), dark:/, toIosColor(brand.light));
expect("ios brand dark", ios, /public static let brand = Color\(light:\s*0x[0-9A-Fa-f]+, dark:\s*(0x[0-9A-Fa-f]+)/, toIosColor(brand.dark));
expect("ios pressed surface light", ios, /public static let pressed = Color\(light:\s*(0x[0-9A-Fa-f]+), dark:/, toIosColor(pressedSurface.light));
expect("ios pressed surface dark", ios, /public static let pressed = Color\(light:\s*0x[0-9A-Fa-f]+, dark:\s*(0x[0-9A-Fa-f]+)/, toIosColor(pressedSurface.dark));

if (failures.length) {
  console.error("IDS semantic parity: FAIL");
  failures.forEach(f => console.error(" - " + f));
  process.exit(1);
}
console.log("IDS semantic parity: PASS");
console.log(`Checked ${contract.semanticRoles.length} semantic roles plus exact brand and pressed-surface values across Web, Android and iOS.`);
