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
const transitCollect = read("ios/App/Sources/TransitCollectScreenView.swift");
const motoFareCollect = read("ios/App/Sources/MotoFareCollectScreenView.swift");
const requestMoney = read("ios/App/Sources/RequestMoneyScreen.swift");
const foreignCurrency = read("ios/App/Sources/ForeignCurrencyScreen.swift");
const saccoScreen = read("ios/App/Sources/SaccoScreenView.swift");
const upfrontDeposit = read("ios/App/Sources/UpfrontDepositScreen.swift");
const ikiminaScreen = read("ios/App/Sources/IkiminaScreenView.swift");
const groupAccountScreen = read("ios/App/Sources/GroupAccountScreenView.swift");
const weeklySavingsScreen = read("ios/App/Sources/WeeklySavingsScreenView.swift");
const cardScreen = read("ios/App/Sources/CardScreenView.swift");
const grow31Screen = read("ios/App/Sources/Grow31SavingsScreenView.swift");
const shopProductDetail = read("ios/Features/Shop/Sources/ShopProductDetail.swift");
const transitScreen = read("ios/App/Sources/TransitScreenView.swift");
const transactionHistory = read("ios/Features/Payments/Sources/TransactionHistoryScreen.swift");
const eatsCheckout = read("ios/Features/Eats/Sources/EatsCheckout.swift");
const investPortfolio = read("ios/Features/Wealth/Sources/InvestPortfolio.swift");
const spendingScreen = read("ios/App/Sources/SpendingScreenView.swift");
const talkChatThread = read("ios/App/Sources/TalkChatThread.swift");
const hoodMarketplaceCard = read("ios/App/Sources/HoodMarketplaceCard.swift");
const investScreen = read("ios/Features/Wealth/Sources/InvestScreenView.swift");
const stockDetail = read("ios/Features/Wealth/Sources/StockDetailScreen.swift");
const homepage = read("client/index.html");
const techBlogStyles = read("services/blog/src/styles.css");
const techBlogHtml = read("services/blog/index.html");
const techBlogPosts = read("services/blog/src/posts/index.ts");
const techBlogCoverPolicy = read("services/blog/public/images/posts/EDITORIAL_COVERS.md");
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
check(webTokens.includes("--itunda-color-on-success:#121216"), "web light-theme on-success foreground token must use accessible brand foreground");
check(webTokens.includes("--itunda-success-500:#8F89FF;"), "legacy web success token must resolve to Itunda brand violet");
check(webTokens.includes("--itunda-success-100:#F1F0FF;"), "legacy web success tint must resolve to the Brand v3 soft tint");
check((webTokens.match(/--itunda-color-on-success:#121216/g) || []).length >= 2, "web dark-theme on-success foreground token is missing");
check(webComponents.includes(".tds-button-success{background:var(--itunda-color-success);color:var(--itunda-color-on-success,#121216)}"), "web success button does not use semantic on-success foreground");
check(webComponents.includes(".tds-button-primary{background:var(--itunda-color-brand);color:var(--itunda-color-on-brand,#121216)"), "web primary button does not use semantic on-brand foreground");
check(publicSiteTokens.includes("--brand:#8F89FF"), "deployed public-site brand token is not v3");
check(publicSiteTokens.includes("--on-brand:#121216"), "deployed public-site on-brand foreground is missing");
check(publicSiteTokens.includes(".btn.primary{background:var(--brand);border-color:var(--brand);color:var(--on-brand)}"), "deployed public-site primary button does not use on-brand foreground");
check(androidColors.includes("brand = Color(0xFF8F89FF)"), "Android brand color is not v3");
check((androidColors.match(/success = Color\(0xFF8F89FF\)/g) || []).length >= 2, "Android light/dark success must use Itunda brand violet");
check(androidColors.includes("onBrand = Color(0xFF121216)"), "Android on-brand foreground is missing");
check(androidButton.includes("Ids.colors.brand to Ids.colors.onBrand"), "Android filled button does not use on-brand foreground");
check(iosColors.includes("public static let brand = Color(light: 0x8F89FF, dark: 0x8F89FF)"), "iOS brand color is not v3");
check(iosColors.includes("public static let onBrand = Color(light: 0x121216, dark: 0x121216)"), "iOS on-brand foreground is missing");
check(iosColors.includes("public static let success = Color(light: 0x8F89FF, dark: 0x8F89FF)"), "iOS success semantic must use Itunda brand violet");
check(iosButton.includes("IDS.Colors.onBrand"), "iOS filled button does not use on-brand foreground");
check(transitCollect.includes("foregroundColor(IDS.Colors.success)"), "transit collection success must use semantic brand success");
check(motoFareCollect.includes("foregroundColor(IDS.Colors.success)"), "moto fare collection success must use semantic brand success");
check(requestMoney.includes("? IDS.Colors.success : req.status"), "completed money requests must use semantic brand success");
check(foreignCurrency.includes("foregroundColor(IDS.Colors.success)"), "foreign currency success/preview must use semantic brand success");
check(saccoScreen.includes("foregroundColor(IDS.Colors.success)"), "SACCO positive amount must use semantic brand success");
check(upfrontDeposit.includes("return IDS.Colors.success") && upfrontDeposit.includes("foregroundColor(IDS.Colors.success)"), "upfront deposit success must use semantic brand success");
check(ikiminaScreen.includes("IDS.Colors.success"), "Ikimina contribution/payout success must use semantic brand success");
check(groupAccountScreen.includes("m.paid ? IDS.Colors.success"), "group-account paid status must use semantic brand success");
check(weeklySavingsScreen.includes("plan.status == \"ACTIVE\" ? IDS.Colors.success") && weeklySavingsScreen.includes("plan.streakBroken ? .red : IDS.Colors.success"), "weekly-savings success/streak must use semantic brand success");
check(cardScreen.includes("IDS.Colors.success"), "card success and positive credit-score states must use semantic brand success");
check(grow31Screen.includes("IDS.Colors.success"), "Grow31 savings success/bonus states must use semantic brand success");
check(shopProductDetail.includes("foregroundColor(IDS.Colors.success)"), "shop order-placed confirmation must use semantic brand success");
check(transitScreen.includes("foregroundColor(IDS.Colors.success)"), "transit tap confirmation must use semantic brand success");
check(transactionHistory.includes("tx.isOutgoing ? .red : IDS.Colors.success"), "incoming transaction amount must use semantic brand success");
check(eatsCheckout.includes("foregroundColor(IDS.Colors.success)"), "Eats checkout positive states must use semantic brand success");
check(investPortfolio.includes("positive ? IDS.Colors.success : .red"), "investment portfolio positive returns must use semantic brand success");
check(spendingScreen.includes("change > 0 ? .red : IDS.Colors.success"), "spending comparison positive decrease must use semantic brand success");
check(talkChatThread.includes("otherOnline ? IDS.Colors.success"), "online presence must use semantic brand success");
check(hoodMarketplaceCard.includes("foregroundColor(IDS.Colors.success)"), "marketplace escrow terminal states must use semantic brand success");
check(investScreen.includes("positive ? IDS.Colors.success : .red"), "investment list positive movement must use semantic brand success");
check(stockDetail.includes("positive ? IDS.Colors.success : .red"), "stock detail positive movement must use semantic brand success");

const staleBrandAnchor = /#(?:7472F4|7675F8|9B98FF|625FE0|625FE6|F0EFFF)\b/i;
for (const surface of activeBrandSurfaces) {
  check(!staleBrandAnchor.test(read(surface)), `${surface} still contains a legacy brand anchor`);
}
check(!homepage.includes("--indigo:#7472F4"), "consumer homepage still declares the legacy brand variable");
check(techBlogStyles.includes("--itunda-brand:#8F89FF"), "Tech blog brand token is not v3");
check(techBlogStyles.includes("--itunda-brand-strong:#7D77F7"), "Tech blog pressed brand token is not aligned with v3");
check(techBlogStyles.includes("--itunda-on-brand:#121216"), "Tech blog semantic on-brand foreground is missing");
check(techBlogStyles.includes(".header-cta{padding:10px 16px;border-radius:var(--itunda-control-radius);background:var(--itunda-brand);color:var(--itunda-on-brand)"), "Tech blog primary CTA does not use the semantic on-brand foreground");
check(techBlogHtml.includes('name="theme-color" content="#8F89FF"'), "Tech blog browser theme color is not v3");
check(techBlogPosts.includes("payments: ['#8F89FF', '#F1F0FF']"), "Tech blog payment cover palette is not v3");
check(techBlogPosts.includes("platform: ['#8F89FF', '#F1F0FF']"), "Tech blog platform cover palette is not v3");
check(techBlogCoverPolicy.includes("Brand v3 violet `#8F89FF`"), "Tech blog cover policy still documents the legacy brand");

if (errors.length) {
  console.error("Itunda brand v3 surface audit failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log("Itunda brand v3 surface audit passed: canonical tokens, web, Android/iOS semantic colors, primary buttons, and active product sites are aligned.");
}
