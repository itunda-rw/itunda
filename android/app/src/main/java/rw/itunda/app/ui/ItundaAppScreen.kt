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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TouchApp
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

private enum class TossTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Benefits("Benefits", Icons.Outlined.CardGiftcard),
    Shop("Shop", Icons.Outlined.ShoppingBag),
    Pay("Pay", Icons.Outlined.QrCodeScanner),
    All("All", Icons.Outlined.Apps)
}

@Composable
fun ItundaAppScreen(viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    TdsTheme {
        var selectedTab by remember { mutableStateOf(TossTab.Home) }

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
                    TossTab.Home -> HomeTab(viewModel)
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
private fun HomeTab(viewModel: MainViewModel) {
    val primaryWallet by viewModel.primaryWallet.collectAsState()
    val balanceText = primaryWallet?.let { "${it.currency} %,.0f".format(it.balance) } ?: "RWF 0"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HomeTopBar() }
        item { WalletHeroCard(balanceText) }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    ShellRow("RWF 463,022", "Spent in July", "3 new", Icons.Outlined.PieChart),
                    ShellRow("Transfer cashback", "BK account -> TUYIZERE Eric", "Claim", Icons.Outlined.Payments),
                    ShellRow("Sprinkle money to friends", "19:03:55 left", "Send", Icons.Outlined.Redeem)
                )
            )
        }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    ShellRow("Get cashback every time you pay", "", ">", Icons.Outlined.Payments),
                    ShellRow("Pay with face ID", "", ">", Icons.Outlined.Face),
                    ShellRow("Receive government alerts", "", ">", Icons.Outlined.Campaign)
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
        TopIconButton(Icons.Outlined.QrCodeScanner)
        TopIconButton(Icons.Outlined.Notifications)
    }
}

@Composable
private fun TopIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(TossCardSoft),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = TossText)
    }
}

@Composable
private fun WalletHeroCard(balanceText: String) {
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
                PrimaryAction("Add money", Modifier.weight(1f), false)
                PrimaryAction("Send", Modifier.weight(1f), true)
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
private fun PrimaryAction(title: String, modifier: Modifier = Modifier, filled: Boolean) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) TossBlue else Color(0xFF1F3053))
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
    val icon: androidx.compose.ui.graphics.vector.ImageVector
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
                            .background(TossChip),
                        contentAlignment = Alignment.Center
                    ) {
                        // Was showing row.third (the action label, e.g. "3 new"
                        // or "Claim") crammed into a 42dp icon box -- a real bug,
                        // not a placeholder; it also rendered a second time below
                        // via SmallBlueButton whenever longer than one character.
                        // Then briefly row.first's initial as a stopgap; a real
                        // per-row icon now, for consistency with every other
                        // section on screen.
                        Icon(row.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = TossText)
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ShopTopBar() }
        item { CategoryTabsRow(listOf("Home", "Categories", "Cycling", "Deals", "Summer food")) }
        item { ShopPromoCard() }
        
        if (discoverItems.isNotEmpty()) {
            item {
                ShellSection(
                    title = "Discover",
                    rows = discoverItems.take(3).map { item ->
                        ShellRow(item.title, item.subtitle, item.badge ?: ">", Icons.Outlined.Storefront)
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { PayTopBar() }
        item { MapPlaceholder() }
        item { PayFeatureCard() }
        item { ShellSection("", listOf(
            ShellRow("Points and pay money", "Total RWF 31,031", " ", Icons.Outlined.Payments),
            ShellRow("Received coupons", "", " ", Icons.Outlined.LocalOffer)
        )) }
    }
}

@Composable
private fun AllTab() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
        item { ListSection("Financial services", listOf(
            "Open account" to "Toss Bank, other banks, securities",
            "My assets" to "Accounts, loans, securities, cards, points",
            "Get a loan" to "Credit, mortgage, overdraft, microloan",
            "Mobile plan" to "MTN, Airtel, broadband"
        )) }
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
    val onClick: () -> Unit = {}
)

/**
 * The real Toss list pattern: a small secondary-color section label, then
 * plain rows with a hairline divider between them -- no card, no icon chip,
 * no background fill. Two real row shapes coexist in the reference
 * screenshots and both are supported here: title + a stacked description
 * below it ("서류 발급 / 통장 사본・송금확인증 등"), or title + a right-aligned
 * value, sometimes in the brand blue as a link ("신용대출 갈아타기 ... 연
 * 5.14%~15.00%"). This is what 서비스/신용카드/체크카드/갈아타기 etc. actually
 * look like, as opposed to a boxed "settings card."
 */
@Composable
private fun FlatSection(title: String, rows: List<FlatRow>) {
    Column {
        Text(
            title,
            color = TossSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = row.onClick)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, color = TossText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    if (row.subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(row.subtitle, color = TossTertiary, fontSize = 13.sp)
                    }
                }
                if (row.trailing != null) {
                    Text(
                        row.trailing,
                        color = if (row.trailingIsLink) TossBlue else TossSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (row.trailingIsLink) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            if (index != rows.lastIndex) Divider(color = TossLine, thickness = 0.5.dp)
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF5D2FE6))) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(20.dp)) {
            Column {
                Text("Limited gift for Rwanda", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TossCard)) {
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TossCard)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("3 chances to get money back", color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
        Icon(Icons.Outlined.Person, contentDescription = null, tint = TossText)
        Spacer(modifier = Modifier.width(12.dp))
        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = TossText)
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDDEFFC))) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("10,000 RWF early-bird", color = Color(0xFFE25A61), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Calcium + Magnesium\n90 tablets 3,900 RWF", color = Color(0xFF151515), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PointActionsCard() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TossBackground)) {
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
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = TossText)
            Icon(Icons.Outlined.Public, contentDescription = null, tint = TossText)
        }
    }
}

@Composable
private fun MapPlaceholder() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFE4D7))) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.BottomCenter) {
            Box(modifier = Modifier.padding(bottom = 18.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF202228)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("5 nearby stores", color = TossText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PayFeatureCard() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TossCard)) {
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

@Composable
private fun AllTopBar() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("TUYIZERE ERIC", color = TossText, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ID", color = TossSecondary)
            Text("|", color = TossTertiary)
            Text("Support", color = TossSecondary)
            Text("|", color = TossTertiary)
            Text("Settings", color = TossSecondary)
        }
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

@Composable
private fun ListSection(title: String, items: List<Pair<String, String>>) {
    FlatSection(
        title = title,
        rows = items.map { (name, description) -> FlatRow(title = name, subtitle = description) }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewItundaAppScreen() {
    ItundaAppScreen()
}
