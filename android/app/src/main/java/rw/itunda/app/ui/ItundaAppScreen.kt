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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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

private enum class TossTab(val label: String, val glyph: String) {
    Home("Home", "H"),
    Benefits("Benefits", "B"),
    Shop("Shop", "S"),
    Pay("Pay", "P"),
    All("All", "A")
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

@Composable
private fun TossBottomBar(selectedTab: TossTab, onSelect: (TossTab) -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF17181D))
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.glyph,
                            color = if (selected) TossText else TossSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) TossText else TossSecondary
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
                    Triple("RWF 463,022", "Spent in July", "3 new"),
                    Triple("Transfer cashback", "BK account -> TUYIZERE Eric", "Claim"),
                    Triple("Sprinkle money to friends", "19:03:55 left", "Send")
                )
            )
        }
        item {
            ShellSection(
                title = "",
                rows = listOf(
                    Triple("Get cashback every time you pay", "", ">"),
                    Triple("Pay with face ID", "", ">"),
                    Triple("Receive government alerts", "", ">")
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(88.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF181920))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TopGlyph("Pay")
            TopGlyph("N")
        }
    }
}

@Composable
private fun TopGlyph(label: String) {
    Text(label, color = TossText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
            Text(amount.take(1), color = TossText, fontWeight = FontWeight.Bold)
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

@Composable
private fun ShellSection(title: String, rows: List<Triple<String, String, String>>) {
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
                        Text(row.third, color = TossText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.first, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TossText)
                        if (row.second.isNotEmpty()) {
                            Text(row.second, fontSize = 14.sp, color = TossSecondary)
                        }
                    }
                    if (row.third.length > 1) {
                        SmallBlueButton(row.third)
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
                        Triple(item.title, item.subtitle, item.badge ?: ">")
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
            Triple("Points and pay money", "Total RWF 31,031", " "),
            Triple("Received coupons", "", " ")
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
        item { IconGridSection("Quick access", listOf("Mini", "Games", "Bank", "Pick")) }
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
        item { IconGridSection("Recent services", listOf("Open acct", "Photo transfer", "Verify", "Send", "Group", "Property", "Insurance", "More")) }
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
 */
@Composable
private fun MiniAppsSection(
    onWalletBalance: () -> Unit,
    onPayBills: () -> Unit,
    onRewardTasks: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Mini apps", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TossText)
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                Triple("Wallet balance", onWalletBalance, "W"),
                Triple("Pay bills", onPayBills, "P"),
                Triple("Reward tasks", onRewardTasks, "R")
            ).forEachIndexed { index, (title, onClick, glyph) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
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
                        Text(glyph, color = TossText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TossText)
                }
                if (index != 2) Divider(color = TossLine)
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
            listOf("Happy lottery", "Push the button", "Try on", "Bring friends").forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                        Text(it.take(1), color = TossText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(it, modifier = Modifier.weight(1f), color = TossText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("₩", color = Color.White, fontWeight = FontWeight.Bold)
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
        Text("U", color = TossText, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text("B", color = TossText, fontSize = 22.sp)
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
                listOf("Check-in", "Scroll", "Feed", "Cat", "Pick").forEach {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                            Text(it.take(1), color = TossText, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = TossSecondary, fontSize = 12.sp)
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
            Text("QR", color = TossText, fontWeight = FontWeight.Bold)
            Text("G", color = TossText, fontWeight = FontWeight.Bold)
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
                    Text("P", color = TossBlue, fontWeight = FontWeight.Bold)
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
                    Text("W", color = TossText, fontWeight = FontWeight.Bold)
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

@Composable
private fun IconGridSection(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = TossText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val chunked = items.chunked(4)
        chunked.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                            Text(item.take(1), color = TossText, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(item, color = TossSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListSection(title: String, items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, color = TossText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TossCard)) {
            Column(modifier = Modifier.padding(20.dp)) {
                items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(TossChip), contentAlignment = Alignment.Center) {
                            Text(item.first.take(1), color = TossText, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(item.first, color = TossText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.second, color = TossSecondary, fontSize = 14.sp)
                        }
                    }
                    if (index != items.lastIndex) Divider(color = TossLine)
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
