//
//  ItundaFacePlaces.swift
//  itundaface: place-category glyphs, ported from bank-mfe's
//  icons/ItundaFacePlaces.tsx / github.com/itunda-rw/itundaface's
//  svg/places/*.svg, matching Android's ItundaFacePlaces.kt. Replaces
//  MapScreenView.swift's own mapCategoryIcons raw-emoji dictionary, the
//  same literal duplicate flagged when the web and Android place icons
//  shipped. Every shape's data is copy-pasted byte-identical from those two
//  already-verified sources via CoreDesignSystem's shared
//  ItundaFaceGlyphCanvas/ItundaFaceShape primitives.
//

import SwiftUI
import CoreDesignSystem

private func placeBadge(_ hex: UInt) -> ItundaFaceShape { .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: hex)) }

private let restaurantShapes: [ItundaFaceShape] = [
    placeBadge(0xFEB6AA),
    .strokedPath(d: "M20,14 V26 M24,14 V26 M22,14 V44", color: Color(hex: 0xA20800), width: 2.8, cap: .round),
    .strokedPath(d: "M20,26 C20,29.5 24,29.5 24,26", color: Color(hex: 0xA20800), width: 2.8, cap: .round),
    .strokedPath(d: "M40,14 C34,16 34,22 40,24 V44", color: Color(hex: 0xA20800), width: 2.8, cap: .round),
]

private let cafeShapes: [ItundaFaceShape] = [
    placeBadge(0xECC38C),
    .filledPath(d: "M16,26 H40 V38 C40,43.5 35.5,48 30,48 H26 C20.5,48 16,43.5 16,38 Z", color: Color(hex: 0x744C00)),
    .strokedPath(d: "M40,28 H45 C47.8,28 50,30.2 50,33 C50,35.8 47.8,38 45,38 H40", color: Color(hex: 0x744C00), width: 2.6, cap: .round),
    .strokedPath(d: "M22,20 C22,17 25,17 25,14 M29,20 C29,17 32,17 32,14", color: Color(hex: 0x744C00), width: 2.2, cap: .round, alpha: 0.8),
]

private let hospitalShapes: [ItundaFaceShape] = [
    placeBadge(0xFEB6AA),
    .filledRect(x: 14, y: 16, w: 32, h: 34, rx: 4, color: Color(hex: 0xFFFFFF)),
    .strokedPath(d: "M30,22 V44 M19,33 H41", color: Color(hex: 0xA20800), width: 5, cap: .round),
]

private let pharmacyShapes: [ItundaFaceShape] = [
    placeBadge(0x8BDECB),
    .rotatedGroup(degrees: -40, pivotX: 30, pivotY: 30, shapes: [
        .filledRect(x: 12, y: 23, w: 36, h: 14, rx: 7, color: Color(hex: 0xFFFFFF)),
        .filledPath(d: "M12,30 A7,7 0 0 1 19,23 H30 V37 H19 A7,7 0 0 1 12,30 Z", color: Color(hex: 0x006455)),
    ]),
]

private let bankShapes: [ItundaFaceShape] = [
    placeBadge(0xC0C6FF),
    .filledPath(d: "M14,22 L30,12 L46,22 Z", color: Color(hex: 0x282565)),
    .filledRect(x: 14, y: 22, w: 32, h: 4, rx: 0, color: Color(hex: 0x282565)),
    .filledRect(x: 18, y: 28, w: 4, h: 16, rx: 0, color: Color(hex: 0x282565)),
    .filledRect(x: 26, y: 28, w: 4, h: 16, rx: 0, color: Color(hex: 0x282565)),
    .filledRect(x: 34, y: 28, w: 4, h: 16, rx: 0, color: Color(hex: 0x282565)),
    .filledRect(x: 42, y: 28, w: 4, h: 16, rx: 0, color: Color(hex: 0x282565)),
    .filledRect(x: 13, y: 46, w: 34, h: 4, rx: 1, color: Color(hex: 0x282565)),
]

private let atmShapes: [ItundaFaceShape] = [
    placeBadge(0xC0C6FF),
    .filledRect(x: 17, y: 14, w: 26, h: 34, rx: 4, color: Color(hex: 0x282565)),
    .filledRect(x: 21, y: 19, w: 18, h: 12, rx: 1.5, color: Color(hex: 0x7C7BFD)),
    .filledRect(x: 21, y: 35, w: 18, h: 3, rx: 1.5, color: Color(hex: 0x7C7BFD)),
    .filledCircle(cx: 34, cy: 42, r: 1.6, color: Color(hex: 0x7C7BFD)),
]

private let hotelShapes: [ItundaFaceShape] = [
    placeBadge(0xDCCB8A),
    .strokedPath(d: "M14,44 V26 C14,24.3 15.3,23 17,23 H27 C28.7,23 30,24.3 30,26 V32", color: Color(hex: 0x665400), width: 2.6, cap: .round, join: .round),
    .strokedPath(d: "M30,32 H43 C44.7,32 46,33.3 46,35 V44", color: Color(hex: 0x665400), width: 2.6, cap: .round, join: .round),
    .filledRect(x: 14, y: 32, w: 32, h: 4, rx: 1.5, color: Color(hex: 0x665400)),
    .strokedLine(x1: 12, y1: 44, x2: 12, y2: 38, color: Color(hex: 0x665400), width: 2.6, cap: .round),
    .strokedLine(x1: 48, y1: 44, x2: 48, y2: 38, color: Color(hex: 0x665400), width: 2.6, cap: .round),
]

