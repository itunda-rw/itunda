#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const tokens = JSON.parse(fs.readFileSync(path.join(root, "client/design-system/tokens.json"), "utf8"));
const indigo500 = tokens.color.primitive.indigo["500"];
const indigo600 = tokens.color.primitive.indigo["600"];
const hexParts = hex => {
  const value = hex.replace("#", "");
  if (!/^[0-9A-Fa-f]{6}$/.test(value)) throw new Error("Expected six-digit hex color: " + hex);
  return [value.slice(0, 2), value.slice(2, 4), value.slice(4, 6)];
};
const androidColor = hex => "0xFF" + hex.replace("#", "").toUpperCase();
const iosColor = hex => {
  const [red, green, blue] = hexParts(hex);
  return `Color(red: 0x${red}/255.0, green: 0x${green}/255.0, blue: 0x${blue}/255.0)`;
};
const targets = [
  {
    file: "client/design-system/android/IDSTheme.kt",
    replacements: [
      [/val indigo500 = Color\(0xFF[0-9A-Fa-f]{6}\)/, `val indigo500 = Color(${androidColor(indigo500)})`],
      [/val indigo600 = Color\(0xFF[0-9A-Fa-f]{6}\)/, `val indigo600 = Color(${androidColor(indigo600)})`],
    ],
  },
  {
    file: "client/design-system/ios/IDS.swift",
    replacements: [
      [/public static let indigo500 = Color\(red: 0x[0-9A-Fa-f]{2}\/255\.0, green: 0x[0-9A-Fa-f]{2}\/255\.0, blue: 0x[0-9A-Fa-f]{2}\/255\.0\)/, `public static let indigo500 = ${iosColor(indigo500)}`],
      [/public static let indigo600 = Color\(red: 0x[0-9A-Fa-f]{2}\/255\.0, green: 0x[0-9A-Fa-f]{2}\/255\.0, blue: 0x[0-9A-Fa-f]{2}\/255\.0\)/, `public static let indigo600 = ${iosColor(indigo600)}`],
    ],
  },
];

const checkOnly = process.argv.includes("--check");
let drift = false;
for (const target of targets) {
  const fullPath = path.join(root, target.file);
  const original = fs.readFileSync(fullPath, "utf8");
  let generated = original;
  for (const [pattern, replacement] of target.replacements) {
    if (!pattern.test(generated)) throw new Error(`${target.file}: expected generated token declaration not found: ${pattern}`);
    generated = generated.replace(pattern, replacement);
  }
  if (generated !== original) {
    drift = true;
    if (checkOnly) console.error(`Token drift: ${target.file} differs from client/design-system/tokens.json`);
    else {
      fs.writeFileSync(fullPath, generated);
      console.log(`Updated ${target.file}`);
    }
  } else {
    console.log(`In sync: ${target.file}`);
  }
}
if (checkOnly && drift) process.exit(1);
if (checkOnly) console.log("Generated IDS indigo tokens are in sync.");
