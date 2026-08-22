//
//  ItundaFaceSmileys.swift
//  itundaface Smileys & Emotion -- iOS port of the same phase-1 batch shipped to
//  bank-mfe/Android same session (icons/ItundaFaceSmileys.tsx /
//  ItundaFaceSmileys.kt), part of the "reach TossFace's 3,600-glyph scale"
//  initiative (see project_itunda_own_icons_graphics.md's staged category
//  roadmap -- this is NOT the older map-POI "Places" glyph group). Every
//  shape's coordinate data is copy-pasted byte-identical from the real,
//  already-verified web/Android source. Lives in App/Sources (not
//  Core/DesignSystem/Features/Maps) since, like ItundaFaceReactions.swift, chat
//  is the one real consumer today -- same single-target exception those files
//  already established.
//
//  Naming note: every private helper below is prefixed `smiley` (never a bare
//  common word like `face`/`badge`) -- see ItundaFaceReactions.swift's own doc
//  comment on the real SwiftUI View.badge(_:) name-collision bug this avoids.
//
//  One small, deliberate simplification vs. web: smileyCool's glass-glare
//  highlight ellipses are dropped -- filledEllipse has no alpha parameter here
//  either (matching the same Android simplification), and it's a decorative
//  detail only, not load-bearing for legibility.
//

import SwiftUI
import CoreDesignSystem

private let smileyLine = Color(hex: 0x664500)
private let smileyTear = Color(hex: 0x7472F4) // itunda's own real indigo, same signature accent as the laughing/sad reaction's tear.
private func smileyFace() -> ItundaFaceShape { .filledCircle(cx: 40, cy: 40, r: 34, color: Color(hex: 0xFFCC4D)) }

private let smileyGrinningShapes: [ItundaFaceShape] = [
    smileyFace(),
    .filledCircle(cx: 26, cy: 34, r: 4, color: smileyLine),
    .filledCircle(cx: 54, cy: 34, r: 4, color: smileyLine),
    .filledPath(d: "M20,48 C20,48 26,64 40,64 C54,64 60,48 60,48 C60,48 54,56 40,56 C26,56 20,48 20,48 Z", color: Color(hex: 0x66471B)),
    .filledPath(d: "M25,49.5 C25,49.5 31,53.5 40,53.5 C49,53.5 55,49.5 55,49.5 L55,52.2 C55,52.2 49,55.5 40,55.5 C31,55.5 25,52.2 25,52.2 Z", color: Color(hex: 0xFFFFFF)),
]

private let smileyGrinningEyesShapes: [ItundaFaceShape] = [
    smileyFace(),
    .strokedPath(d: "M18,32 C21,26 27,26 30,32", color: smileyLine, width: 4.4, cap: .round),
    .strokedPath(d: "M50,32 C53,26 59,26 62,32", color: smileyLine, width: 4.4, cap: .round),
    .filledPath(d: "M22,48 C22,48 28,62 40,62 C52,62 58,48 58,48 C58,48 52,55 40,55 C28,55 22,48 22,48 Z", color: Color(hex: 0x66471B)),
]

private let smileySlightShapes: [ItundaFaceShape] = [
    smileyFace(),
    .filledCircle(cx: 26, cy: 34, r: 3.6, color: smileyLine),
    .filledCircle(cx: 54, cy: 34, r: 3.6, color: smileyLine),
    .strokedPath(d: "M28,52 C32,58 48,58 52,52", color: smileyLine, width: 4.4, cap: .round),
]

private let smileyWinkShapes: [ItundaFaceShape] = [
    smileyFace(),
    .filledCircle(cx: 26, cy: 34, r: 4, color: smileyLine),
    .strokedPath(d: "M48,32 C51,36 57,36 60,32", color: smileyLine, width: 4.4, cap: .round),
    .strokedPath(d: "M26,50 C30,58 50,58 54,50", color: smileyLine, width: 4.4, cap: .round),
]

private let smileyHeartEyesShapes: [ItundaFaceShape] = [
    smileyFace(),
    .filledPath(d: "M26,30.6 C27.2,28.2 29.6,27 31.6,28.4 C33.6,29.8 33.6,32.6 31.6,34.6 C30,36.2 26,38.6 26,38.6 C26,38.6 22,36.2 20.4,34.6 C18.4,32.6 18.4,29.8 20.4,28.4 C22.4,27 24.8,28.2 26,30.6 Z", color: Color(hex: 0xEF4A63)),
    .filledPath(d: "M54,30.6 C55.2,28.2 57.6,27 59.6,28.4 C61.6,29.8 61.6,32.6 59.6,34.6 C58,36.2 54,38.6 54,38.6 C54,38.6 50,36.2 48.4,34.6 C46.4,32.6 46.4,29.8 48.4,28.4 C50.4,27 52.8,28.2 54,30.6 Z", color: Color(hex: 0xEF4A63)),
    .strokedPath(d: "M26,50 C30,58 50,58 54,50", color: smileyLine, width: 4.4, cap: .round),
]

