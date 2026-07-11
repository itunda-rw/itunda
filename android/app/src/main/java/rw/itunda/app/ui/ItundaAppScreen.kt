package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import rw.itunda.core.designsystem.theme.TdsTheme
import rw.itunda.core.designsystem.theme.Tds

// Aliased to the real theme-reactive design-system tokens (see
// core/designsystem/theme/TdsSemanticColors.kt) rather than the ad-hoc,
// half dark-mode-aware set this file used to hand-roll -- kept as thin
// aliases (not a full rename) since this file's Composables all reference
// these names throughout; the fix was making the values real, not renaming
// every call site.
private val TossBlue: Color
    @Composable get() = Tds.colors.brand
private val TossBackground: Color
    @Composable get() = Tds.colors.background
private val TossCard: Color
    @Composable get() = Tds.colors.surface
private val TossCardSoft: Color
    @Composable get() = Tds.colors.surfaceSoft
private val TossText: Color
    @Composable get() = Tds.colors.textPrimary
private val TossSecondary: Color
    @Composable get() = Tds.colors.textSecondary
private val TossTertiary: Color
    @Composable get() = Tds.colors.textTertiary
private val TossLine: Color
    @Composable get() = Tds.colors.divider
private val TossChip: Color
    @Composable get() = Tds.colors.chip

// Fixed vivid accent colors for the small product-icon badges in
// FlatSection rows (갈아타기/서비스/외화/목돈굴리기/연금/대출 등) -- these are
// brand/product colors in real Toss, not semantic theme colors, so unlike
// TossBlue etc. above they intentionally stay constant across light/dark.
private val AccentBlue = Color(0xFF3182F6)
private val AccentTeal = Color(0xFF14AE85)
private val AccentPurple = Color(0xFF7C5CFC)
private val AccentOrange = Color(0xFFF2A93B)
private val AccentRed = Color(0xFFFF5B5B)
private val AccentPink = Color(0xFFEC5F8C)
private val AccentGray = Color(0xFF6B7684)

private enum class TossTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Benefits("Benefits", Icons.Outlined.CardGiftcard),
    Shop("Shop", Icons.Outlined.ShoppingBag),
    Pay("Pay", Icons.Outlined.QrCodeScanner),
    All("All", Icons.Outlined.Apps)
}

/**
 * Real top-level navigation for the transfer flow -- a full-screen takeover
 * over the tab scaffold, matching how the reference screenshots show it
 * (no bottom nav visible during recipient/amount entry). Genuinely wired
 * to a visible entry point (WalletHeroCard's "Send" button), not built and
 * left unreachable like the screens it replaces.
 */
private sealed class TransferStep {
    data object Recipient : TransferStep()
    data class Amount(val accountNumber: String) : TransferStep()
}

