//
//  ItundaFacePeople.swift
//  itundaface People & Body -- iOS port of the same phase-2 batch shipped to
//  bank-mfe/Android same session. See ItundaFacePeople.tsx's own doc comment
//  for the full real sourcing story: after 6 hand-authored iteration passes
//  still didn't reach real quality, researched and confirmed Google's real
//  Noto Emoji (github.com/googlefonts/noto-emoji) is SIL Open Font License
//  1.1 -- genuinely permissive, unlike TossFace's restrictive terms
//  itundaface has deliberately avoided all session. The 7 hand-gesture
//  silhouette paths below are Noto's own real, professionally-drawn outer-
//  silhouette path data, fetched directly from their public repo and
//  recolored to itunda's palette -- not hand-approximated. Kept Noto's
//  native 128x128 viewBox (ItundaFaceGlyphCanvas already supports an
//  arbitrary viewBoxSize) rather than transforming every coordinate, so the
//  path data stays byte-identical to the real source.
//
//  REQUIRED ATTRIBUTION: see /NOTICE_THIRD_PARTY.md at the repo root (OFL
//  clause 2 requires the license notice travel with any copy).
//
//  Eyes keeps its original hand-authored construction -- it already read
//  clearly from the first pass, no Noto source was needed there.
//
//  Naming note: every private helper below is prefixed `people` (never a
//  bare common word) -- see ItundaFaceReactions.swift's own doc comment on
//  the real SwiftUI View.badge(_:) name-collision class of bug this avoids.
//

import SwiftUI
import CoreDesignSystem

private let peopleSkin = Color(hex: 0xFFCC4D)
private let peopleIndigo = Color(hex: 0x7472F4)

private let peopleEyesShapes: [ItundaFaceShape] = [
    .strokedEllipse(cx: 24, cy: 42, rx: 17, ry: 14, color: Color(hex: 0xB8B4AA), width: 2),
    .filledEllipse(cx: 24, cy: 42, rx: 17, ry: 14, color: Color(hex: 0xFFFFFF)),
    .strokedEllipse(cx: 56, cy: 42, rx: 17, ry: 14, color: Color(hex: 0xB8B4AA), width: 2),
    .filledEllipse(cx: 56, cy: 42, rx: 17, ry: 14, color: Color(hex: 0xFFFFFF)),
    .filledCircle(cx: 26, cy: 42, r: 7, color: peopleIndigo),
    .filledCircle(cx: 58, cy: 42, r: 7, color: peopleIndigo),
    .filledCircle(cx: 28, cy: 39, r: 2, color: Color(hex: 0xFFFFFF)),
    .filledCircle(cx: 60, cy: 39, r: 2, color: Color(hex: 0xFFFFFF)),
]

// Real Noto Emoji silhouette (u1F44B.svg), recolored. Rescaled 1.10x around
// Noto's own real bounding-box center (measured via pixel-bbox analysis, not
// guessed) -- Noto's native art has real safe-zone padding inside its
// 128x128 frame that reads noticeably smaller than TossFace's edge-to-edge
// convention, per direct user correction.
private let peopleWavingHandShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.10, fromCenterX: 64.8, fromCenterY: 65.0, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M93.3,60c0.2,0.3,0.7,0.1,0.7-0.2c0.6-5.4,2.2-20.3,12.8-23.5c3.4-1,6.8,1.4,7.2,4.5c0.7,5-5,17.9-4.5,29.7c0.1,1.9,3.3,22-5.2,33.9s-28.7,24-48.8,5.6c-10.4-9.5-10.4-13.3-23.3-26.6c-2.6-2.6-13-14-15.8-17.5c-3.7-4.7,2.2-10.9,6.7-7.7c2.1,1.5,20.7,17.1,21.4,17.8c1.4,1.2,3.1-0.5,2.1-1.7c-11.4-15-22.4-28.5-25.4-33.7s4.2-10.8,8.4-6.3c2.9,3,24.4,28.1,25.4,29.2s2.7-0.3,2.1-1.7C56.4,60.5,39,30.2,35.9,23.3c-2.7-6.1,6.3-11.8,10.5-5.5c3.4,5,22.4,36.6,23.1,37.7c0.9,1.6,2.9,0.6,2.1-1.2C71,53,59.3,21,58.4,17.7c-1.6-5.8,6.7-10.6,10.6-4C74.3,22.8,84.8,50.6,93.3,60z", color: peopleSkin),
        .strokedPath(d: "M30,70 C40,80 55,80 65,72", color: peopleIndigo, width: 3.5, cap: .round, alpha: 0.55),
    ]),
]

