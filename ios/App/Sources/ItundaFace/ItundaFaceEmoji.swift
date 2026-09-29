//
//  ItundaFaceEmoji.swift
//  itundaface emoji-input infrastructure -- iOS port of the real new capability
//  shipped to bank-mfe/Android same session (icons/ItundaFaceEmoji.tsx /
//  ItundaFaceEmoji.kt), part of the "reach TossFace's 3,600-glyph scale"
//  initiative. See those files' own doc comments for the full rationale;
//  itunda had zero free-text emoji input on iOS either before this (Talk's
//  only prior "emoji" concept was the 5 fixed quickReactions, same as the
//  other two platforms).
//
//  Real iOS-specific constraint neither web nor Android hit: this app's
//  deployment target is iOS 15 (ios/build/generated/ios/Package.swift), which
//  has no native way to embed an arbitrary custom SwiftUI View inline inside a
//  wrapping Text run (Compose's appendInlineContent/Placeholder and the DOM's
//  own inline-element flow both have no iOS-15-era equivalent; the `Layout`
//  protocol needed for a hand-rolled flow layout is iOS 16+). The real,
//  standard iOS-15-compatible technique instead: rasterize each glyph View to
//  a UIImage once (via UIHostingController + UIGraphicsImageRenderer, a
//  well-established pre-iOS-16 pattern), cache it, and embed it as an
//  NSTextAttachment inside an NSAttributedString bridged to SwiftUI via
//  `Text(AttributedString(...))` (AttributedString(NSAttributedString:) IS
//  available iOS 15+) -- NSTextAttachment images wrap correctly within a
//  single Text run, which is actually a closer match to how a REAL emoji font
//  substitution works than web/Android's own inline-content approaches.
//

import SwiftUI
import CoreDesignSystem

private let smileysKeys: [String] = ["👍", "❤️", "😂", "😮", "😢", "😀", "😄", "🙂", "😉", "😍", "😘", "😴", "😭", "😡", "😎"]
private let peopleKeys: [String] = ["👀", "✊", "👋", "✌️", "👌", "💪", "🙏", "👏", "🤝"]
private let natureKeys: [String] = ["🐶", "🐱", "⭐", "🌟", "🌈", "🌸", "🐦"]
private let foodKeys: [String] = ["🍕", "🍔", "☕", "🍰", "🍩", "🍓", "🍉", "🍎"]
private let travelKeys: [String] = ["🚗", "✈️", "🏠", "🚀", "🚲", "🌍"]
private let activitiesKeys: [String] = ["⚽", "🏀", "🎮", "🎨", "🎵", "🎉", "🏆", "🎯"]
private let objectsKeys: [String] = ["💳", "📱", "⌚", "🔑", "💡", "🎧", "📎", "🖊️"]
private let symbolsKeys: [String] = ["✅", "❌", "❗", "❓", "💯", "⚠️", "🚫", "♻️"]
private let flagsKeys: [String] = ["🇷🇼", "🇰🇪", "🇺🇬", "🇹🇿", "🇧🇮", "🇨🇩", "🇺🇸", "🇬🇧", "🇪🇺", "🇸🇸", "🇸🇴", "🇨🇳", "🇮🇳", "🇧🇪", "🇩🇪", "🇫🇷", "🇿🇦", "🇳🇬", "🇪🇹", "🇰🇷", "🇯🇵", "🇦🇪", "🇨🇭", "🇳🇱", "🇬🇭", "🇲🇦", "🇪🇬", "🇿🇲", "🇲🇿", "🇸🇪", "🇨🇦", "🇦🇺", "🇸🇳", "🇨🇮", "🇨🇲", "🇦🇴", "🇿🇼", "🇧🇼", "🇲🇼"]

