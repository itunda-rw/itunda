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
  const exists = fs.existsSync(absolute);
  checks.push({ platform, relativePath, exists });
  if (!exists) failures.push(`${platform}: missing source ${relativePath}`);
  return exists;
}

function checkSymbol(platform, relativePath, symbols) {
  const absolute = path.join(platformRoots[platform], relativePath);
  if (!fs.existsSync(absolute)) return;
  const source = fs.readFileSync(absolute, "utf8");
  for (const symbol of symbols) {
    if (!source.includes(symbol)) failures.push(`${platform}: ${symbol} not found in ${relativePath}`);
  }
}

for (const [name, component] of Object.entries(contract.components)) {
  for (const [platform, source] of Object.entries(component.sources)) {
    if (source.includes("/")) checkFile(platform, source);
  }
}

checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt", ["fun IdsButton(", "IdsButtonVariant", "IdsButtonSize"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsTextField.kt", ["fun IdsTextField(", "isError", "isSuccess", "loading"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsListRow.kt", ["fun IdsListRow(", "selected", "loading"]);
checkSymbol("ios", "ios/Core/DesignSystem/Sources/Components/Components.swift", ["struct IdsButton", "struct EmptyStateView", "struct ErrorCardView"]);
checkSymbol("web", "client/design-system/components.css", [".ids-button", ".ids-field", ".ids-list-row", ".ids-empty-state", ".ids-error-state", ".ids-skeleton", ".ids-bottom-cta"]);

console.log(`IDS component parity: ${checks.length} source checks, ${failures.length} failures`);
if (failures.length) {
  for (const failure of failures) console.error("FAIL:", failure);
  process.exit(1);
}