// Real Noto Emoji silhouette (u270A.svg), recolored. Rescaled 1.07x.
private let peopleFistShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.07, fromCenterX: 64.9, fromCenterY: 64.1, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M105.5,38.7c-0.1-4.5,0-18-0.8-22.7c-0.8-4.6-6-7.6-11.4-7.9C88,7.8,82,9.9,80.3,16.7C78.2,9.8,73,8.5,67.9,8.9C61.7,9.3,55.7,12.4,55.6,19c-1.7-3.9-6-5.6-12.2-4.1c-5,1.2-10.1,4-10.5,12.3c-4.5-3.3-20.7-2.1-20.3,17.3c0.1,5.8-1.9,8-1.6,18.1c1,26.2,15.1,48,31.3,53.7c10.9,3.9,27.1,7.1,44.7-2.7c19.2-10.7,27.9-33.7,29.2-37.5c3.4-9.5,3.8-18.6-2.8-27.4C110.3,44.4,105.5,38.7,105.5,38.7z", color: peopleSkin),
        .strokedPath(d: "M35,55 C45,65 60,68 72,60", color: peopleIndigo, width: 4, cap: .round, alpha: 0.55),
    ]),
]

// Real Noto Emoji silhouette (u270C.svg), recolored. Rescaled 1.06x.
private let peopleVictoryHandShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.06, fromCenterX: 63.8, fromCenterY: 63.9, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M94.6,75.6c3,3.4,5.8,8.8,4.1,16.8C98,95.7,92,114,78.5,120.1c-15.1,6.8-41.8,6.2-50-24c-4-14.5-2.2-18.3-2.1-30.9c0-9,7.8-14.8,15.3-8.2c-0.8-5,1.7-9.5,5.9-10.8c3.8-1.2,7.3-0.8,10.3,4.3c1-2.2-3.3-26.9-3.3-39.9c0-7.7,11.3-8.8,12.7-2.1c2,9.4,6.8,40.3,7.9,46c0.4,1.9,2.1,1.4,2.4,0.4c1.1-3.6,9.3-36.6,10.5-40.3c2.8-9,14.7-5.8,13.6,3.1c-1.8,13.7-5.5,29.8-7.7,45.5C93.3,67.7,94,71.6,94.6,75.6z", color: peopleSkin),
        .strokedPath(d: "M35,90 C45,98 60,98 70,90", color: peopleIndigo, width: 4, cap: .round, alpha: 0.55),
    ]),
]

// Real Noto Emoji silhouette (u1F44C.svg), recolored. Rescaled 1.05x.
private let peopleOkHandShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.05, fromCenterX: 62.8, fromCenterY: 64.8, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M60.3,47.1c-2.2-7-5.9-10.8-6.8-14.1c-1.1-3.8,3.3-6.4,5.7-3.3c-1.2-3-5-5.6-5.4-10.8c-0.1-1.6,0.5-4.4,4.2-4.9c-0.7-4.1,0.7-6,2-6.8c1.7-1,3.5-0.5,4.6,0.3c10,7.6,24.3,24.8,31.3,49.1c0.7,2.4,1.2,4.5,1.3,6.6c0.1,1.8,0.3,3.9,0.3,8.3c0,7.8-0.8,17.3-1.8,32.6c0,0.7,0.1,1.7,0.3,2.3c0.3,0.6,0.4,1.2-0.2,2c-4.6,5.1-19.7,13.2-29.3,14.6c-1,0.1-1.4-0.3-1.8-0.7c-4.7-4.2-11.5-4.3-17.9-9.1c-8.4-6.4-15.6-14.6-18.7-30.7c-0.5-2.8,2.1-7,6.9-5.6c10.8,3.3,5.5,14.7,18.8,18.5c10.1,2.9,19.9-5.7,19.8-14c-0.1-6.8-2.4-14.2-11.4-16.3c-11.7-2.8-18.8,11.3-25.6,10.8c-4.4-0.4-6.2-2.4-6.6-4.7C29.5,68.3,32.9,64,42.2,56C46,52.8,52.6,48.6,60.3,47.1z", color: peopleSkin),
        .strokedPath(d: "M40,95 C50,102 65,100 75,90", color: peopleIndigo, width: 4, cap: .round, alpha: 0.55),
    ]),
]

