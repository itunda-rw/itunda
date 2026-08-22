//
//  ItundaFaceReactions.swift
//  itundaface: chat quick-reaction glyphs, ported from bank-mfe's
//  icons/ItundaFace.tsx / github.com/itunda-rw/itundaface's svg/flat/*.svg,
//  matching Android's ItundaFaceReactions.kt -- itundaface's own flagship
//  group. Replaces TalkMessageBubbles.swift's quickReactions raw-emoji
//  rendering at both real render points (reaction-count badges + quick-react
//  picker; `quickReactions` itself stays as the underlying emoji-identifier
//  list, display-layer swap only).
//
//  `heart` keeps its real shared-seam two-facet construction (two
//  overlapping paths, not a single fill) and `laughing`/`sad` keep the real
//  itunda-indigo tear accent -- itundaface's own real identity signature,
//  ported byte-identical, not redrawn.
//
//  Naming note: every private helper here is prefixed `reaction` (not a
//  bare word like `badge`) -- SwiftUI's real `View.badge(_:)` modifier (and
//  many other short, common words: background, border, overlay, etc.)
//  silently shadows an unqualified same-named free function when called
//  from inside a View-conforming type, a real bug caught while porting the
//  place-category glyphs (ItundaFacePlaces.swift's own doc comment).
//

import SwiftUI
import CoreDesignSystem

private let reactionThumbsUpShapes: [ItundaFaceShape] = [
    .filledPath(d: "M28,32 V72 C28,74.2 26.2,76 24,76 H16 C13.8,76 12,74.2 12,72 V38 C12,35.8 13.8,34 16,34 H24 Z", color: Color(hex: 0xE0A655)),
    .filledPath(
        d: "M32,34 H56 C60,34 63,37.2 63,41.2 C63,42.6 62.6,44 61.9,45.1 C64.3,46.1 66,48.5 66,51.2 C66,53.4 64.9,55.3 63.2,56.6 " +
            "C64.3,58 65,59.8 65,61.7 C65,64.5 63.4,66.9 61.1,68.1 C61.4,68.9 61.6,69.8 61.6,70.7 C61.6,74.7 58.3,78 54.3,78 H36 C33.8,78 32,76.2 32,74 V34 Z",
        color: Color(hex: 0xFFCF87)
    ),
    .strokedPath(d: "M32,34 L38,16 C39,12.6 42.1,10.3 45.6,10.3 C47,10.3 48,11.5 47.8,12.9 L45.6,28", color: Color(hex: 0xFFCF87), width: 6, cap: .round),
]

// Real shared-seam two-facet construction -- one outer heart silhouette split by a
// shared seam into a lit right facet + a shaded left facet, the same technique
// itunda's own app-icon mark uses. Not a single gradient fill.
private let reactionHeartShapes: [ItundaFaceShape] = [
    .filledPath(d: "M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 L40,18.4 Z", color: Color(hex: 0xEF4A63)),
    .filledPath(d: "M40,18.4 C38,14.4 33.6,10 26.6,10 C17.1,10 8,17.7 8,29.6 C8,52.6 40,74 40,74 L40,18.4 Z", color: Color(hex: 0xC72E4C)),
]

private let reactionLaughingShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 40, cy: 40, r: 34, color: Color(hex: 0xFFCC4D)),
    .strokedPath(d: "M18,32 C21,26 27,26 30,32", color: Color(hex: 0x664500), width: 4.4, cap: .round),
    .strokedPath(d: "M50,32 C53,26 59,26 62,32", color: Color(hex: 0x664500), width: 4.4, cap: .round),
    .filledPath(d: "M16,48 C16,48 24,66 40,66 C56,66 64,48 64,48 C64,48 56,54 40,54 C24,54 16,48 16,48 Z", color: Color(hex: 0x66471B)),
    // Real itunda-indigo tear accent (identity-signature work) -- a tear's exact hue
    // isn't meaning-bearing the way the heart's red is, the one deliberate real-
    // brand-color touch on a secondary element.
    .strokedPath(d: "M23,52 C23,52 26,60 25,66", color: Color(hex: 0x7472F4), width: 4, cap: .round),
]

private let reactionWowShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 40, cy: 40, r: 34, color: Color(hex: 0xFFCC4D)),
    .filledCircle(cx: 26, cy: 34, r: 5, color: Color(hex: 0x664500)),
    .filledCircle(cx: 54, cy: 34, r: 5, color: Color(hex: 0x664500)),
    .filledEllipse(cx: 40, cy: 56, rx: 9, ry: 11, color: Color(hex: 0x66471B)),
]

private let reactionSadShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 40, cy: 40, r: 34, color: Color(hex: 0xFFCC4D)),
    .strokedPath(d: "M20,32 C23,36 29,36 32,32", color: Color(hex: 0x664500), width: 4.4, cap: .round),
    .strokedPath(d: "M48,32 C51,36 57,36 60,32", color: Color(hex: 0x664500), width: 4.4, cap: .round),
    .strokedPath(d: "M26,62 C30,54 50,54 54,62", color: Color(hex: 0x664500), width: 4.4, cap: .round),
    .filledPath(d: "M48,40 C51,44 54,49 54,53.6 C54,57.6 51,60.6 48,60.6 C45,60.6 42,57.6 42,53.6 C42,49 45,44 48,40 Z", color: Color(hex: 0x7472F4)),
]

// Maps quickReactions' own real emoji identifiers to itundaface's glyphs --
// display-layer only, the stored/toggled `emoji` identifier sent to the reaction
// toggle API is unchanged.
private let itundaFaceReactions: [String: [ItundaFaceShape]] = [
    "👍": reactionThumbsUpShapes,
    "❤️": reactionHeartShapes,
    "😂": reactionLaughingShapes,
    "😮": reactionWowShapes,
    "😢": reactionSadShapes,
]

/// Renders itundaface's own glyph for a known reaction emoji, rendering nothing for
/// anything outside the 5 known quick-reactions -- there's no raw-emoji-glyph
/// fallback path left to match once the source string itself no longer round-trips
/// through a real Unicode glyph on screen.
struct ReactionGlyph: View {
    let emoji: String
    var size: CGFloat = 24

    var body: some View {
        if let shapes = itundaFaceReactions[emoji] {
            ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: shapes)
        }
    }
}
