#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const manifest = JSON.parse(fs.readFileSync(path.join(root, "sdk-manifest.json"), "utf8"));
const bridge = fs.readFileSync(
  path.join(root, "packages/react-native/src/async-bridges.ts"),
  "utf8"
);

const exported = [...bridge.matchAll(/^export \{ (\w+) \} from /gm)].map((m) => m[1]);
const declared = Object.values(manifest.capabilities).flatMap((cap) => cap.methods);
const exportSet = new Set(exported);
const declaredSet = new Set(declared);
const missing = declared.filter((method) => !exportSet.has(method));
const undocumented = exported.filter((method) => !declaredSet.has(method));

if (missing.length || undocumented.length) {
  if (missing.length) console.error("Missing SDK exports: " + missing.join(", "));
  if (undocumented.length) console.error("Undocumented SDK exports: " + undocumented.join(", "));
  process.exit(1);
}

if (manifest.protocol !== "saronite" || manifest.protocolVersion !== 1) {
  console.error("Unsupported Saronite protocol manifest.");
  process.exit(1);
}

console.log(
  "Saronite SDK manifest valid: " +
    declaredSet.size +
    " capability methods across " +
    Object.keys(manifest.capabilities).length +
    " domains."
);
