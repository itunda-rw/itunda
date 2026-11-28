package rw.itunda.feature.menu.impl

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** One row of a mini-app list: emoji tile, name, one-line description. */
private data class MiniApp(val emoji: String, val name: String, val line: String, val tags: List<String> = emptyList())

private val TRENDING = listOf(
    MiniApp("📈", "Practice Investing", "Find out if you’d make money in stocks"),
    MiniApp("🏷️", "Deal of the Day", "Only at this price, only today"),
    MiniApp("🚌", "Bus Times", "Live Kigali bus arrivals"),
    MiniApp("📱", "Airtime Top-up", "Buy MTN or Airtel airtime in seconds"),
    MiniApp("🛡️", "Safe Savings", "Small daily saves that add up"),
)
private val GAMES = listOf(
    MiniApp("🧩", "Match Puzzle", "Cute animal puzzle, play together", listOf("puz", "hot")),
    MiniApp("🃏", "Card Classic", "Solo card game, no waiting", listOf("cls")),
    MiniApp("🍉", "Fruit Merge", "Merge fruit, reach the watermelon", listOf("puz", "hot")),
    MiniApp("🌸", "Daily Garden", "Grow real-looking flowers", listOf("new")),
    MiniApp("🔤", "Word Streak", "A new word every day", listOf("new", "str")),
)
private val GAME_FILTERS = listOf("all" to ("▦" to "All"), "hot" to ("🔥" to "Trending"), "new" to ("✨" to "New"), "puz" to ("🧩" to "Puzzle"), "cls" to ("♟️" to "Classic"), "str" to ("🧠" to "Strategy"))
private val MISSIONS = listOf(Triple(MiniApp("🧩", "Match Puzzle", "Play 1 minute"), "+2 RWF", 0), Triple(MiniApp("📈", "Practice Investing", "Finish a round"), "+3 RWF", 0), Triple(MiniApp("🏷️", "Deal Finder", "View a deal"), "+1 RWF", 0))
private val CATEGORIES = linkedMapOf(
    "Games" to listOf(MiniApp("🎲", "Ludo Rwanda", "Play with friends"), MiniApp("🧩", "Word Puzzle", "Daily puzzle"), MiniApp("🏎️", "Kigali Racer", "Endless driving")),
    "Shopping" to listOf(MiniApp("🛍️", "Market Deals", "Local offers"), MiniApp("🧺", "Grocery List", "Plan and share"), MiniApp("📦", "Parcel Track", "Follow deliveries")),
    "Money" to listOf(MiniApp("🧮", "Budget Buddy", "Track monthly spend"), MiniApp("💱", "Rate Watch", "Currency alerts"), MiniApp("🧾", "Receipt Box", "Keep your receipts")),
    "Travel" to listOf(MiniApp("🚌", "Bus Times", "Live arrivals"), MiniApp("🗺️", "Trip Planner", "Plan a weekend"), MiniApp("🏨", "Stay Finder", "Guesthouses in Rwanda")),
    "Learning" to listOf(MiniApp("🔤", "Kinyarwanda 101", "Learn basics"), MiniApp("🧠", "Quiz Night", "Test your knowledge"), MiniApp("📚", "Library", "Free e-books")),
    "Lifestyle" to listOf(MiniApp("🍲", "Recipes", "Rwandan classics"), MiniApp("💪", "Fit 30", "30-day workouts"), MiniApp("🎵", "Radio", "Local stations")),
    "Utilities" to listOf(MiniApp("🔦", "Flashlight", "Quick light"), MiniApp("📏", "Converter", "Units and sizes"), MiniApp("⏰", "Reminders", "Never forget")),
)

/** Prototype `mini`: Featured / Games / Points / Categories hub, plus the category pages and the profile. */
@Composable
fun MiniAppsHubScreen(
    onBack: () -> Unit,
    onLaunchPartnerMiniApp: suspend (activity: android.app.Activity, app: rw.itunda.core.network.PartnerMiniAppDto, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var gameFilter by rememberSaveable { mutableStateOf("all") }
    var points by rememberSaveable { mutableStateOf(0) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var profile by rememberSaveable { mutableStateOf(false) }
    var airtime by rememberSaveable { mutableStateOf(false) }
    val added = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val soon: (String) -> Unit = { Toast.makeText(context, "$it is coming soon", Toast.LENGTH_SHORT).show() }

    BackHandler { if (airtime) airtime = false else if (profile) profile = false else if (category != null) category = null else onBack() }
    if (airtime) { AirtimeScreen { airtime = false }; return }
    if (profile) { MiniProfile { profile = false }; return }
    category?.let { c -> MiniCategory(c, added, { category = null }); return }

    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) { ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary) }
            Box(Modifier.size(48.dp).pressScaleClickable { profile = true }, contentAlignment = Alignment.Center) { ItundaArt("user", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textTertiary) }
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
            listOf("Featured", "Games", "Points", "Categories").forEachIndexed { i, l ->
                Box(Modifier.weight(1f).height(46.dp).pressScaleClickable { tab = i }, contentAlignment = Alignment.Center) {
                    Text(l, color = if (tab == i) Ids.colors.textPrimary else Ids.colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.18).sp, fontWeight = FontWeight.Bold)
                    if (tab == i) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.72f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ids.colors.textPrimary))
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> { MiniHeading("Trending now"); TRENDING.forEachIndexed { i, a -> MiniAppRow(i + 1, a) { if (a.name == "Airtime Top-up") airtime = true else soon(a.name) } } }
                1 -> GamesTab(gameFilter, { gameFilter = it }, points, { points = minOf(10, points + 2); Toast.makeText(context, "+2 RWF points", Toast.LENGTH_SHORT).show() }, soon)
                2 -> PointsTab(points)
                else -> CategoriesTab { category = it }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun MiniHeading(title: String, top: Int = 26) {
    Text(title, color = Ids.colors.textPrimary, fontSize = 19.sp, lineHeight = 26.6.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = top.dp, bottom = 12.dp))
}