@Composable
fun ItundaAppScreen(viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    TdsTheme {
        var selectedTab by remember { mutableStateOf(TossTab.Home) }
        var transferStep by remember { mutableStateOf<TransferStep?>(null) }
        var biometricError by remember { mutableStateOf<String?>(null) }
        val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
        val biometricAuth = remember(activity) { rw.itunda.core.identity.NIDABiometricAuth(activity) }

        val step = transferStep
        if (step != null) {
            when (step) {
                is TransferStep.Recipient -> rw.itunda.feature.payments.impl.RecipientEntryScreen(
                    onBack = { transferStep = null },
                    onNext = { accountNumber -> transferStep = TransferStep.Amount(accountNumber) }
                )
                is TransferStep.Amount -> {
                    rw.itunda.feature.payments.impl.TransferAmountScreen(
                        recipientAccountNumber = step.accountNumber,
                        onBack = { transferStep = TransferStep.Recipient },
                        onConfirm = { amountRwf ->
                            // Toss-style biometric confirmation gate before a transfer
                            // completes -- see docs/ARCHITECTURE.md's NIDABiometricAuth
                            // note. Still local-state-only (no backend session yet, see
                            // TransferFlow.kt's header), so "success" here means the
                            // sheet closes, not that money actually moved.
                            biometricError = null
                            biometricAuth.authenticateForTransaction(
                                reason = "Confirm sending $amountRwf RWF"
                            ) { success, error ->
                                if (success) {
                                    transferStep = null
                                } else {
                                    biometricError = error ?: "Couldn't verify. Try again."
                                }
                            }
                        }
                    )
                    biometricError?.let { message ->
                        androidx.compose.material3.Text(
                            text = message,
                            color = Tds.colors.danger,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            return@TdsTheme
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                TossBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    TossTab.Home -> HomeTab(viewModel, onSend = { transferStep = TransferStep.Recipient })
                    TossTab.Benefits -> BenefitsTab()
                    TossTab.Shop -> ShopTab(viewModel)
                    TossTab.Pay -> PayTab()
                    TossTab.All -> AllTab()
                }
            }
        }
    }
}

/**
 * Toss's real bottom nav is a flat, edge-to-edge bar with a hairline top
 * divider and real icons -- not a floating rounded pill with letter-glyph
 * placeholders, which is what this was before and read as an unfinished
 * wireframe rather than an actual app (feedback from comparing directly
 * against real Toss screenshots, 2026-07-10).
 */
@Composable
private fun TossBottomBar(selectedTab: TossTab, onSelect: (TossTab) -> Unit) {
    Column {
        Divider(color = TossLine, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TossCard)
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TossTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) TossBlue else TossTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) TossBlue else TossTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(viewModel: MainViewModel, onSend: () -> Unit) {
    val primaryWallet by viewModel.primaryWallet.collectAsState()
    val balanceText = primaryWallet?.let { "${it.currency} %,.0f".format(it.balance) } ?: "RWF 0"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // top/bottom kept as their own literal values, not forced into
        // screenVertical/sectionGap -- they're genuinely different from the other
        // 4 tabs' uniform vertical padding, and TdsLayout.kt's own header explains
        // why this pass doesn't force every value into a token that doesn't
        // actually fit.
        contentPadding = PaddingValues(start = Tds.layout.screenHorizontal, top = 14.dp, end = Tds.layout.screenHorizontal, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)
    ) {
        item { HomeTopBar() }
        item { WalletHeroCard(balanceText, onSend) }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    ShellRow("RWF 463,022", "Spent in July", "3 new", Icons.Outlined.PieChart, AccentPurple),
                    ShellRow("Transfer cashback", "BK account -> TUYIZERE Eric", "Claim", Icons.Outlined.Payments, AccentBlue),
                    ShellRow("Sprinkle money to friends", "19:03:55 left", "Send", Icons.Outlined.Redeem, AccentOrange)
                )
            )
        }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    ShellRow("Get cashback every time you pay", "", ">", Icons.Outlined.Payments, AccentBlue),
                    ShellRow("Pay with face ID", "", ">", Icons.Outlined.Face, AccentPurple),
                    ShellRow("Receive government alerts", "", ">", Icons.Outlined.Campaign, AccentRed)
                )
            )
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real search bar, not an empty placeholder box -- the previous
        // version here was a Box() with a background color and no children
        // at all, a genuine leftover bug (found comparing directly against
        // real Toss screenshots, 2026-07-10).
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(TossCardSoft)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text("Search", color = TossSecondary, fontSize = 15.sp)
        }
        TopIconButton(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR code")
        TopIconButton(Icons.Outlined.Notifications, contentDescription = "Notifications")
    }
}

