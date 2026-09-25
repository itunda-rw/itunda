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

const brandChecks = [
  ["web light", web, "#7472F4"],
  ["web dark", web, "#7675F8"],
  ["android light", android, "0xFF7472F4"],
  ["android dark", android, "0xFF7675F8"],
  ["ios light", ios, "0x7472F4"],
  ["ios dark", ios, "0x7675F8"],
];
for (const [label, source, anchor] of brandChecks) {
  if (!source.includes(anchor)) failures.push(`${label}: missing brand anchor ${anchor}`);
}

if (failures.length) {
  console.error("IDS semantic parity: FAIL");
  failures.forEach(f => console.error(" - " + f));
  process.exit(1);
}

console.log("IDS semantic parity: PASS");
console.log(`Checked ${contract.semanticRoles.length} semantic roles across Web, Android and iOS.`);
