#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const contractPath = "client/design-system/component-parity.json";
const failures = [];
let contract;

try {
  contract = JSON.parse(fs.readFileSync(path.join(root, contractPath), "utf8"));
} catch (error) {
  console.error(`IDS component parity: unable to read ${contractPath}: ${error.message}`);
  process.exit(1);
}

if (typeof contract.version !== "string" || !contract.version.trim()) {
  failures.push("contract must declare a non-empty version");
}
if (!contract.components || typeof contract.components !== "object" || Array.isArray(contract.components)) {
  failures.push("contract must define a components object");
} else {
  const components = Object.entries(contract.components);
  if (components.length === 0) failures.push("components object must not be empty");

  for (const [name, spec] of components) {
    if (!spec || typeof spec !== "object" || Array.isArray(spec)) {
      failures.push(`${name}: component specification must be an object`);
      continue;
    }
    if (!Array.isArray(spec.states) || spec.states.length === 0) {
      failures.push(`${name}: define at least one interaction/state contract`);
    }
    const hasBehaviorContract =
      (Array.isArray(spec.requirements) && spec.requirements.length > 0) ||
      (Array.isArray(spec.accessibility) && spec.accessibility.length > 0) ||
      (Array.isArray(spec.variants) && spec.variants.length > 0);
    if (!hasBehaviorContract) {
      failures.push(`${name}: define requirements, accessibility criteria, or variants`);
    }
    if (!spec.sources || typeof spec.sources !== "object" || Array.isArray(spec.sources)) {
      failures.push(`${name}: define platform source paths`);
      continue;
    }

    for (const [platform, relativePath] of Object.entries(spec.sources)) {
      if (!["android", "ios", "web"].includes(platform)) {
        failures.push(`${name}: unsupported source platform "${platform}"`);
        continue;
      }
      if (typeof relativePath !== "string" || !relativePath.trim()) {
        failures.push(`${name}: ${platform} source path must be a non-empty string`);
        continue;
      }
      const absolutePath = path.resolve(root, relativePath);
      if (!absolutePath.startsWith(root + path.sep) && absolutePath !== root) {
        failures.push(`${name}: ${platform} source path escapes repository root`);
      } else if (!fs.existsSync(absolutePath)) {
        failures.push(`${name}: ${platform} source does not exist: ${relativePath}`);
      }
    }
  }
}

if (typeof contract.completionRule !== "string" || !contract.completionRule.trim()) {
  failures.push("contract must define a completionRule");
}

if (failures.length) {
  console.error("IDS component parity: FAIL");
  failures.forEach((failure) => console.error(" - " + failure));
  process.exit(1);
}

console.log("IDS component parity: PASS");
console.log(`Validated ${Object.keys(contract.components).length} component contracts and all declared platform source paths.`);