// Fixed (2026-07-11): this shared icon-only button had no way to tell a screen reader
// what any given instance actually does -- contentDescription was hardcoded null
// regardless of which icon was passed in. A required contentDescription param means a
// new call site can't silently reintroduce the bug the way an optional/defaulted
// param could.
//
// Touch target bumped 44dp -> 48dp (2026-07-11): 44dp cleared WCAG's 44px baseline
// but sat below Material Design's own 48dp recommendation, flagged as an open gap in
// docs/ACCESSIBILITY.md. Every call site sits in a Row with a flexible weight(1f)
// sibling or SpaceBetween arrangement, so the extra 4dp per button is absorbed by
// that flexible space rather than causing overflow -- checked each of the 3 call
// sites' surrounding layout before changing this shared component.
@Composable
private fun TopIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(Tds.layout.minTouchTarget)
            .clip(CircleShape)
            .background(TossCardSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = TossText)
    }
}

@Composable
private fun WalletHeroCard(balanceText: String, onSend: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Wallet", fontSize = 14.sp, color = TossSecondary)
            Text(balanceText, fontSize = 34.sp, color = TossText, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction("Add money", Modifier.weight(1f), false) {}
                PrimaryAction("Send", Modifier.weight(1f), true, onSend)
            }
            Divider(color = TossLine)
            WalletMiniRow("RWF 613", "Bravo Korea parking", "Send")
            WalletMiniRow("RWF 7,489", "Savings deposit", "Send")
            Text(
                "See all",
                modifier = Modifier.fillMaxWidth(),
                color = TossSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PrimaryAction(title: String, modifier: Modifier = Modifier, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) TossBlue else Color(0xFF1F3053))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = TossText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun WalletMiniRow(amount: String, subtitle: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TossChip),
            contentAlignment = Alignment.Center
        ) {
            // Was amount.take(1) -- literally the first character of the RWF
            // string as an "icon" (e.g. "R"), a real leftover bug, not a
            // deliberate placeholder. Real icon now.
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossText)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(amount, color = TossText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = TossSecondary, fontSize = 14.sp)
        }
        SmallBlueButton(action)
    }
}

@Composable
private fun SmallBlueButton(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF223554))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TossBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private data class ShellRow(
    val title: String,
    val subtitle: String,
    val action: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color = AccentBlue
)

@Composable
private fun ShellSection(title: String, rows: List<ShellRow>) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (title.isNotEmpty()) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TossText)
                Spacer(modifier = Modifier.height(8.dp))
            }
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(row.iconColor),
                        contentAlignment = Alignment.Center
                    ) {
                        // Was showing row.third (the action label, e.g. "3 new"
                        // or "Claim") crammed into a 42dp icon box -- a real bug,
                        // not a placeholder; it also rendered a second time below
                        // via SmallBlueButton whenever longer than one character.
                        // Then briefly row.first's initial as a stopgap, then a
                        // real icon but on a flat muted TossChip background --
                        // real Toss's card-list icon badges (송금/자산 reference
                        // screenshots) are vivid per-item brand colors, not one
                        // neutral gray tone reused everywhere.
                        Icon(row.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TossText)
                        if (row.subtitle.isNotEmpty()) {
                            Text(row.subtitle, fontSize = 14.sp, color = TossSecondary)
                        }
                    }
                    if (row.action == ">") {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TossTertiary)
                    } else if (row.action.isNotBlank()) {
                        SmallBlueButton(row.action)
                    }
                }
                if (index != rows.lastIndex) {
                    Divider(color = TossLine)
                }
            }
        }
    }
}

@Composable
private fun BenefitsTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)
    ) {
        item { PlainTopBar("Benefits") }
        item { PromoBannerCard() }
        item { PointPill("P 137") }
        item { BenefitsVisitCard() }
        item { CashbackChanceCard() }
    }
}

@Composable
private fun ShopTab(viewModel: MainViewModel) {
    val discoverItems by viewModel.discoverItems.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)
    ) {
        item { ShopTopBar() }
        item { CategoryTabsRow(listOf("Home", "Categories", "Cycling", "Deals", "Summer food")) }
        item { ShopPromoCard() }
        
        if (discoverItems.isNotEmpty()) {
            item {
                ShellSection(
                    title = "Discover",
                    rows = discoverItems.take(3).map { item ->
                        ShellRow(item.title, item.subtitle, item.badge ?: ">", Icons.Outlined.Storefront, AccentTeal)
                    }
                )
            }
        }

        item { PointActionsCard() }
    }
}

