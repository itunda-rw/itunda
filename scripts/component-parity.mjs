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

/* Validate source-backed platform markers, not just the existence of broad source folders. */
for (const [name, component] of Object.entries(contract.components)) {
  for (const [platform, markers] of Object.entries(component.platformVariants ?? {})) {
    const source = component.sources?.[platform];
    if (source && source.endsWith(".swift") || source && source.endsWith(".kt") || source && source.endsWith(".css") || source && source.endsWith(".tsx") || source && source.endsWith(".ts")) {
      checkSymbol(platform, source, markers);
    } else if (source) {
      failures.push(`${platform}: ${name} platform markers need a concrete source file, got ${source}`);
    }
  }
}

checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt", ["fun IdsButton(", "IdsButtonVariant", "IdsButtonSize"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt", ["IdsButtonVariant.Destructive -> Ids.colors.danger to Ids.colors.onBrand", "IdsButtonVariant.Destructive -> Ids.colors.danger.copy(alpha = 0.35f) to Ids.colors.onBrand"]);
checkSymbol("web", "client/design-system/components.css", [".tds-button-error{background:var(--itunda-color-error);color:var(--itunda-color-on-brand,#121216)}"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsTextField.kt", ["fun IdsTextField(", "isError", "isSuccess", "loading"]);
checkSymbol("android", "android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsListRow.kt", ["fun IdsListRow(", "selected", "loading"]);
checkSymbol("ios", "ios/Core/DesignSystem/Sources/Components/Components.swift", ["struct IdsButton", "struct EmptyStateView", "struct ErrorCardView"]);
checkSymbol("ios", "ios/Core/DesignSystem/Sources/Components/Components.swift", ["variant == .filled ? IDS.Colors.onBrand", "variant == .destructive ? IDS.Colors.onBrand", "IDS.Colors.danger.opacity(0.35)", "IDS.Colors.brand.opacity(0.35)"]);
checkSymbol("web", "client/design-system/components.css", [".tds-button", ".tds-field", ".tds-list-row", ".tds-state", ".tds-state", ".tds-skeleton", ".tds-action-group"]);

console.log(`IDS component parity: ${checks.length} source checks, ${failures.length} failures`);
if (failures.length) {
  for (const failure of failures) console.error("FAIL:", failure);
  process.exit(1);
}