let itundaFaceEmojiKeys: [String] = smileysKeys + peopleKeys + natureKeys + foodKeys + travelKeys + activitiesKeys + objectsKeys + symbolsKeys + flagsKeys
let itundaFaceEmojiCategories: [(name: String, keys: [String])] = [
    ("Smileys & Emotion", smileysKeys),
    ("People & Body", peopleKeys),
    ("Animals & Nature", natureKeys),
    ("Food & Drink", foodKeys),
    ("Travel & Places", travelKeys),
    ("Activities", activitiesKeys),
    ("Objects", objectsKeys),
    ("Symbols", symbolsKeys),
    ("Flags", flagsKeys),
]

@ViewBuilder
func itundaFaceEmojiGlyph(_ emoji: String, size: CGFloat) -> some View {
    switch emoji {
    case "👍": ReactionGlyph(emoji: emoji, size: size)
    case "❤️": ReactionGlyph(emoji: emoji, size: size)
    case "😂": ReactionGlyph(emoji: emoji, size: size)
    case "😮": ReactionGlyph(emoji: emoji, size: size)
    case "😢": ReactionGlyph(emoji: emoji, size: size)
    case "😀": SmileyGrinning(size: size)
    case "😄": SmileyGrinningEyes(size: size)
    case "🙂": SmileySlight(size: size)
    case "😉": SmileyWink(size: size)
    case "😍": SmileyHeartEyes(size: size)
    case "😘": SmileyKissHeart(size: size)
    case "😴": SmileySleeping(size: size)
    case "😭": SmileyLoudlyCrying(size: size)
    case "😡": SmileyAngry(size: size)
    case "😎": SmileyCool(size: size)
    case "👀": PeopleEyes(size: size)
    case "✊": PeopleFist(size: size)
    case "👋": PeopleWavingHand(size: size)
    case "✌️": PeopleVictoryHand(size: size)
    case "👌": PeopleOkHand(size: size)
    case "💪": PeopleMuscle(size: size)
    case "🙏": PeoplePray(size: size)
    case "👏": PeopleClappingHands(size: size)
    case "🤝": HandshakeGlyph(size: size)
    case "🐶": NatureDog(size: size)
    case "🐱": NatureCat(size: size)
    case "⭐": NatureStar(size: size)
    case "🌟": NatureGlowingStar(size: size)
    case "🌈": NatureRainbow(size: size)
    case "🌸": NatureCherryBlossom(size: size)
    case "🐦": NatureBird(size: size)
    case "🍕": FoodPizza(size: size)
    case "🍔": FoodHamburger(size: size)
    case "☕": FoodCoffee(size: size)
    case "🍰": FoodCake(size: size)
    case "🍩": FoodDonut(size: size)
    case "🍓": FoodStrawberry(size: size)
    case "🍉": FoodWatermelon(size: size)
    case "🍎": FoodApple(size: size)
    case "🚗": TravelCar(size: size)
    case "✈️": TravelAirplane(size: size)
    case "🏠": TravelHouse(size: size)
    case "🚀": TravelRocket(size: size)
    case "🚲": TravelBike(size: size)
    case "🌍": TravelGlobe(size: size)
    case "⚽": ActivitySoccer(size: size)
    case "🏀": ActivityBasketball(size: size)
    case "🎮": ActivityVideoGame(size: size)
    case "🎨": ActivityPalette(size: size)
    case "🎵": ActivityMusicalNote(size: size)
    case "🎉": ActivityPartyPopper(size: size)
    case "🏆": ActivityTrophy(size: size)
    case "🎯": ActivityDirectHit(size: size)
    case "💳": ObjectCreditCard(size: size)
    case "📱": ObjectMobilePhone(size: size)
    case "⌚": ObjectWatch(size: size)
    case "🔑": ObjectKey(size: size)
    case "💡": ObjectLightBulb(size: size)
    case "🎧": ObjectHeadphones(size: size)
    case "📎": ObjectPaperclip(size: size)
    case "🖊️": ObjectPen(size: size)
    case "✅": SymbolCheckMarkButton(size: size)
    case "❌": SymbolCrossMark(size: size)
    case "❗": SymbolExclamationMark(size: size)
    case "❓": SymbolQuestionMark(size: size)
    case "💯": SymbolHundred(size: size)
    case "⚠️": SymbolWarning(size: size)
    case "🚫": SymbolProhibited(size: size)
    case "♻️": SymbolRecycling(size: size)
    case "🇷🇼": FlagRwanda(size: size)
    case "🇰🇪": FlagKenya(size: size)
    case "🇺🇬": FlagUganda(size: size)
    case "🇹🇿": FlagTanzania(size: size)
    case "🇧🇮": FlagBurundi(size: size)
    case "🇨🇩": FlagCongo(size: size)
    case "🇺🇸": FlagUnitedStates(size: size)
    case "🇬🇧": FlagUnitedKingdom(size: size)
    case "🇪🇺": FlagEuropeanUnion(size: size)
    case "🇸🇸": FlagSouthSudan(size: size)
    case "🇸🇴": FlagSomalia(size: size)
    case "🇨🇳": FlagChina(size: size)
    case "🇮🇳": FlagIndia(size: size)
    case "🇧🇪": FlagBelgium(size: size)
    case "🇩🇪": FlagGermany(size: size)
    case "🇫🇷": FlagFrance(size: size)
    case "🇿🇦": FlagSouthAfrica(size: size)
    case "🇳🇬": FlagNigeria(size: size)
    case "🇪🇹": FlagEthiopia(size: size)
    case "🇰🇷": FlagSouthKorea(size: size)
    case "🇯🇵": FlagJapan(size: size)
    case "🇦🇪": FlagUnitedArabEmirates(size: size)
    case "🇨🇭": FlagSwitzerland(size: size)
    case "🇳🇱": FlagNetherlands(size: size)
    case "🇬🇭": FlagGhana(size: size)
    case "🇲🇦": FlagMorocco(size: size)
    case "🇪🇬": FlagEgypt(size: size)
    case "🇿🇲": FlagZambia(size: size)
    case "🇲🇿": FlagMozambique(size: size)
    case "🇸🇪": FlagSweden(size: size)
    case "🇨🇦": FlagCanada(size: size)
    case "🇦🇺": FlagAustralia(size: size)
    case "🇸🇳": FlagSenegal(size: size)
    case "🇨🇮": FlagIvoryCoast(size: size)
    case "🇨🇲": FlagCameroon(size: size)
    case "🇦🇴": FlagAngola(size: size)
    case "🇿🇼": FlagZimbabwe(size: size)
    case "🇧🇼": FlagBotswana(size: size)
    case "🇲🇼": FlagMalawi(size: size)
    case "🇶🇦": FlagQatar(size: size)
    case "🇮🇱": FlagIsrael(size: size)
    case "🇵🇰": FlagPakistan(size: size)
    case "🇷🇺": FlagRussia(size: size)
    default: EmptyView()
    }
}

