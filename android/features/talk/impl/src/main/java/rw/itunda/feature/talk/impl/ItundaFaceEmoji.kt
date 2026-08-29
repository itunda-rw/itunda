package rw.itunda.feature.talk.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

// itundaface emoji-input infrastructure -- Android port of the real new
// capability shipped to bank-mfe same session (icons/ItundaFaceEmoji.tsx), part
// of the "reach TossFace's 3,600-glyph scale" initiative. See that file's own
// doc comment for the full rationale; ported here rather than re-derived --
// itunda had zero free-text emoji input on Android either before this (Talk's
// only prior "emoji" concept was the 5 fixed QUICK_REACTIONS, same as web).
//
// Real ordered key list per category (not a Map<String, @Composable ...> --
// storing Composable lambdas as map values hit a real Kotlin/Compose-compiler
// type-inference dead end in this project's toolchain, "ERROR CLASS: Unknown
// return lambda parameter type", and no existing file in this codebase uses
// that pattern either; a plain `when` dispatch is both the working fix and
// the more idiomatic Compose approach). Add a category's glyphs by: (1)
// adding a new `...Keys` list below, (2) adding it to ITUNDAFACE_EMOJI_ALL_KEYS
// and ITUNDAFACE_EMOJI_CATEGORIES, (3) adding `when` branches in
// ItundaFaceEmojiGlyph -- same real category-array shape as the web picker.
private val smileysKeys = listOf("👍", "❤️", "😂", "😮", "😢", "😀", "😄", "🙂", "😉", "😍", "😘", "😴", "😭", "😡", "😎")
private val peopleKeys = listOf("👀", "✊", "👋", "✌️", "👌", "💪", "🙏", "👏", "🤝")
private val natureKeys = listOf("🐶", "🐱", "⭐", "🌟", "🌈", "🌸", "🐦")
private val foodKeys = listOf("🍕", "🍔", "☕", "🍰", "🍩", "🍓", "🍉", "🍎")
private val travelKeys = listOf("🚗", "✈️", "🏠", "🚀", "🚲", "🌍")
private val activitiesKeys = listOf("⚽", "🏀", "🎮", "🎨", "🎵", "🎉", "🏆", "🎯")
private val objectsKeys = listOf("💳", "📱", "⌚", "🔑", "💡", "🎧", "📎", "🖊️")

val ITUNDAFACE_EMOJI_ALL_KEYS: List<String> = smileysKeys + peopleKeys + natureKeys + foodKeys + travelKeys + activitiesKeys + objectsKeys
val ITUNDAFACE_EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "Smileys & Emotion" to smileysKeys,
    "People & Body" to peopleKeys,
    "Animals & Nature" to natureKeys,
    "Food & Drink" to foodKeys,
    "Travel & Places" to travelKeys,
    "Activities" to activitiesKeys,
    "Objects" to objectsKeys,
)

@Composable
fun ItundaFaceEmojiGlyph(emoji: String, size: Dp) {
    when (emoji) {
        "👍" -> ReactionThumbsUp(size)
        "❤️" -> ReactionHeart(size)
        "😂" -> ReactionLaughing(size)
        "😮" -> ReactionWow(size)
        "😢" -> ReactionSad(size)
        "😀" -> SmileyGrinning(size)
        "😄" -> SmileyGrinningEyes(size)
        "🙂" -> SmileySlight(size)
        "😉" -> SmileyWink(size)
        "😍" -> SmileyHeartEyes(size)
        "😘" -> SmileyKissHeart(size)
        "😴" -> SmileySleeping(size)
        "😭" -> SmileyLoudlyCrying(size)
        "😡" -> SmileyAngry(size)
        "😎" -> SmileyCool(size)
        "👀" -> PeopleEyes(size)
        "✊" -> PeopleFist(size)
        "👋" -> PeopleWavingHand(size)
        "✌️" -> PeopleVictoryHand(size)
        "👌" -> PeopleOkHand(size)
        "💪" -> PeopleMuscle(size)
        "🙏" -> PeoplePray(size)
        "👏" -> PeopleClappingHands(size)
        "🤝" -> HandshakeGlyph(size)
        "🐶" -> NatureDog(size)
        "🐱" -> NatureCat(size)
        "⭐" -> NatureStar(size)
        "🌟" -> NatureGlowingStar(size)
        "🌈" -> NatureRainbow(size)
        "🌸" -> NatureCherryBlossom(size)
        "🐦" -> NatureBird(size)
        "🍕" -> FoodPizza(size)
        "🍔" -> FoodHamburger(size)
        "☕" -> FoodCoffee(size)
        "🍰" -> FoodCake(size)
        "🍩" -> FoodDonut(size)
        "🍓" -> FoodStrawberry(size)
        "🍉" -> FoodWatermelon(size)
        "🍎" -> FoodApple(size)
        "🚗" -> TravelCar(size)
        "✈️" -> TravelAirplane(size)
        "🏠" -> TravelHouse(size)
        "🚀" -> TravelRocket(size)
        "🚲" -> TravelBike(size)
        "🌍" -> TravelGlobe(size)
        "⚽" -> ActivitySoccer(size)
        "🏀" -> ActivityBasketball(size)
        "🎮" -> ActivityVideoGame(size)
        "🎨" -> ActivityPalette(size)
        "🎵" -> ActivityMusicalNote(size)
        "🎉" -> ActivityPartyPopper(size)
        "🏆" -> ActivityTrophy(size)
        "🎯" -> ActivityDirectHit(size)
        "💳" -> ObjectCreditCard(size)
        "📱" -> ObjectMobilePhone(size)
        "⌚" -> ObjectWatch(size)
        "🔑" -> ObjectKey(size)
        "💡" -> ObjectLightBulb(size)
        "🎧" -> ObjectHeadphones(size)
        "📎" -> ObjectPaperclip(size)
        "🖊️" -> ObjectPen(size)
    }
}

