import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const contract = JSON.parse(fs.readFileSync(path.join(root, "client/design-system/component-parity.json"), "utf8"));

const platformRoots = {
  android: root,
  ios: root,
  web: root,
};

const failures = [];
const checks = [];

function checkFile(platform, relativePath) {
  const absolute = path.join(platformRoots[platform], relativePath);
  const isFile = fs.existsSync(absolute) && fs.statSync(absolute).isFile();
  checks.push({ platform, relativePath, exists: isFile });
  if (!isFile) failures.push(`${platform}: source must be an existing file (directories are not valid component sources): ${relativePath}`);
  return isFile;
}

function checkSymbol(platform, relativePath, symbols) {
  const absolute = path.join(platformRoots[platform], relativePath);
  if (!fs.existsSync(absolute)) return;
  const source = fs.readFileSync(absolute, "utf8");
  for (const symbol of symbols) {
    if (!source.includes(symbol)) failures.push(`${platform}: ${symbol} not found in ${relativePath}`);
  }
}


if (!contract || typeof contract !== "object" || !contract.components || typeof contract.components !== "object" || Array.isArray(contract.components)) {
  failures.push("contract: components must be a non-empty object");
} else if (Object.keys(contract.components).length === 0) {
  failures.push("contract: components must not be empty");
}
if (typeof contract.completionRule !== "string" || contract.completionRule.trim().length === 0) {
  failures.push("contract: completionRule must explain the component completion criteria");
}
const supportedPlatforms = new Set(["web", "android", "ios"]);
for (const [componentId, component] of Object.entries(contract.components ?? {})) {
  if (!componentId.trim()) failures.push("contract: component IDs must not be empty");
  if (!component || typeof component !== "object" || Array.isArray(component)) {
    failures.push(`contract: ${componentId} must be an object`);
    continue;
  }
  if (!component.sources || typeof component.sources !== "object" || Array.isArray(component.sources) || Object.keys(component.sources).length === 0) {
    failures.push(`contract: ${componentId}.sources must be a non-empty object`);
  } else {
    for (const [platform, relativePath] of Object.entries(component.sources)) {
      if (!supportedPlatforms.has(platform)) failures.push(`contract: ${componentId}.sources has unsupported platform ${platform}`);
      if (typeof relativePath !== "string" || !relativePath.trim() || path.isAbsolute(relativePath) || relativePath.split(/[\\\\/]/).includes("..")) {
        failures.push(`contract: ${componentId}.sources.${platform} must be a safe repository-relative path`);
      }
    }
  }
  for (const field of ["states", "variants", "sizes", "accessibility", "requirements"]) {
    if (component[field] !== undefined) {
      const values = component[field];
      if (!Array.isArray(values) || values.length === 0 || values.some(value => typeof value !== "string" || !value.trim())) {
        failures.push(`contract: ${componentId}.${field} must be a non-empty array of non-empty strings`);
      } else if (new Set(values).size !== values.length) {
        failures.push(`contract: ${componentId}.${field} contains duplicates`);
      }
    }
  }
  if (Array.isArray(component.states) && component.states.length === 0) {
    failures.push(`contract: ${componentId}.states must define at least one state`);
  }
}

for (const [name, component] of Object.entries(contract.components ?? {})) {
  if (!component || typeof component !== "object" || Array.isArray(component)) continue;
  for (const [platform, source] of Object.entries(component.sources ?? {})) {
    // Invalid contract entries should produce actionable failures above, not crash the
    // checker while trying to resolve an unknown platform or unsafe path.
    if (!supportedPlatforms.has(platform)) continue;
    if (typeof source !== "string" || !source.trim() || path.isAbsolute(source) || source.split(/[\\\\/]/).includes("..")) continue;
    if (source.includes("/")) checkFile(platform, source);
  }
}

checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt", ["fun IdsButton(", "IdsButtonVariant", "IdsButtonSize"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsTextField.kt", ["fun IdsTextField(", "isError", "isSuccess", "loading"]);
checkSymbol("ios", "ios/Core/DesignSystem/Sources/Components/Components.swift", ["public struct IdsTextField", "errorText", "successText", "autoFocus"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsListRow.kt", ["fun IdsListRow(", "selected", "loading"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/HoodShared.kt", ["fun EmptyState(", "fun ErrorCard(", "fun SkeletonBlock("]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsKeyboardDockedButton.kt", ["fun FixedBottomCta(", "verticalScroll(rememberScrollState())", "cta()"]);
checkSymbol("ios", "ios/Core/DesignSystem/Sources/Components/Components.swift", ["struct IdsButton", "struct EmptyStateView", "struct ErrorCardView", "public struct FixedBottomCTA", "public struct IdsSkeletonBlock"]);
checkSymbol("web", "client/design-system/components.css", [".tds-button", ".tds-field", ".tds-list-row", ".tds-state", ".tds-skeleton", ".tds-action-group", ".tds-bottom-cta"]);

console.log(`IDS component parity: ${Object.keys(contract.components ?? {}).length} component contracts, ${checks.length} source checks, ${failures.length} failures`);
if (failures.length) {
  for (const failure of failures) console.error("FAIL:", failure);
  process.exit(1);
}