// Real Noto Emoji silhouette (u1F4AA.svg), recolored. Rescaled 1.05x.
private let peopleMuscleShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.05, fromCenterX: 63.8, fromCenterY: 63.8, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M79.5,28.9c1.9,2,2.3,4.5,1.5,5.9c-0.9,1.6-2,2.4-3.7,2.4c-0.7,0-4-0.1-5.8,1c-2,1.2-6.3,3.3-11.3,2.8c-3.3-0.3-7.6-3.6-9.6-1.2c-1,1.2-2.6,5.7-2.7,12c-0.1,4.4,0.6,19.9-1.4,29.8C46.4,82.3,47,83,47.7,83c1.9-0.1,4.1-0.9,6-5.5c10.1-24.7,51.5-23.1,57.6,4.7c2.3,10.2,5.4,31.2-14.7,37.2c-14.9,4.4-35.9,0.8-47.1-0.1c-7-0.6-32.4-1.7-34.6-15.9c-0.7-4.1,0.3-11.2,1.6-20.6c0.4-3.2,1.4-12.2,4.7-22.1C24,52.7,28,45.3,33,37c1.8-3,4-7.9,9.8-13.9c8.1-8.4,18-14.2,23.7-16.8c4.2-1.9,6.6,1.4,7.7,4c0.9,2.2,6.1,9.2,8.5,12.5c0.7,1,1.2,3.2-0.3,4.2C81,28.1,79.5,28.9,79.5,28.9z", color: peopleSkin),
        .strokedPath(d: "M55,45 C62,50 70,50 76,44", color: peopleIndigo, width: 4, cap: .round, alpha: 0.55),
    ]),
]

// Real Noto Emoji silhouette (u1F64F.svg), recolored. Rescaled 1.14x.
private let peoplePrayShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.14, fromCenterX: 64.0, fromCenterY: 59.1, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M102.8,89.9l-15.7,20.3c0,0-14.6-8-18.4-10S64,91.4,64,91.4s-0.9,6.8-4.7,8.8s-18.4,10-18.4,10L25.2,89.9c5.4-2.5,8.7-5,10.9-6.8c2.2-1.8,4-4.8,4.8-7.8c0.7-3,3.7-17.6,3.8-20s0.8-5.4,2.3-7.8s2-6.6,3-11.8c1.7-9,3-13.5,3.3-15.7c0.3-2.1,0.5-7.1,0.6-8.7C54,9.9,55.6,8.1,58.2,8c4.1-0.2,5.8,2.2,5.8,5.4c0-3.3,1.7-5.6,5.8-5.4c2.6,0.1,4.1,1.9,4.3,3.5c0.1,1.6,0.3,6.6,0.6,8.7c0.3,2.1,1.6,6.7,3.3,15.7c1,5.2,1.6,9.5,3,11.8s2.2,5.4,2.3,7.8c0.1,2.4,3.1,17,3.8,20c0.8,3,2.6,6,4.8,7.8C94.1,85,97.4,87.4,102.8,89.9z", color: peopleSkin),
        .strokedPath(d: "M64,15 L64,105", color: peopleIndigo, width: 3, cap: .round, alpha: 0.45),
    ]),
]

