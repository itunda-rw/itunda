//
//  ItundaFaceMisc.swift
//  itundaface: misc single-use glyphs, ported from bank-mfe's
//  icons/ItundaFaceMisc.tsx / github.com/itunda-rw/itundaface, matching
//  Android's ItundaFaceMisc.kt. Lives in CoreDesignSystem since real usage
//  is scattered across the App target and Feature modules. Starts with
//  PackageGlyph (Marketplace escrow delivery address); the rest of the web/
//  Android misc tail follows in later passes.
//

import SwiftUI

private let packageShapes: [ItundaFaceShape] = [
    .filledPath(d: "M12,2.5 L21,7 V17 L12,21.5 L3,17 V7 Z", color: Color(hex: 0xC8935A)),
    .filledPath(d: "M12,2.5 L21,7 L12,11.5 L3,7 Z", color: Color(hex: 0xE0AC74)),
    .strokedPath(d: "M12,11.5 V21.5", color: Color(hex: 0x8A5F33), width: 1.2),
    .strokedPath(d: "M7.5,4.7 L16.5,9.2", color: Color(hex: 0x8A5F33), width: 1.4, cap: .round),
]

private let flameShapes: [ItundaFaceShape] = [
    .filledPath(
        d: "M12,22 C7.5,22 5,18.8 5,15.2 C5,11.5 7.4,8.6 8.4,5.6 C8.7,4.7 9.7,4.6 10.1,5.4 C10.9,7 11,8.8 12,9 " +
            "C13,8.3 12.8,5.2 12.2,3.2 C11.9,2.2 12.8,1.5 13.6,2.1 C17.4,4.9 19,9.5 19,13.5 C19,18.5 16,22 12,22 Z",
        color: Color(hex: 0xF0740A)
    ),
    .filledPath(
        d: "M12,20 C9.5,20 8,18 8,15.8 C8,13.8 9.3,12.3 10.2,10.9 C10.6,10.3 11.4,10.5 11.5,11.2 C11.6,12.2 12.1,12.6 12.6,12.2 " +
            "C13.1,11.8 13,10.4 12.7,9.4 C12.5,8.7 13.2,8.2 13.8,8.6 C15.6,9.9 16.5,12.1 16.5,14.2 C16.5,17.6 14.6,20 12,20 Z",
        color: Color(hex: 0xFFB02E)
    ),
]

private let bikeShapes: [ItundaFaceShape] = [
    .strokedCircle(cx: 5.5, cy: 17, r: 4, color: Color(hex: 0x0A8A72), width: 1.8),
    .strokedCircle(cx: 18.5, cy: 17, r: 4, color: Color(hex: 0x0A8A72), width: 1.8),
    .strokedPath(d: "M5.5,17 L10,8 H15 M10,8 L13,13 M13,13 L18.5,17 M13,13 L8.5,17", color: Color(hex: 0x0A8A72), width: 1.8, cap: .round),
    .filledCircle(cx: 15, cy: 8, r: 1.3, color: Color(hex: 0x0A8A72)),
    .strokedPath(d: "M9,8 H11.4", color: Color(hex: 0x0A8A72), width: 1.8, cap: .round),
]

private let electricBikeShapes: [ItundaFaceShape] = bikeShapes + [
    .filledCircle(cx: 18.5, cy: 6, r: 5.6, color: Color(hex: 0xFFB02E)),
    .filledPath(d: "M19.6,2.4 L16.8,6.6 H18.7 L17.9,9.6 L21,5.2 H19 Z", color: Color(hex: 0x7A5300)),
]

private let cameraShapes: [ItundaFaceShape] = [
    .filledPath(
        d: "M10,22 H26 L30,14 H42 L46,22 H62 C64.2,22 66,23.8 66,26 V58 C66,60.2 64.2,62 62,62 H10 " +
            "C7.8,62 6,60.2 6,58 V26 C6,23.8 7.8,22 10,22 Z",
        color: Color(hex: 0x415676)
    ),
    .filledCircle(cx: 36, cy: 42, r: 14, color: Color(hex: 0xC0CCDD)),
    .filledCircle(cx: 36, cy: 42, r: 8, color: Color(hex: 0x415676)),
    .filledCircle(cx: 56, cy: 30, r: 2.6, color: Color(hex: 0xC0CCDD)),
]

private let clockShapes: [ItundaFaceShape] = [
    .strokedCircle(cx: 36, cy: 36, r: 27, color: Color(hex: 0x415676), width: 4.4),
    .strokedPath(d: "M36,20 V37 L48,45", color: Color(hex: 0x415676), width: 4.4, cap: .round),
]