/// Rasterizes an itundaface glyph to a UIImage once and caches it by
/// "emoji@size" -- avoids re-hosting a SwiftUI view tree on every render pass
/// of a chat thread. Not thread-safe by design (SwiftUI rendering is main-
/// thread-only anyway, same as every other itundaface draw call).
@MainActor
private final class ItundaFaceEmojiImageCache {
    static let shared = ItundaFaceEmojiImageCache()
    private var cache: [String: UIImage] = [:]

    func image(for emoji: String, size: CGFloat) -> UIImage? {
        let key = "\(emoji)@\(size)"
        if let cached = cache[key] { return cached }
        let host = UIHostingController(rootView: itundaFaceEmojiGlyph(emoji, size: size).frame(width: size, height: size))
        host.view.bounds = CGRect(x: 0, y: 0, width: size, height: size)
        host.view.backgroundColor = .clear
        let renderer = UIGraphicsImageRenderer(size: host.view.bounds.size)
        let rendered = renderer.image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        cache[key] = rendered
        return rendered
    }
}

// Built from the registry's own real keys, longest-first (so a future multi-
// codepoint entry, e.g. a ZWJ sequence, isn't shadowed by a shorter prefix
// match) -- matches the same registry-driven approach Android uses (rather
// than a broad Unicode-property scan) so both platforms degrade identically:
// anything not registered is never matched, and flows through as plain text.
private let itundaFaceEmojiRegex: NSRegularExpression = {
    let pattern = itundaFaceEmojiKeys.sorted { $0.count > $1.count }.map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "|")
    return try! NSRegularExpression(pattern: pattern)
}()