// Real Noto Emoji silhouette (u1F44F.svg), recolored -- motion lines recolored
// from Noto's own grey to itunda indigo (same non-semantic-accent rule as
// every other secondary element in itundaface, not a literal palette copy).
// Rescaled 1.24x.
private let peopleClappingHandsShapes: [ItundaFaceShape] = [
    .scaledGroup(scale: 1.24, fromCenterX: 64.9, fromCenterY: 67.5, toCenterX: 64, toCenterY: 64, shapes: [
        .filledPath(d: "M99,44.7c1.9-4.5,5.5-7.7,9.7-6.7c2.9,0.6,3.5,3.3,3.3,4.9c-2.3,15.9,1.1,28.1,0.2,39.9c-1.1,14.9-10.5,30.3-30.2,31.7c-12.5,3-26.4,0.9-37.6-10c-6.2-6-11.6-10.6-19.1-22.8c-3.1-5.1-7.9-13.2-7.9-18.5c0-4.5,4.5-5.9,6.8-3.8c-3-4.8-4.4-8.3-4.5-12.4c-0.1-3.4,4.1-5.8,6.8-3.2c-2.3-3.7-5.5-10.2-0.7-13.5c1.5-1,5.3-2.3,9.9,3.5c-1.2-2.3-2.5-6.2,0.7-8.8c1.7-1.4,5-1.8,7.8,1.4c-0.2-1.2,1.5-4.2,4.3-4.3c3.4-0.2,6.1,2.9,8.1,5.2c-2-8,6.3-10.3,10.9-4.9c2.4,2.7,8,9.5,16.4,23.5c-0.2-8.4,3.9-15.8,9.9-14.7c2.1,0.4,4.2,1.8,4.4,5.7C98.6,40.4,99,44.7,99,44.7z", color: peopleSkin),
        .filledPath(d: "M19.7,85.2c-1.2,0.1-10.6,0.5-12,0.6c-2.6,0.2-2.4,4.6,0.2,4.5c1.4-0.1,12-0.6,12-0.6C22.7,89.4,22.4,85,19.7,85.2z", color: peopleIndigo.opacity(0.6)),
        .filledPath(d: "M29,100.2c-0.4,1.2-3.4,10-3.9,11.4c-0.8,2.5,3.4,3.9,4.2,1.4c0.5-1.4,3.9-11.4,3.9-11.4C34,99.1,29.8,97.7,29,100.2z", color: peopleIndigo.opacity(0.6)),
        .filledPath(d: "M111.4,18.4c-0.9,0.8-8.2,6.7-9.2,7.7c-2,1.7,0.8,5.1,2.8,3.4c1.1-0.9,9.2-7.7,9.2-7.7C116.3,20,113.4,16.6,111.4,18.4z", color: peopleIndigo.opacity(0.6)),
        .filledPath(d: "M81,12.2c0.4,1.2,2.9,10.2,3.3,11.5c0.7,2.5,5,1.3,4.3-1.2C88.2,21.1,85.3,11,85.3,11C84.5,8.4,80.2,9.6,81,12.2z", color: peopleIndigo.opacity(0.6)),
        .filledPath(d: "M23.1,93.1c-1,0.7-14,9.8-15.2,10.7c-2.1,1.5,0.5,5.2,2.6,3.6c1.2-0.9,15.2-10.7,15.2-10.7C27.9,95,25.2,91.4,23.1,93.1z", color: peopleIndigo.opacity(0.6)),
        .filledPath(d: "M99.6,5.8C99.2,7,93.8,22,93.4,23.4c-0.8,2.5,3.4,4,4.2,1.5c0.5-1.4,6.2-17.6,6.2-17.6C104.6,4.7,100.4,3.3,99.6,5.8z", color: peopleIndigo.opacity(0.6)),
    ]),
]