@Composable
private fun PayTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)
    ) {
        item { PayTopBar() }
        item { MapPlaceholder() }
        item { PayFeatureCard() }
        item { ShellSection("", listOf(
            ShellRow("Points and pay money", "Total RWF 31,031", " ", Icons.Outlined.Payments, AccentBlue),
            ShellRow("Received coupons", "", " ", Icons.Outlined.LocalOffer, AccentOrange)
        )) }
    }
}

@Composable
private fun AllTab() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)
    ) {
        item { AllTopBar() }
        item { SearchBar("Search") }
        item {
            IconGridSection("Quick access", listOf(
                "Mini" to Icons.Outlined.Apps,
                "Games" to Icons.Outlined.SportsEsports,
                "Bank" to Icons.Outlined.AccountBalance,
                "Pick" to Icons.Outlined.Star
            ))
        }
        item {
            MiniAppsSection(
                onWalletBalance = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.WalletBalanceMiniAppActivity::class.java))
                },
                onPayBills = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.PayBillsMiniAppActivity::class.java))
                },
                onRewardTasks = {
                    context.startActivity(android.content.Intent(context, rw.itunda.app.miniapps.RewardTasksMiniAppActivity::class.java))
                }
            )
        }
        item {
            IconGridSection("Recent services", listOf(
                "Open acct" to Icons.Outlined.AddCircleOutline,
                "Photo transfer" to Icons.Outlined.CameraAlt,
                "Verify" to Icons.Outlined.VerifiedUser,
                "Send" to Icons.Outlined.Send,
                "Group" to Icons.Outlined.Group,
                "Property" to Icons.Outlined.HomeWork,
                "Insurance" to Icons.Outlined.Shield,
                "More" to Icons.Outlined.MoreHoriz
            ))
        }
        item {
            FlatSection("Financial services", listOf(
                FlatRow("Open account", subtitle = "Itunda Wallet, other banks, RSE brokerage", icon = Icons.Outlined.AddCircleOutline, iconColor = AccentBlue),
                FlatRow("My assets", subtitle = "Accounts, loans, RSE holdings, cards, points", icon = Icons.Outlined.PieChart, iconColor = AccentPurple),
                FlatRow("Get a loan", subtitle = "Personal, salary-backed, SME working capital", icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue),
                FlatRow("Mobile plan", subtitle = "MTN, Airtel, broadband", icon = Icons.Outlined.Public, iconColor = AccentTeal)
            ))
        }

        // Everything below is modeled directly on the real Toss Bank
        // 갈아타기/신용카드/체크카드/서비스/외화/목돈굴리기/연금/대출/알림 및 동의/고객센터
        // reference screens (user-provided, 2026-07-10), adapted to Rwanda
        // rails per docs/FACT_CHECKED_TOSS_RWANDA_MAP.md's established
        // mapping (REG/WASAC/Irembo/RRA, MTN MoMo/Airtel Money, RSE tickers,
        // RSSB pension) rather than left as Korean-market content.
        item {
            FlatSection("Switch & save", listOf(
                FlatRow("Switch your personal loan", trailing = "12% ~ 24%", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue),
                FlatRow("Switch your rent deposit loan", trailing = "9% ~ 15%", trailingIsLink = true, icon = Icons.Outlined.HomeWork, iconColor = AccentTeal),
                FlatRow("Switch your SME loan", trailing = "11% ~ 22%", trailingIsLink = true, icon = Icons.Outlined.Storefront, iconColor = AccentTeal)
            ))
        }
        item {
            FlatSection("Cards", listOf(
                FlatRow("Itunda Card", trailing = "5% back on bills", trailingIsLink = true, icon = Icons.Outlined.CreditCard, iconColor = AccentRed),
                FlatRow("Virtual card", trailing = "Instant issue", icon = Icons.Outlined.CreditCard, iconColor = AccentGray)
            ))
        }
        item {
            FlatSection("Services", listOf(
                FlatRow("Rent deposit protection", icon = Icons.Outlined.HomeWork, iconColor = AccentBlue),
                FlatRow("Recurring payments", icon = Icons.Outlined.Description, iconColor = AccentBlue),
                FlatRow("Import recurring payments", icon = Icons.Outlined.LocalShipping, iconColor = AccentGray),
                FlatRow("REG & WASAC bills", icon = Icons.Outlined.Bolt, iconColor = AccentBlue),
                FlatRow("Claim interest now", icon = Icons.Outlined.Bolt, iconColor = AccentPurple),
                FlatRow("SME income tax estimate", icon = Icons.Outlined.Savings, iconColor = AccentOrange),
                FlatRow("Split a bill with friends", icon = Icons.Outlined.Groups, iconColor = AccentBlue),
                FlatRow("Shared calendar", icon = Icons.Outlined.CalendarMonth, iconColor = AccentBlue),
                FlatRow("Kids' allowance tasks", icon = Icons.Outlined.CheckCircle, iconColor = AccentOrange)
            ))
        }
        item {
            FlatSection("Foreign currency", listOf(
                FlatRow("Foreign currency wallet", trailing = "100% rate preference", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentPurple),
                FlatRow("International transfer", icon = Icons.Outlined.AttachMoney, iconColor = AccentBlue)
            ))
        }
        item {
            FlatSection("Grow your money", listOf(
                FlatRow("RSE stocks", subtitle = "BOK, MTNR, BLR, IMR, CMR, EQTY", icon = Icons.Outlined.ShowChart, iconColor = AccentTeal),
                FlatRow("Bonds & fixed income", trailing = "7.5% ~ 12%", trailingIsLink = true, icon = Icons.Outlined.AccountBalance, iconColor = AccentBlue),
                FlatRow("IPO schedule", icon = Icons.Outlined.TrendingUp, iconColor = AccentRed),
                FlatRow("Brokerage account", trailing = "Up to 30,000 RWF", trailingIsLink = true, icon = Icons.Outlined.AccountBalance, iconColor = AccentTeal)
            ))
        }
        item {
            FlatSection("Pension", listOf(
                FlatRow("Check my RSSB pension", icon = Icons.Outlined.AccountBalance, iconColor = AccentBlue),
                FlatRow("Pension products", icon = Icons.Outlined.Percent, iconColor = AccentBlue)
            ))
        }
        item {
            FlatSection("Loans", listOf(
                FlatRow("Check my max limit", icon = Icons.Outlined.TrendingUp, iconColor = AccentPurple),
                FlatRow("Personal loan", trailing = "11% ~ 24%", trailingIsLink = true, icon = Icons.Outlined.AccountBalanceWallet, iconColor = AccentBlue)
            ))
        }
        item {
            FlatSection("Notifications & consent", listOf(
                FlatRow("Notifications", showChevron = true),
                FlatRow("Credit data usage policy", showChevron = true),
                FlatRow("Privacy policy", showChevron = true),
                FlatRow("Terms & consent", showChevron = true)
            ))
        }
        item {
            FlatSection("Support", listOf(
                FlatRow("FAQ", showChevron = true),
                FlatRow("Live chat", showChevron = true),
                FlatRow("Call support", showChevron = true),
                FlatRow("Report fraud", showChevron = true),
                FlatRow("Announcements", showChevron = true)
            ))
        }
    }
}