private let speechBubbleShapes: [ItundaFaceShape] = [
    .filledPath(
        d: "M10,14 H62 C64.2,14 66,15.8 66,18 V46 C66,48.2 64.2,50 62,50 H32 L18,62 V50 H10 " +
            "C7.8,50 6,48.2 6,46 V18 C6,15.8 7.8,14 10,14 Z",
        color: Color(hex: 0x483EB6)
    ),
    .filledCircle(cx: 24, cy: 32, r: 3.4, color: Color(hex: 0xC0C6FF)),
    .filledCircle(cx: 36, cy: 32, r: 3.4, color: Color(hex: 0xC0C6FF)),
    .filledCircle(cx: 48, cy: 32, r: 3.4, color: Color(hex: 0xC0C6FF)),
]

private let pinShapes: [ItundaFaceShape] = [
    .filledPath(d: "M36,8 C24,8 16,16.5 16,27.5 C16,42 36,66 36,66 C36,66 56,42 56,27.5 C56,16.5 48,8 36,8 Z", color: Color(hex: 0xE0455C)),
    .filledCircle(cx: 36, cy: 27, r: 9, color: Color(hex: 0xFFD7DC)),
]

private let moneyBagShapes: [ItundaFaceShape] = [
    .strokedPath(d: "M28,14 C28,9 31.6,5 36,5 C40.4,5 44,9 44,14", color: Color(hex: 0x483EB6), width: 3.2, cap: .round),
    .filledPath(d: "M22,17 H50 L57,45 C59,53 51,62 42,62 H30 C21,62 13,53 15,45 Z", color: Color(hex: 0x483EB6)),
    .strokedCircle(cx: 36, cy: 40, r: 8, color: Color(hex: 0xC0C6FF), width: 2.6),
    .strokedLine(x1: 36, y1: 34.5, x2: 36, y2: 45.5, color: Color(hex: 0xC0C6FF), width: 2.6, cap: .round),
]

private let shoppingBagShapes: [ItundaFaceShape] = [
    .filledRect(x: 14, y: 28, w: 44, h: 36, rx: 4, color: Color(hex: 0xE0455C)),
    .strokedPath(d: "M22,28 V20 C22,14.5 26.5,10 32,10", color: Color(hex: 0xE0455C), width: 4, cap: .round),
    .strokedPath(d: "M50,28 V20 C50,14.5 45.5,10 40,10", color: Color(hex: 0xE0455C), width: 4, cap: .round),
    .strokedLine(x1: 14, y1: 38, x2: 58, y2: 38, color: Color(hex: 0xFFD7DC), width: 2.6, alpha: 0.8),
]

private let priceDropShapes: [ItundaFaceShape] = [
    .filledPath(d: "M36,52 L14,20 H58 Z", color: Color(hex: 0xE0455C)),
    .filledRect(x: 14, y: 58, w: 44, h: 6, rx: 3, color: Color(hex: 0xE0455C)),
]

private let linkShapes: [ItundaFaceShape] = [
    .strokedEllipse(cx: 24, cy: 48, rx: 17, ry: 11, color: Color(hex: 0x5C55D8), width: 6.4),
    .strokedEllipse(cx: 48, cy: 24, rx: 17, ry: 11, color: Color(hex: 0x483EB6), width: 6.4),
]

public struct PackageGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 24, shapes: packageShapes) }
}

public struct FlameGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 24, shapes: flameShapes) }
}

public struct BikeGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 24, shapes: bikeShapes) }
}

public struct ElectricBikeGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 24, shapes: electricBikeShapes) }
}

public struct BikeTypeGlyph: View {
    let electric: Bool
    public var size: CGFloat = 24
    public init(electric: Bool, size: CGFloat = 24) {
        self.electric = electric
        self.size = size
    }
    public var body: some View {
        if electric { ElectricBikeGlyph(size: size) } else { BikeGlyph(size: size) }
    }
}

public struct CameraGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: cameraShapes) }
}

public struct ClockGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: clockShapes) }
}

public struct SpeechBubbleGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: speechBubbleShapes) }
}

public struct PinGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: pinShapes) }
}

public struct MoneyBagGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: moneyBagShapes) }
}

public struct ShoppingBagGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: shoppingBagShapes) }
}

public struct PriceDropGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: priceDropShapes) }
}

public struct LinkGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: linkShapes) }
}