private let smileyKissHeartShapes: [ItundaFaceShape] = [
    smileyFace(),
    .strokedPath(d: "M18,32 C21,26 27,26 30,32", color: smileyLine, width: 4.4, cap: .round),
    .filledCircle(cx: 54, cy: 34, r: 4, color: smileyLine),
    .filledEllipse(cx: 38, cy: 54, rx: 5, ry: 4, color: Color(hex: 0xC94F63)),
    .filledPath(d: "M62,38 C63.5,35.6 66.6,34.7 69,36.2 C71.4,37.7 72.1,41 70.6,43.4 C68.6,46.6 62,50 62,50 C62,50 60.8,42.6 62,38 Z", color: Color(hex: 0xEF4A63)),
]

private let smileySleepingShapes: [ItundaFaceShape] = [
    smileyFace(),
    .strokedPath(d: "M19,34 C22,31.4 28,31.4 31,34", color: smileyLine, width: 4, cap: .round),
    .strokedPath(d: "M49,34 C52,31.4 58,31.4 61,34", color: smileyLine, width: 4, cap: .round),
    .strokedPath(d: "M32,54 C35,56.4 45,56.4 48,54", color: smileyLine, width: 3.6, cap: .round),
    .strokedPath(d: "M56,16 L64,16 L55,25 L64,25", color: smileyLine, width: 2.6, cap: .round),
    .strokedPath(d: "M64,10 L70,10 L63,17 L70,17", color: smileyLine, width: 2.2, cap: .round, alpha: 0.7),
]

private let smileyLoudlyCryingShapes: [ItundaFaceShape] = [
    smileyFace(),
    .strokedPath(d: "M19,35 C22,31 28,31 31,35", color: smileyLine, width: 4.2, cap: .round),
    .strokedPath(d: "M49,35 C52,31 58,31 61,35", color: smileyLine, width: 4.2, cap: .round),
    .filledEllipse(cx: 40, cy: 58, rx: 11, ry: 9, color: Color(hex: 0x5C3D15)),
    .strokedPath(d: "M23,36 C23,36 21,50 15,58 C15,58 22,55 25,60", color: smileyTear, width: 4.4, cap: .round),
    .strokedPath(d: "M57,36 C57,36 59,50 65,58 C65,58 58,55 55,60", color: smileyTear, width: 4.4, cap: .round),
]

private let smileyAngryShapes: [ItundaFaceShape] = [
    smileyFace(),
    .strokedPath(d: "M18,29 L32,34", color: Color(hex: 0x7A4D15), width: 4.4, cap: .round),
    .strokedPath(d: "M62,29 L48,34", color: Color(hex: 0x7A4D15), width: 4.4, cap: .round),
    .filledCircle(cx: 27, cy: 37, r: 3.6, color: smileyLine),
    .filledCircle(cx: 53, cy: 37, r: 3.6, color: smileyLine),
    .strokedPath(d: "M28,58 C32,53 48,53 52,58", color: smileyLine, width: 4.4, cap: .round),
]

private let smileyCoolShapes: [ItundaFaceShape] = [
    smileyFace(),
    .filledRect(x: 15, y: 30, w: 21, h: 12, rx: 6, color: Color(hex: 0x20242C)),
    .filledRect(x: 44, y: 30, w: 21, h: 12, rx: 6, color: Color(hex: 0x20242C)),
    .strokedPath(d: "M28,52 C32,58 48,58 52,52", color: smileyLine, width: 4.4, cap: .round),
]

struct SmileyGrinning: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyGrinningShapes) } }
struct SmileyGrinningEyes: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyGrinningEyesShapes) } }
struct SmileySlight: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileySlightShapes) } }
struct SmileyWink: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyWinkShapes) } }
struct SmileyHeartEyes: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyHeartEyesShapes) } }
struct SmileyKissHeart: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyKissHeartShapes) } }
struct SmileySleeping: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileySleepingShapes) } }
struct SmileyLoudlyCrying: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyLoudlyCryingShapes) } }
struct SmileyAngry: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyAngryShapes) } }
struct SmileyCool: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: smileyCoolShapes) } }