// Built from the registry's own real keys, longest-first (so a future multi-
// codepoint entry, e.g. a ZWJ sequence, isn't shadowed by a shorter prefix
// match) rather than a broad Unicode-property class -- Android's
// java.util.regex Unicode-property support (`\p{Is...}`) is inconsistent
// across API levels/vendors, so matching only what's actually registered is
// both simpler and safer than web's Extended_Pictographic approach, and
// degrades identically: anything not in the registry is never matched, so it
// flows through as plain text untouched.
private val EMOJI_REGEX = Regex(ITUNDAFACE_EMOJI_ALL_KEYS.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) })

/** Renders a message body with any registered emoji swapped for itundaface's own
 * glyph inline (via Compose's real `appendInlineContent`/`Placeholder` mechanism,
 * the standard way to embed a Composable inside flowing text), leaving
 * unregistered emoji as plain text. `body` itself is never mutated -- same
 * display-layer-only discipline as every itundaface batch. Falls back to a plain
 * `Text` (no AnnotatedString/inlineContent overhead) when nothing matches, the
 * common case for most messages. */
@Composable
fun MessageBodyWithEmoji(body: String, color: Color, fontSize: TextUnit = 14.sp, maxLines: Int = Int.MAX_VALUE) {
    val matches = remember(body) { EMOJI_REGEX.findAll(body).toList() }
    if (matches.isEmpty()) {
        Text(body, color = color, fontSize = fontSize, maxLines = maxLines)
        return
    }
    val density = LocalDensity.current
    val glyphSizeSp = (fontSize.value * 1.15f).sp
    val glyphSizeDp = with(density) { glyphSizeSp.toDp() }
    val inlineContentMap = remember(body) {
        buildMap<String, InlineTextContent> {
            matches.forEachIndexed { i, m ->
                put(
                    "itdf$i",
                    InlineTextContent(Placeholder(glyphSizeSp, glyphSizeSp, PlaceholderVerticalAlign.TextCenter)) {
                        ItundaFaceEmojiGlyph(m.value, glyphSizeDp)
                    },
                )
            }
        }
    }
    val annotated = remember(body) {
        buildAnnotatedString {
            var lastIndex = 0
            matches.forEachIndexed { i, m ->
                if (m.range.first > lastIndex) append(body.substring(lastIndex, m.range.first))
                appendInlineContent("itdf$i", "?")
                lastIndex = m.range.last + 1
            }
            if (lastIndex < body.length) append(body.substring(lastIndex))
        }
    }
    Text(annotated, color = color, fontSize = fontSize, maxLines = maxLines, inlineContent = inlineContentMap)
}

/** Real chat-composer emoji picker -- distinct from EmoticonPickerPanel above
 * (a KakaoTalk-style sticker/image picker, backed by real owned-pack API calls):
 * this is Unicode text emoji, picking one calls `onPick` with the real character
 * to insert into the draft, no network call involved. Renders each real category
 * as its own header row (`GridItemSpan(maxLineSpan)`) inside one scrollable grid,
 * ready for more categories to append without restructuring, same as the web
 * picker's own category-array shape. */
@Composable
fun ItundaFaceEmojiPicker(onPick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(12.dp),
    ) {
        LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(220.dp)) {
            ITUNDAFACE_EMOJI_CATEGORIES.forEach { (name, keys) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(name, color = Ids.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                }
                gridItems(keys, key = { it }) { emoji ->
                    Box(
                        modifier = Modifier
                            .pressScaleClickable { onPick(emoji) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ItundaFaceEmojiGlyph(emoji, 28.dp)
                    }
                }
            }
        }
    }
}
