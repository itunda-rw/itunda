//
//  ItundaFaceSecurity.swift
//  itundaface: security/trust glyphs, ported from bank-mfe's
//  icons/ItundaFaceSecurity.tsx / github.com/itunda-rw/itundaface, matching
//  Android's ItundaFaceSecurity.kt -- the 🔒 group (device step-up, escrow
//  payment-held messaging, "Pay via itunda" CTA, frozen-card status) plus
//  🌐 (paired with 🔒 for the map bookmark-folder public/private toggle,
//  same real pairing as web/Android). Lives in CoreDesignSystem since real
//  usage spans the App target AND FeatureMaps.
//

import SwiftUI

private let lockShapes: [ItundaFaceShape] = [
    .strokedPath(d: "M8,10 V7.5 C8,4.5 9.8,2.5 12,2.5 C14.2,2.5 16,4.5 16,7.5 V10", color: Color(hex: 0x483EB6), width: 2.4, cap: .round),
    .filledRect(x: 5.5, y: 10, w: 13, h: 11.5, rx: 3, color: Color(hex: 0x483EB6)),
    .filledCircle(cx: 12, cy: 14.8, r: 1.6, color: Color(hex: 0xC0C6FF)),
    .filledRect(x: 11.1, y: 15.6, w: 1.8, h: 3, rx: 0.9, color: Color(hex: 0xC0C6FF)),
]

private let globeShapes: [ItundaFaceShape] = [
    .strokedCircle(cx: 36, cy: 36, r: 26, color: Color(hex: 0x483EB6), width: 3.2),
    .strokedEllipse(cx: 36, cy: 36, rx: 11, ry: 26, color: Color(hex: 0x483EB6), width: 3.2),
    .strokedLine(x1: 10, y1: 36, x2: 62, y2: 36, color: Color(hex: 0x483EB6), width: 3.2),
    .strokedLine(x1: 14, y1: 22, x2: 58, y2: 22, color: Color(hex: 0x483EB6), width: 2.6),
    .strokedLine(x1: 14, y1: 50, x2: 58, y2: 50, color: Color(hex: 0x483EB6), width: 2.6),
]

public struct LockGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 24, shapes: lockShapes) }
}

public struct GlobeGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 72, shapes: globeShapes) }
}