// Real Noto Emoji silhouette (u1F91D.svg -- handshake), recolored. Real,
// multi-round correction, found live via direct user feedback twice:
// (1) a first attempt using only the 3 base-fill silhouette paths read as
// an unreadable blob/cape ("that doesn't look like a handshake"); (2) a
// second attempt adding all 13 of Noto's own real internal shading/crease
// paths, rendered UNCLIPPED, still read as "not good" -- direct comparison
// against TossFace's own real handshake (visual reference only, never
// copied -- see this file's own header on itundaface's TossFace-license
// discipline) showed a real quality gap. Investigated properly rather than
// guessing a third time: rendered Noto's ORIGINAL, complete, correctly-
// clipped SVG directly (not this file's own hand-parsed reconstruction) and
// found it produces the SAME softer result -- proving the gap is a genuine
// Noto-vs-TossFace HOUSE STYLE difference, not a bug in this port. User
// chose to ship Noto's real design as-is (a real, correctly-sourced,
// permissively-licensed glyph, not a mistake) and to build real clipPath
// support rather than approximate it unclipped, for faithfulness and so any
// future clipPath-based Noto glyph can reuse it. See
// ItundaFaceShape.clippedGroup's own doc comment (ItundaFaceCanvas.swift)
// for the primitive itself. All 13 shading paths clip to the exact same
// boundary (Noto's own source duplicates the base silhouette 4 times purely
// to feed 4 separate `<clipPath>` defs, confirmed by diffing each clip
// target's "d" string) -- one clippedGroup wrapping all 13 in source order
// is exactly equivalent to the source's 4 separate groups. Two real Noto
// tones kept as-is (FAA700 mid shadow, B55E19 deep crease line), same
// deliberate exception to this file's "one flat peopleSkin tone" rule as
// every multi-tone Animals & Nature glyph -- the shading IS the semantic
// content here, not decoration.
private let peopleHandshakeShapes: [ItundaFaceShape] = [
    .filledPath(d: "M69.3,31.7c11.4-1.9,31.6,1.3,44.8-7.6c1.7-1.2,2.6-0.1,3,0.4 c3.2,4.1,6.7,28.5,4.9,34.7c-0.5,1.7-1.2,2.2-2.1,2.8c-2.1,1.6-9,6.8-15.9,16c1.8,3.1,1.2,5.6-0.8,7.7c-2,2.2-4.3,3-7.3,2 c0,2.4-0.2,3.7-1.7,5.2c-2.3,2.2-5.6,2.8-8.1,2.3c0.2,2.5-1.3,3.8-2.4,4.6c-2.5,1.9-4.9,2.4-8.3,1c-0.7,1.4-5.3,6-10.9,2.4 c-1.1-0.7-3.7-2.6-3.7-2.6s-5.3,5-10.8,2.8c-1.1-0.4-2.6-1.8-3-3.1c-1.3,0.9-5,1.2-6.9-0.2c-1.8-1.4-4.2-3.6-3.6-7.3 c-2,0.8-3.8,0.9-6.5-1.6c-2-1.9-3.7-4.4-2.4-6.6c-2.1,0.4-3.7-0.3-4.7-1.5c-1-1.2-3.4-4.8-0.7-8.2c-7.5-5-11.9-9.5-13.8-10.4 c-1.1-0.6-1.9-1.5-2.2-2.8c-0.9-4.7,0-30.5,5.1-36c0.9-1,2.1-0.7,2.7-0.3C43.7,43.5,51.5,25.4,69.3,31.7z", color: Color(hex: 0xFFCA28)),
    .filledPath(d: "M51,93c2.6-3,3.4-6.7,0.9-9.2c-3.5-3.4-6.3-2.1-8.9-0.8c2.6-2.9,2.2-6.4-0.1-8.4 C39.5,71.7,36,74,33,77c0.6-2,1.7-2.7-0.9-5.7c-2.4-2.7-8.4-0.3-9.9,3.6c-2.7,3.4-0.3,7,0.7,8.2s2.6,1.9,4.7,1.5 c-1.3,2.2,0.4,4.7,2.4,6.6c2.7,2.5,4.5,2.4,6.5,1.6c-0.6,3.7,1.8,5.9,3.6,7.3c1.9,1.4,5.6,1.1,6.9,0.2c0.4,1.3,1.9,2.7,3,3.1 c2.3,0.9,4.6,0.6,6.4-0.1c2.9-3.7,4.2-9.2,1.1-11.6C55.2,90,53,91,51,93z", color: Color(hex: 0xFFCB05)),
    .filledPath(d: "M117.1,24.5c-0.4-0.5-1.3-1.6-3-0.4c-13.2,8.9-33.4,5.7-44.8,7.6c-2.4,0-3.7,0.1-6.6,1.2 c-4.4,1.7-11.7,7.8-19.5,10.8c-3.6,1.4-4.5,6.8-0.1,9.1c9.3,5,19.5-4.6,29-5.2C80.4,47,87.2,57,92,63c2.9,3.7,7.7,10,12,15 c6.4-8.7,13.9-14.5,15.9-16c0.9-0.6,1.6-1.1,2.1-2.8C123.8,53,120.3,28.6,117.1,24.5z", color: Color(hex: 0xFFCB05)),
    .clippedGroup(clipPathD: "M69.3,31.7c11.4-1.9,31.6,1.3,44.8-7.6c1.7-1.2,2.6-0.1,3,0.4 c3.2,4.1,6.7,28.5,4.9,34.7c-0.5,1.7-1.2,2.2-2.1,2.8c-2.1,1.6-9,6.8-15.9,16c1.8,3.1,1.2,5.6-0.8,7.7c-2,2.2-4.3,3-7.3,2 c0,2.4-0.2,3.7-1.7,5.2c-2.3,2.2-5.6,2.8-8.1,2.3c0.2,2.5-1.3,3.8-2.4,4.6c-2.5,1.9-4.9,2.4-8.3,1c-0.7,1.4-5.3,6-10.9,2.4 c-1.1-0.7-3.7-2.6-3.7-2.6s-5.3,5-10.8,2.8c-1.1-0.4-2.6-1.8-3-3.1c-1.3,0.9-5,1.2-6.9-0.2c-1.8-1.4-4.2-3.6-3.6-7.3 c-2,0.8-3.8,0.9-6.5-1.6c-2-1.9-3.7-4.4-2.4-6.6c-2.1,0.4-3.7-0.3-4.7-1.5c-1-1.2-3.4-4.8-0.7-8.2c-7.5-5-11.9-9.5-13.8-10.4 c-1.1-0.6-1.9-1.5-2.2-2.8c-0.9-4.7,0-30.5,5.1-36c0.9-1,2.1-0.7,2.7-0.3C43.7,43.5,51.5,25.4,69.3,31.7z", shapes: [
        .filledPath(d: "M22.5,72.1c0,0-14-8.7-15-9.3s-1.6-1.6-2.3-3s-0.8,1.4-0.8,1.4l12.2,17.5c2.2-1.9,4.4-3.8,6.6-5.9 C23,72.3,22.5,72.1,22.5,72.1z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M105.1,77.5c-4.7-1.9-5.9-8.2-10.5-10c1.9,2.7,4.7,6.8,5.3,7.6c1.6,2.2,0.8,3.6,0.3,4.5 c-0.6,0.9-1.9,2.5-3.1,3.5c-1.8,1.5-4.2,0.9-5.3,0.2c-1.1-0.7-3.3-0.4-2,1.2c2.3,3,1.3,4.3,1,4.9c-0.5,0.8-1.5,1.9-2.2,2.5 c-1.4,1.2-4.4,0.9-5.5,0.2c-1.1-0.7-3.6-0.9-2.5,0.4c1.7,2,0.4,3.6,0.1,4s-1,1.1-2,2.2c-0.8,0.9-2.9,1-4.1,0.3 c-0.7-0.4-4.2-3.3-5-3.8s-2.4-0.1-0.9,2c1.5,2.2,1.3,2.7,0.8,3.5c-0.2,0.3-0.6,0.8-1.3,1.5c-1.4,1.4-3.3-0.2-3.3-0.2l-5.7-4 c0,0-0.3,0.2-1.1,0.7c0.3,2.7,1.3,5.4,1.7,8l2.3,0.3l22.4-4.3l23.8-18.2L105.1,77.5z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M80.8,52.2c-1.9-1.1-4.2-2.6-7.7-1s-11.6,5.4-13.9,6.4c-2.4,1-8.4,2.6-11.9-2.5 c4.6-1.3,19.8-7,19.8-7l6.8-0.9l5.3,1.9c0,0,1.8,1.4,2.4,2C82.3,51.7,80.8,52.2,80.8,52.2z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M78,20.5c-30.6,0.2-68.9,0.4-68.9,0.4l0.6,5c31.8,19.3,40.3,4.9,51.5,6.9 c3.2-0.5,8.8-1.6,10.7-1.8C78.4,27.6,77.6,29.8,78,20.5z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M22.5,71.7c-0.7-0.4-8-4.5-10.6-5.5c-1.1-0.4-1.3,0.2-1.1,0.6l4.5,5.1c0,0,5.2,4.7,6.9,3 C23,74.1,23.7,72.5,22.5,71.7z", color: Color(hex: 0xB55E19)),
        .filledPath(d: "M95.9,87.7c-5-1.9-9.7-6.2-16.9-13.2c-1.1-1.1,0.6-3.2,2.1-1.8C90,81,92,82.8,97.5,86.4 c2.5,1.7,3.8,0.3,3.8,1.4c0,0.7-0.7,1.3-2,1.4S95.9,87.7,95.9,87.7z", color: Color(hex: 0xB55E19)),
        .filledPath(d: "M86.1,95.2c-1.5-0.3-4.1,0.1-15.5-11.4c-1.2-1.3,0.6-3.2,2.1-1.7c5.9,5.9,8.5,8.1,13,11.4 c2.6,2,3.7,0.9,3.7,2C89.4,96.3,86.1,95.2,86.1,95.2z", color: Color(hex: 0xB55E19)),
        .filledPath(d: "M75.4,100.8c-1.6-0.5-10.5-7.4-11.4-8.2c-1.5-1.3,0-2.9,1.6-1.6c5.5,4.8,9.6,8.1,11,9 c2.1,1.4,2.7,0.9,2.7,1.8C79.3,102.7,77,101.3,75.4,100.8z", color: Color(hex: 0xB55E19)),
        .filledPath(d: "M115.1,20.4c0,0-16.8,0-37.1,0.1c-0.4,9.3-11.2,9.6-17.8,13c-2.9,2.1-10.4,6.9-10.4,6.9 s3,1.6,6-0.3c9.5-6.2,13.2-6.8,17.4-6.7c26.2,0.2,34.5-1.3,44.4-9.6C119.2,22.5,115,20.5,115.1,20.4z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M58.1,98.6c-0.5,0.3-1.1,0.8-1.8,1.5c-2.3,2.1-3.9,1.4-5.6,0c-1.7-1.4-1.2-3.1-0.8-4.3 c0.4-1.2-0.4-0.5-2.9,1.3c-2.9,2.2-4.3,0.9-5.4,0.2c-0.6-0.3-1.7-1.5-2.7-2.8c-1.6-2.1-0.3-4.3,0.1-5s0.1-1.4-1.1-0.7 c-2,1.2-3.9,0-4.7-0.5c-0.7-0.5-1.9-1.7-2.7-2.7c-1.6-2-0.5-3.7-0.1-4.7s-0.5-1.6-2.3-0.4c-2.4,1.5-7.2-1.5-4.9-6.6 c0.2-0.5-0.4-0.9-1,1c-2.2,2.1-3.4,1.9-5.6,3.8l0.8,1.1l11,13.6l17.4,11.4l14,1.9C59.4,104,58.4,101.3,58.1,98.6z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M105.1,77.5l-0.7-1.5c0,0-1.5-7.9-1.4-13.1c2.4-3.5,1.9-5.6,0.1-6.5c-1.8-0.8-5.3,0.1-8.6-0.5 L89.9,58l3,7c0,0,0.7,1,1.7,2.4C99.2,69.2,100.4,75.6,105.1,77.5z", color: Color(hex: 0xFAA700)),
        .filledPath(d: "M120.6,62.6c-1.8,0.3-5.8-0.2-17,11.2c-0.5,0.5-1.2,0.5-1.7,0c-2.3-2.7-5.1-6.9-8.4-13.2 c-0.5-0.9-0.1-2.4,1.5-2.3c1.6,0.1,5.7-0.6,6.6-1.2c1-0.6,0.2-1.1-0.4-1.1C96,55.9,90,56,80.8,48.6c-1.5-1.2-7.4-5.1-16.2-1 c-4,1.8-9.2,3.8-10.8,4.3c-2.7,0.9-9,2.4-11.7-1.9c-0.9-1.4-1.3-3.8,1-5c2.4-1.2,5.4-2.5,9.2-4.7c6.1-3.5,10.9-7.8,17-8.6 c0.6-1,0.6-0.6,0.1-0.5c-13.6,0.7-13.4,6-27,11.7c-3.3,1.4-4.4,4.5-2.5,7.9c2.5,4.4,6.2,5.7,15.5,3.4c5-1.2,14.7-7.7,21.4-4.4 c3.1,1.5,8.4,6.3,15.1,14.9c6.6,8.4,11,11.4,12.1,13.3c2.7,0.6,9-5.2,9-5.2L120.6,62.6z", color: Color(hex: 0xB55E19)),
        .filledPath(d: "M22.5,71.7c3.5-4.2,8.1-3.1,10-1.6c1.6,1.3,2.1,2.6,2.1,4c1.3-1,4.8-4,9.4-0.2 c1.7,1.4,2.6,3.9,1.8,6.7c1.6-0.4,4.5-0.1,6.7,2s2.9,5.2,1.4,7.7c1.9-1,6.8,0.5,6.4,5c-0.1,1.4-0.6,2.9-1.2,4 c0.6,0.6,1.7,1.3,1.7,1.3l0.2,2.9c0,0-7.1,0.9-7.8,1.1c-0.8,0.2-1.8-0.8-1.2-1c0.6-0.3,1.9-0.6,3.2-2.2c1.2-1.6,2.1-3.3,2.4-3.9 c2.4-4-2.6-6.7-5-4.7c-1.1,0.9-3.3,5-5.6,7.5c-3.2,3.7-5.8,0.8-6,0.6c-0.3-0.2-0.9-0.8,0-0.9s3.8,0.2,5.3-2.2 c1.5-2.4,4.1-6,4.7-7s2.3-4.1-0.5-6.5c-0.8-0.7-4-2.7-7.1,0.3c-1.3,1.2-5.6,6.5-6.9,8.2c-1.3,1.7-3.8,1.5-4.8,0.2 c-0.3-0.4-0.4-1.1,0.4-0.8c0.8,0.3,1.9,0.4,3.4-1.3c1.5-1.7,6.5-8.4,7.2-9.5c0.8-1.1,1.6-3.5-0.4-5.7c-0.8-0.9-3.4-1.9-5.4-0.6 c-3,1.9-8,7.8-9.3,9.5s-3.7-0.1-4-0.5s-0.3-0.9,0.5-0.8c0.9,0.1,1.2,0.3,2.3-0.9s4.8-5.1,5.3-5.6c1-1.1,1.2-2.6,0-4.2 c-1.3-1.7-4.5-2-6.2-0.6c-1.5,1.2-2.5,2.1-3.3,2.9C21.5,74,21.5,72.9,22.5,71.7z", color: Color(hex: 0xB55E19)),
    ]),
]

struct PeopleEyes: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: peopleEyesShapes) } }
struct PeopleWavingHand: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleWavingHandShapes) } }
struct PeopleFist: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleFistShapes) } }
struct PeopleVictoryHand: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleVictoryHandShapes) } }
struct PeopleOkHand: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleOkHandShapes) } }
struct PeopleMuscle: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleMuscleShapes) } }
struct PeoplePray: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peoplePrayShapes) } }
struct PeopleClappingHands: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleClappingHandsShapes) } }
struct HandshakeGlyph: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: peopleHandshakeShapes) } }