private let supermarketShapes: [ItundaFaceShape] = [
    placeBadge(0xB9D79B),
    .strokedPath(d: "M16,16 H21 L26,38 H43 L47,22 H24", color: Color(hex: 0x3E6200), width: 3, cap: .round, join: .round),
    .filledCircle(cx: 29, cy: 45, r: 3.4, color: Color(hex: 0x3E6200)),
    .filledCircle(cx: 41, cy: 45, r: 3.4, color: Color(hex: 0x3E6200)),
]

private let gasStationShapes: [ItundaFaceShape] = [
    placeBadge(0xC0CCDD),
    .filledRect(x: 17, y: 16, w: 18, h: 32, rx: 3, color: Color(hex: 0x415676)),
    .filledRect(x: 21, y: 21, w: 10, h: 8, rx: 1.5, color: Color(hex: 0xC0CCDD)),
    .strokedPath(d: "M35,26 H39 C41,26 42,27.5 42,29.5 V40 C42,41.5 43,42.5 44.5,42.5 C46,42.5 47,41.5 47,40 V32 L44,29", color: Color(hex: 0x415676), width: 2.6, cap: .round, join: .round),
]

private let schoolShapes: [ItundaFaceShape] = [
    placeBadge(0x97D5F5),
    .filledPath(d: "M30,16 L50,25 L30,34 L10,25 Z", color: Color(hex: 0x005D7F)),
    .strokedPath(d: "M20,29 V38 C20,41 24,44 30,44 C36,44 40,41 40,38 V29", color: Color(hex: 0x005D7F), width: 2.4, cap: .round),
    .strokedLine(x1: 50, y1: 25, x2: 50, y2: 37, color: Color(hex: 0x005D7F), width: 2.2, cap: .round),
]

private let itundaAgentShapes: [ItundaFaceShape] = [
    placeBadge(0xC0C6FF),
    .strokedPath(d: "M24,20 C24,16 27,13 30,13 C33,13 36,16 36,20", color: Color(hex: 0x483EB6), width: 2.6, cap: .round),
    .filledPath(d: "M20,22 H40 L44,38 C45,43 41,48 35,48 H25 C19,48 15,43 16,38 Z", color: Color(hex: 0x483EB6)),
    .strokedCircle(cx: 30, cy: 34, r: 6, color: Color(hex: 0xC0C6FF), width: 2),
    .strokedLine(x1: 30, y1: 30, x2: 30, y2: 38, color: Color(hex: 0xC0C6FF), width: 2, cap: .round),
]

private let marketShapes: [ItundaFaceShape] = [
    placeBadge(0xB3D5B9),
    .filledPath(d: "M16,26 H44 L40,44 C39.5,46.3 37.5,48 35,48 H25 C22.5,48 20.5,46.3 20,44 Z", color: Color(hex: 0x156631)),
    .strokedPath(d: "M23,26 C23,20 26,16 30,16 C34,16 37,20 37,26", color: Color(hex: 0x156631), width: 2.6, cap: .round),
    .strokedPath(d: "M22,32 H38 M23,38 H37", color: Color(hex: 0xB3D5B9), width: 1.8, alpha: 0.7),
]

private let busStopShapes: [ItundaFaceShape] = [
    placeBadge(0xC0CCDD),
    .filledRect(x: 14, y: 18, w: 32, h: 22, rx: 5, color: Color(hex: 0x253142)),
    .filledRect(x: 18, y: 22, w: 9, h: 8, rx: 1.5, color: Color(hex: 0xC0CCDD)),
    .filledRect(x: 33, y: 22, w: 9, h: 8, rx: 1.5, color: Color(hex: 0xC0CCDD)),
    .filledCircle(cx: 21, cy: 43, r: 3.4, color: Color(hex: 0x253142)),
    .filledCircle(cx: 39, cy: 43, r: 3.4, color: Color(hex: 0x253142)),
]

private let itundaFacePlaces: [String: [ItundaFaceShape]] = [
    "RESTAURANT": restaurantShapes, "CAFE": cafeShapes, "HOSPITAL": hospitalShapes, "PHARMACY": pharmacyShapes,
    "BANK": bankShapes, "ATM": atmShapes, "HOTEL": hotelShapes, "SUPERMARKET": supermarketShapes,
    "GAS_STATION": gasStationShapes, "SCHOOL": schoolShapes, "ITUNDA_AGENT": itundaAgentShapes,
    "MARKET": marketShapes, "BUS_STOP": busStopShapes,
]

/// Renders itundaface's own glyph for a known place-category id, falling back to a
/// plain indigo pin dot for anything outside the 13 known categories -- matches
/// mapCategoryIcons' own existing `?? "📍"` fallback.
struct PlaceGlyph: View {
    let category: String
    var size: CGFloat = 24

    private static let fallbackShapes: [ItundaFaceShape] = [
        placeBadge(0xC0C6FF),
        ItundaFaceShape.filledCircle(cx: 30, cy: 24, r: 8, color: Color(hex: 0x483EB6)),
    ]

    private var shapes: [ItundaFaceShape] {
        if let known = itundaFacePlaces[category] {
            return known
        }
        return Self.fallbackShapes
    }

    var body: some View {
        ItundaFaceGlyphCanvas(size: size, viewBoxSize: 60, shapes: shapes)
    }
}
