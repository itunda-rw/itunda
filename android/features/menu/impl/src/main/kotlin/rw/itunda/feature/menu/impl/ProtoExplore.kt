package rw.itunda.feature.menu.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsIcons
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** One tappable row of the Explore catalogue. [key] is what the host maps to a destination. */
internal data class ExploreRow(val key: String, val title: String, val subtitle: String, val emoji: String)
internal data class ExploreSection(val title: String, val rows: List<ExploreRow>)

// The prototype's own catalogue (v1038), in its order.
internal val exploreSections = listOf(
    ExploreSection("Everyday", listOf(ExploreRow("shop", "Shop", "Products · Deals", "🛍️"), ExploreRow("eats", "Eats", "Food · Delivery", "🍴"), ExploreRow("front", "Front", "Eat here · To go", "🧾"))),
    ExploreSection("Your neighbourhood", listOf(ExploreRow("marketplace", "Marketplace", "Buy · Sell", "🏪"), ExploreRow("community", "Community", "Posts · Q&A", "💬"), ExploreRow("jobs", "Jobs", "Gigs · Part-time", "💼"), ExploreRow("property", "Property", "Rent · Buy", "🏠"), ExploreRow("places", "Places", "Discover · Reserve · Pay", "🏬"))),
    ExploreSection("Money tools", listOf(ExploreRow("benefits", "Benefits", "Points · Coupons", "🎁"), ExploreRow("invest", "Invest", "RSE · Stocks", "📊"), ExploreRow("week26", "26-Week Savings", "Auto-save", "💵"), ExploreRow("day31", "31-Day Savings", "Daily streak", "💵"))),
    ExploreSection("Accounts & cards", listOf(ExploreRow("open", "Open account", "itunda · Other banks", "🏦"), ExploreRow("assets", "My assets", "Accounts · Loans · Cards", "👜"), ExploreRow("card", "Card", "Limits · Freeze", "💳"), ExploreRow("transit", "Transit", "Bus fare", "🚌"), ExploreRow("spending", "Spending", "By category", "📈"), ExploreRow("group", "Group account", "Dues · Split", "👥"), ExploreRow("family", "Family", "Guardian · Child", "👨‍👩‍👧"), ExploreRow("fx", "Foreign currency", "USD · EUR · GBP", "🌍"), ExploreRow("subs", "Subscriptions", "Recurring", "🕒"), ExploreRow("cert", "Digital certificate", "Sign", "✅"))),
    ExploreSection("Send & pay", listOf(ExploreRow("transfer", "Transfer", "Auto · Split", "📨"), ExploreRow("request", "Request money", "Payment code", "🧾"), ExploreRow("topup", "Auto top-up", "Refill", "🔄"), ExploreRow("mobile", "Mobile plan", "MTN · Airtel", "📱"))),
    ExploreSection("Save & grow", listOf(ExploreRow("deposit", "12-month deposit", "Interest upfront", "🏧"), ExploreRow("youth", "Youth account", "Ages 7-18", "🧒"), ExploreRow("ikimina", "Ikimina", "Group savings", "🤝"), ExploreRow("sacco", "SACCO shares", "Dividends", "🌱"))),
    ExploreSection("Borrow", listOf(ExploreRow("loan", "Get a loan", "Personal · SME", "💰"), ExploreRow("credit", "Credit score", "Free check", "📉"), ExploreRow("harvest", "Harvest advance", "Coffee co-op", "☕"), ExploreRow("vup", "VUP Financial Services", "Microloan", "🌾"), ExploreRow("student", "Student loan", "BRD", "🎓"), ExploreRow("moto", "Moto-Taxi Ownership", "Down payment", "🛵"))),
    ExploreSection("Transport", listOf(ExploreRow("rides", "Rides", "Ride · Drive", "🚗"), ExploreRow("bike", "Bike rental", "Bikes · Scooters", "🚲"), ExploreRow("bus", "Bus", "Intercity seats", "🚌"), ExploreRow("directions", "Directions", "Bus · Car · Walk · Bike", "🧭"))),
    ExploreSection("Support", listOf(ExploreRow("chat", "Live chat", "Call support", "💬"), ExploreRow("report", "Report an issue with a transaction", "", "⚠️"), ExploreRow("news", "Announcements", "", "📢"), ExploreRow("ussd", "USSD access", "Any phone", "☎️")))
)

private val quickTiles = listOf(Triple("bank", "🏛️", "Bank"), Triple("apps", "👥", "Mini apps"), Triple("games", "🎉", "Games"), Triple("verify", "✅", "Verify"))

/** Explore tab in the prototype's layout: bar, search, quick tiles, offers, white section cards. */
@Composable
fun ProtoExploreContent(
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    val q = query.trim()
    val sections = if (q.isEmpty()) exploreSections else listOf(
        ExploreSection("Results", exploreSections.flatMap { it.rows }.filter { it.title.contains(q, true) || it.subtitle.contains(q, true) }),
    )
    LazyColumn(
        Modifier.fillMaxSize().background(Ids.colors.surfaceSoft),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 15.dp, end = 15.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(Modifier.padding(top = 16.dp)) {
                AllTopBar(onOpenAuthentication = { onOpen("verify") }, onOpenHelp = onHelp, onOpenSettings = onSettings, onOpenMiniApps = { onOpen("apps") })
            }
        }
        item { MenuSearchBar(query = query, onQueryChange = onQuery, placeholder = "Search") }
        if (q.isEmpty()) {
            item { QuickTiles(onOpen) }
            item { ExploreOffersCarousel() }
        }
        items(sections) { section -> SectionCard(section, onOpen) }
    }
}

@Composable
private fun QuickTiles(onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.surface).padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        quickTiles.forEach { (key, emoji, label) ->
            Column(Modifier.width(80.dp).pressScaleClickable { onOpen(key) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 24.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(label, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SectionCard(section: ExploreSection, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.surface).padding(vertical = 12.dp)) {
        Text(
            section.title, color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        )
        section.rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(64.dp).pressScaleClickable { onOpen(row.key) }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                    Text(row.emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (row.subtitle.isNotEmpty()) Text(row.subtitle, color = Ids.colors.textTertiary, fontSize = 14.sp)
                }
                Icon(IdsIcons.ChevronRight, null, Modifier.size(20.dp), tint = Ids.colors.textTertiary.copy(alpha = 0.7f))
            }
        }
    }
}