@Composable
private fun EmojiTile(emoji: String, box: Int, font: Int, radius: Int = 16) {
    Box(Modifier.size(box.dp).clip(RoundedCornerShape(radius.dp)).background(Ids.colors.chip), contentAlignment = Alignment.Center) { Text(emoji, fontSize = font.sp) }
}

@Composable
private fun MiniAppRow(rank: Int, a: MiniApp, onStart: () -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("$rank", color = Ids.colors.textSecondary, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
        EmojiTile(a.emoji, 56, 28)
        Column(Modifier.weight(1f)) {
            Text(a.name, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold)
            Text(a.line, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.brand.copy(alpha = 0.12f)).pressScaleClickable(onClick = onStart).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text("Start", color = Ids.colors.textBrand, fontSize = 14.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GamesTab(filter: String, onFilter: (String) -> Unit, points: Int, onPlay: () -> Unit, soon: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GAME_FILTERS.forEach { (id, il) ->
            val on = filter == id
            Column(Modifier.width(68.dp).pressScaleClickable { onFilter(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Ids.colors.chip).then(if (on) Modifier.border(2.dp, Ids.colors.textBrand, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    Text(il.first, color = if (on) Ids.colors.textBrand else Ids.colors.textTertiary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(il.second, color = if (on) Ids.colors.textBrand else Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    MiniHeading("Point missions", top = 22)
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(Modifier.height(68.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiTile("🧩", 64, 32)
            Column(Modifier.weight(1f)) {
                Text("Match Puzzle", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
                Text("Play 1 minute, get 2 RWF", color = Ids.colors.textPrimary, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.height(58.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF8F89FF).copy(alpha = if (points >= 10) 0.4f else 1f))
                    .then(if (points < 10) Modifier.pressScaleClickable(onClick = onPlay) else Modifier).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Play", color = Color(0xFF17171C), fontSize = 18.sp, letterSpacing = (-0.36).sp, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.padding(top = 12.dp, bottom = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ids.colors.chip)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(points / 10f).background(Ids.colors.brand))
        }
        Text(androidx.compose.ui.text.buildAnnotatedString {
            withStyle(androidx.compose.ui.text.SpanStyle(color = Ids.colors.textBrand)) { append("$points RWF") }
            append(" / 10 RWF")
        }, color = Ids.colors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
    MiniHeading("Trending games")
    val shown = GAMES.filter { filter == "all" || filter in it.tags }
    if (shown.isEmpty()) Text("Nothing here yet.", color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp))
    shown.forEachIndexed { i, a -> MiniAppRow(i + 1, a) { soon(a.name) } }
}

@Composable
private fun PointsTab(points: Int) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
        Text("Your points", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp)
        Text("$points RWF", color = Ids.colors.textPrimary, fontSize = 34.sp, lineHeight = 47.6.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text("Earn by playing missions. Points can be moved to your itunda Pay balance.", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
    }
    MiniHeading("Missions")
    MISSIONS.forEach { (a, reward, _) ->
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiTile(a.emoji, 56, 28)
            Column(Modifier.weight(1f)) {
                Text(a.name, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold)
                Text(a.line, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp)
            }
            Text(reward, color = Ids.colors.textBrand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesTab(onPick: (String) -> Unit) {
    FlowRow(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CATEGORIES.keys.forEach { c ->
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.chip).pressScaleClickable { onPick(c) }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(c, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Prototype `minicat`: 72dp rows with an Add / Added pill. */
@Composable
private fun MiniCategory(name: String, added: MutableMap<String, Boolean>, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar(name, onBack)
        Spacer(Modifier.height(8.dp))
        (CATEGORIES[name] ?: emptyList()).forEach { a ->
            val on = added[a.name] == true
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EmojiTile(a.emoji, 48, 24, 14)
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold)
                    Text(a.line, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) Ids.colors.chip else Ids.colors.brand)
                        .pressScaleClickable { added[a.name] = !on; Toast.makeText(context, if (on) "${a.name} removed" else "${a.name} added", Toast.LENGTH_SHORT).show() }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (on) "Added" else "Add", color = if (on) Ids.colors.textPrimary else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Prototype `miniprof`: avatar and player name (its Edit / Connect friends / Purchase history controls are not wired up yet, so they are left out). */
@Composable
private fun MiniProfile(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar("", onBack)
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Text("🐼", fontSize = 24.sp) }
            Column {
                Text("My profile", color = Ids.colors.textTertiary, fontSize = 14.sp)
                Text("Player84825", color = Ids.colors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