/// Renders a message body with any registered emoji swapped for itundaface's
/// own glyph inline, leaving unregistered emoji as plain text -- `body` itself
/// is never mutated, same display-layer-only discipline as every itundaface
/// batch. Falls back to a plain `Text` when nothing matches, the common case.
struct MessageBodyWithEmoji: View {
    // Named `messageText`, not `body` -- SwiftUI's View protocol requires a `var
    // body: some View`, so a same-named stored property here is a real
    // redeclaration error, the same class of bare-common-word collision as the
    // `View.badge(_:)` bug documented in ItundaFaceReactions.swift's own header.
    let messageText: String
    var color: Color = .primary
    var fontSize: CGFloat = 14

    // Real Dynamic Type fix (2026-08-30) -- both branches below now share one
    // UIFontMetrics-scaled size (see IDS.scaledUIFont's own doc comment) instead of
    // the raw `fontSize` point value, so a message that happens to contain an
    // itundaface emoji scales identically to one that doesn't. `.subheadline` is the
    // real closest Apple HIG text style to this type's only real call sites'
    // literal size (15pt, TalkChatBubbles.swift/TalkGroupExtras.swift), same
    // "match the literal to the closest real style" convention the rest of this
    // sweep already used.
    private var scaledFont: UIFont { IDS.scaledUIFont(size: fontSize, weight: .regular, relativeTo: .subheadline) }

    var body: some View {
        let ns = messageText as NSString
        let matches = itundaFaceEmojiRegex.matches(in: messageText, range: NSRange(location: 0, length: ns.length))
        let font = scaledFont
        guard !matches.isEmpty else {
            return AnyView(Text(messageText).foregroundColor(color).font(Font(font)))
        }
        let scaledSize = font.pointSize
        let glyphSize = scaledSize * 1.15
        let attributed = NSMutableAttributedString()
        var lastIndex = 0
        for match in matches {
            let range = match.range
            if range.location > lastIndex {
                attributed.append(NSAttributedString(string: ns.substring(with: NSRange(location: lastIndex, length: range.location - lastIndex))))
            }
            let emoji = ns.substring(with: range)
            if let image = ItundaFaceEmojiImageCache.shared.image(for: emoji, size: glyphSize) {
                let attachment = NSTextAttachment()
                attachment.image = image
                attachment.bounds = CGRect(x: 0, y: (scaledSize - glyphSize) / 2 - 1, width: glyphSize, height: glyphSize)
                attributed.append(NSAttributedString(attachment: attachment))
            } else {
                attributed.append(NSAttributedString(string: emoji))
            }
            lastIndex = range.location + range.length
        }
        if lastIndex < ns.length {
            attributed.append(NSAttributedString(string: ns.substring(from: lastIndex)))
        }
        attributed.addAttribute(.font, value: font, range: NSRange(location: 0, length: attributed.length))
        return AnyView(Text(AttributedString(attributed)).foregroundColor(color))
    }
}

/// Real chat-composer emoji picker -- distinct from the pre-existing KakaoTalk-
/// style sticker/emoticon picker elsewhere in Talk: this is Unicode text emoji,
/// picking one inserts the real character into the draft, no network call.
/// Renders each real category as its own labeled section, same category-array
/// shape as the other two platforms' pickers.
struct ItundaFaceEmojiPicker: View {
    let onPick: (String) -> Void
    private let columns = Array(repeating: GridItem(.flexible()), count: 6)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(itundaFaceEmojiCategories, id: \.name) { category in
                Text(category.name).font(.caption2).foregroundColor(.secondary)
                LazyVGrid(columns: columns, spacing: 4) {
                    ForEach(category.keys, id: \.self) { emoji in
                        Button(action: { onPick(emoji) }) {
                            itundaFaceEmojiGlyph(emoji, size: 28)
                        }
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}