/**
 * Real entry point for Apps-in-Itunda mini-apps -- launches the genuine
 * ReactActivity subclasses in rw.itunda.app.miniapps, each loading a real RN
 * bundle from packages/saronite/mini-apps, not a placeholder screen.
 *
 * Flat, no card wrapper -- matches the real Toss settings/service screens
 * (송금, 전체 서비스, 고객센터 reference screenshots, 2026-07-10): rows sit
 * directly on the screen background, grouped by a small label, separated by
 * hairline dividers, not floated in an isolated white/gray card island. The
 * earlier card-per-section treatment read as generic fintech-app UI, not
 * Toss's actual, much flatter composition.
 */
@Composable
private fun MiniAppsSection(
    onWalletBalance: () -> Unit,
    onPayBills: () -> Unit,
    onRewardTasks: () -> Unit
) {
    FlatSection(
        title = "Mini apps",
        rows = listOf(
            FlatRow("Wallet balance", onClick = onWalletBalance),
            FlatRow("Pay bills", onClick = onPayBills),
            FlatRow("Reward tasks", onClick = onRewardTasks)
        )
    )
}

private data class FlatRow(
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val trailingIsLink: Boolean = false,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val iconColor: Color = AccentBlue,
    val showChevron: Boolean = false,
    val onClick: () -> Unit = {}
)

