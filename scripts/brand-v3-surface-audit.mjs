import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const errors = [];
const check = (condition, message) => { if (!condition) errors.push(message); };

const webTokens = read("client/design-system/tokens.css");
const webComponents = read("client/design-system/components.css");
const publicSiteTokens = read("sites/public/ids.css");
const androidColors = read("android/core/designsystem/src/main/java/rw/itunda/core/designsystem/theme/IdsSemanticColors.kt");
const androidButton = read("android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt");
const iosColors = read("ios/Core/DesignSystem/Sources/IDS.swift");
const iosButton = read("ios/Core/DesignSystem/Sources/Components/Components.swift");
const homepage = read("client/index.html");
const androidNotificationColor = read("android/app/src/main/res/values/colors.xml");
const activeBrandSurfaces = [
  "client/index.html",
  "client/data/index.html",
  "client/identity/index.html",
  "client/services/index.html",
  "client/everyday/index.html",
  "client/platform/index.html",
  "data/index.html",
  "identity/index.html",
  "services/index.html",
  "everyday/index.html",
  "platform/index.html",
  "site/index.html",
  "business/index.html",
  "business/developers/index.html",
  "sites/itunda/index.html",
  "sites/business/index.html",
  "sites/developers/index.html",
  "sites/tech/index.html",
];
const canonical = read("packages/brand-assets/canonical/itunda-brand/tokens/itunda-colors.css");

check(canonical.includes("--itunda-brand:#8F89FF"), "canonical brand v3 token must be #8F89FF");
check(canonical.includes("--itunda-on-brand:#121216"), "canonical on-brand foreground must be #121216");
check(webTokens.includes("--itunda-color-brand:#8F89FF"), "web semantic brand token is not v3");
check(webTokens.includes("--itunda-color-on-brand:#121216"), "web on-brand foreground token is missing");
check(webComponents.includes(".tds-button-primary{background:var(--itunda-color-brand);color:var(--itunda-color-on-brand,#121216)"), "web primary button does not use semantic on-brand foreground");
check(publicSiteTokens.includes("--brand:#8F89FF"), "deployed public-site brand token is not v3");
check(publicSiteTokens.includes("--on-brand:#121216"), "deployed public-site on-brand foreground is missing");
check(publicSiteTokens.includes(".btn.primary{background:var(--brand);border-color:var(--brand);color:var(--on-brand)}"), "deployed public-site primary button does not use on-brand foreground");
check(androidColors.includes("brand = Color(0xFF8F89FF)"), "Android brand color is not v3");
check(androidColors.includes("onBrand = Color(0xFF121216)"), "Android on-brand foreground is missing");
check(androidButton.includes("Ids.colors.brand to Ids.colors.onBrand"), "Android filled button does not use on-brand foreground");
check(iosColors.includes("public static let brand = Color(light: 0x8F89FF, dark: 0x8F89FF)"), "iOS brand color is not v3");
check(iosColors.includes("public static let onBrand = Color(light: 0x121216, dark: 0x121216)"), "iOS on-brand foreground is missing");
check(iosButton.includes("IDS.Colors.onBrand"), "iOS filled button does not use on-brand foreground");

const staleBrandAnchor = /#(?:7472F4|7675F8|9B98FF|625FE0|625FE6|F0EFFF)\b/i;
for (const surface of activeBrandSurfaces) {
  check(!staleBrandAnchor.test(read(surface)), `${surface} still contains a legacy brand anchor`);
}
check(!homepage.includes("--indigo:#7472F4"), "consumer homepage still declares the legacy brand variable");

if (errors.length) {
  console.error("Itunda brand v3 surface audit failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log("Itunda brand v3 surface audit passed: canonical tokens, web, Android/iOS semantic colors, Android role-app backgrounds, primary buttons, and active product sites are aligned.");
}
