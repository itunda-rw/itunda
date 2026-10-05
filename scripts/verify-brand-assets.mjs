#!/usr/bin/env node
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const brand = join(root, "client", "brand");

const required = [
  "symbol/itunda-symbol.svg",
  "symbol/itunda-symbol-dark.svg",
  "symbol/itunda-symbol-white.svg",
  "symbol/itunda-symbol-mono.svg",
  "symbol/itunda-symbol-small-mono.svg",
  "symbol/itunda-symbol-animated.svg",
  "app-icons/itunda-app-icon.svg",
  "app-icons/itunda-favicon.svg",
  "app-icons/itunda-ios-dark.svg",
  "app-icons/itunda-ios-tinted.svg",
  "app-icons/itunda-android-adaptive-background.svg",
  "app-icons/itunda-android-adaptive-foreground.svg",
  "app-icons/itunda-android-monochrome.svg",
  "app-icons/itunda-android-notification.svg",
  "splash/itunda-splash-light.svg",
  "splash/itunda-splash-dark.svg",
  "splash/itunda-splash-icon.svg",
  "splash/itunda-splash-icon-dark.svg",
];

const missing = required.filter((p) => !existsSync(join(brand, p)));
if (missing.length) {
  console.error("Missing canonical Itunda brand assets:");
  for (const p of missing) console.error(" - " + p);
  process.exit(1);
}

const legacy = [];
function walk(dir) {
  for (const name of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, name.name);
    if (name.isDirectory() && name.name !== ".git") walk(p);
    else if (name.isFile() && /\.(tsx?|jsx?|html|css|scss|md)$/.test(name.name)) {
      const text = readFileSync(p, "utf8");
      if (text.includes("itunda-icon.svg")) legacy.push(relative(root, p));
    }
  }
}
walk(root);

if (legacy.length) {
  console.error("Legacy itunda-icon.svg references are forbidden:");
  for (const p of legacy) console.error(" - " + p);
  process.exit(1);
}

console.log("Itunda brand asset guard passed.");