/**
 * The real Toss list pattern, matched directly against the reference
 * screenshots (user-provided, 2026-07-10) of 갈아타기/신용카드/체크카드/서비스/
 * 외화/목돈굴리기/연금/대출: a bold white section header (not a small gray
 * label), then plain rows with NO divider between them and NO card
 * background -- only a gap between different sections. Every row in that
 * product-list pattern carries a small colorful square icon (never a
 * chevron); a right-aligned value in brand blue appears only when there's
 * a real number/status to show (interest rate, discount). A second,
 * separate pattern exists for legal/settings lists (알림 및 동의, 고객센터):
 * no icon at all, plain chevron on the right -- selected per-row via
 * showChevron since both patterns can appear in the same screen.
 */
@Composable
private fun FlatSection(title: String, rows: List<FlatRow>) {
    Column {
        Text(
            title,
            color = TossText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = row.onClick)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (row.icon != null) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(row.iconColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(row.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column {
                        Text(row.title, color = TossText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        if (row.subtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(row.subtitle, color = TossTertiary, fontSize = 13.sp)
                        }
                    }
                }
                if (row.trailing != null) {
                    Text(
                        row.trailing,
                        color = if (row.trailingIsLink) TossBlue else TossSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (row.trailingIsLink) FontWeight.SemiBold else FontWeight.Normal
                    )
                } else if (row.showChevron) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TossTertiary)
                }
            }
        }
    }
}

@Composable
private fun PlainTopBar(title: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("...", color = TossText, fontSize = 24.sp)
    }
}

@Composable
private fun PromoBannerCard() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Color(0xFF5D2FE6))) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(20.dp)) {
            Column {
                Text(
                    // Toss's real open-source emoji font (github.com/toss/tossface),
                    // bundled from the actual release asset -- not a generic system
                    // emoji glyph.
                    "🎁 Limited gift for Rwanda",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = rw.itunda.core.designsystem.theme.TossFaceFontFamily
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("25,000", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFEF56FF)).padding(horizontal = 26.dp, vertical = 12.dp)) {
                    Text("Redeem for free", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PointPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TossChip)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = TossText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BenefitsVisitCard() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Visit 3 of 4 services and earn points", color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            listOf(
                "Happy lottery" to Icons.Outlined.Casino,
                "Push the button" to Icons.Outlined.TouchApp,
                "Try on" to Icons.Outlined.Checkroom,
                "Bring friends" to Icons.Outlined.PersonAddAlt
            ).forEach { (title, icon) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossText)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(title, modifier = Modifier.weight(1f), color = TossText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    SmallBlueButton("Visit")
                }
            }
        }
    }
}

@Composable
private fun CashbackChanceCard() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "🍀 3 chances to get money back",
                color = TossText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = rw.itunda.core.designsystem.theme.TossFaceFontFamily
            )
            Text("We will notify you when new chances are available", color = TossSecondary, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF246BFF)), contentAlignment = Alignment.Center) {
                    // Was the Korean Won symbol ("₩") -- wrong currency
                    // entirely for a Rwanda app; real icon now.
                    Icon(Icons.Outlined.CurrencyExchange, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("RWF 5,000", color = TossText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("BK account -> TUYIZERE Eric", color = TossSecondary)
                }
                SmallBlueButton("Get back")
            }
        }
    }
}

@Composable
private fun ShopTopBar() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SearchBar("Search products")
        Spacer(modifier = Modifier.width(12.dp))
        // Fixed (2026-07-11): standalone icon-only buttons, no adjacent text label --
        // contentDescription = null left a screen reader with no way to know what
        // either one does, unlike the many *decorative* icons elsewhere in this file
        // that correctly stay null because they sit next to their own visible Text().
        Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = TossText)
        Spacer(modifier = Modifier.width(12.dp))
        Icon(Icons.Outlined.ShoppingCart, contentDescription = "Cart", tint = TossText)
    }
}

@Composable
private fun SearchBar(placeholder: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TossCardSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(placeholder, color = TossSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun CategoryTabsRow(tabs: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        tabs.forEachIndexed { index, tab ->
            Text(tab, color = if (index == 0) TossText else TossSecondary, fontSize = 17.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun ShopPromoCard() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Color(0xFFDDEFFC))) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("10,000 RWF early-bird", color = Color(0xFFE25A61), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Calcium + Magnesium\n90 tablets 3,900 RWF", color = Color(0xFF151515), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PointActionsCard() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossBackground)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Points and coupon tasks", color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "Check-in" to Icons.Outlined.EventAvailable,
                    "Scroll" to Icons.Outlined.Swipe,
                    "Feed" to Icons.Outlined.DynamicFeed,
                    "Cat" to Icons.Outlined.Pets,
                    "Pick" to Icons.Outlined.Star
                ).forEach { (label, icon) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = TossText)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(label, color = TossSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PayTopBar() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("itunda pay", color = TossText, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Fixed (2026-07-11): standalone icon-only buttons -- "itunda pay" above
            // is this screen's title, not a label for these two icons specifically.
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR code", tint = TossText)
            Icon(Icons.Outlined.Public, contentDescription = "Language", tint = TossText)
        }
    }
}

@Composable
private fun MapPlaceholder() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFE4D7))) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.BottomCenter) {
            Box(modifier = Modifier.padding(bottom = 18.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF202228)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("5 nearby stores", color = TossText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PayFeatureCard() {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossBlue)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("itunda pay", color = TossSecondary)
                    Text("30% rewards at partner stores", color = TossBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                SmallBlueButton("Find store")
            }
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TossCardSoft).padding(18.dp)) {
                Text("Apply pay money and points automatically", color = TossSecondary, fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossText)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("How to pay online", color = TossText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Use Itunda Pay on e-commerce and partner stores", color = TossSecondary)
                }
                SmallBlueButton("See")
            }
        }
    }
}

// Was a text navbar -- "ID | Support | Settings" with pipe separators --
// a website convention with no equivalent anywhere in real Toss. The
// 전체 (All) tab top bar is just the user's name plus a single settings
// icon button; support/ID live as rows further down the list, not up here.
@Composable
private fun AllTopBar() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("TUYIZERE ERIC", color = TossText, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        TopIconButton(Icons.Outlined.Settings, contentDescription = "Settings")
    }
}

// Was rendering item.take(1) -- the first letter of the label -- as the
// "icon" in every grid tile across the app (Mini/Games/Bank/Pick all
// showed as plain letters M/G/B/P). Real icons per item now; this is the
// single biggest reason the app read as a wireframe rather than Toss.
@Composable
private fun IconGridSection(title: String, items: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = TossSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val chunked = items.chunked(4)
        chunked.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, icon) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = TossText)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(label, color = TossSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewItundaAppScreen() {
    ItundaAppScreen()
}
