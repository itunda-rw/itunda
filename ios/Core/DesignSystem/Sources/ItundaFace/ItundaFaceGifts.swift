//
//  ItundaFaceGifts.swift
//  itundaface: gift-theme + split-bill glyphs, ported from bank-mfe's
//  icons/ItundaFaceGifts.tsx / github.com/itunda-rw/itundaface, matching
//  Android's ItundaFaceGifts.kt. Lives in CoreDesignSystem (not one feature
//  module) because the real `giftThemeLabels` source of truth lives in
//  CoreNetwork and is consumed by both the App target's chat screens and
//  FeaturePayments' TransferFlowScreens -- the same cross-module reasoning
//  as Android's equivalent file.
//
//  Naming note: every private shape constant here is prefixed `gift`/
//  `voucher`/`dice` (not a bare word) -- see ItundaFacePlaces.swift's own
//  doc comment for the real `View.badge(_:)` name-collision this avoids.
//

import SwiftUI

private let giftBoxShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0xC0C6FF)),
    .filledRect(x: 14, y: 26, w: 32, h: 22, rx: 2, color: Color(hex: 0x282565)),
    .filledRect(x: 14, y: 20, w: 32, h: 8, rx: 2, color: Color(hex: 0x483EB6)),
    .filledRect(x: 28, y: 20, w: 4, h: 28, rx: 0, color: Color(hex: 0x7C7BFD)),
    .filledPath(d: "M22,20 C16,20 15,12 22,12 C27,12 28,17 28,20 Z", color: Color(hex: 0x7C7BFD)),
    .filledPath(d: "M38,20 C44,20 45,12 38,12 C33,12 32,17 32,20 Z", color: Color(hex: 0x7C7BFD)),
]

private let giftCongratulationsShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0xDCCB8A)),
    .filledPath(d: "M18,44 L36,18 L42,46 Z", color: Color(hex: 0x665400)),
    .filledCircle(cx: 14, cy: 16, r: 2.6, color: Color(hex: 0x665400)),
    .filledCircle(cx: 24, cy: 8, r: 2.2, color: Color(hex: 0x665400)),
    .filledCircle(cx: 36, cy: 8, r: 2.6, color: Color(hex: 0x665400)),
    .filledCircle(cx: 46, cy: 15, r: 2.2, color: Color(hex: 0x665400)),
    .filledCircle(cx: 20, cy: 24, r: 1.8, color: Color(hex: 0x665400)),
]

private let giftHeartfeltShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0xFEB6AA)),
    .filledRect(x: 13, y: 18, w: 34, h: 24, rx: 3, color: Color(hex: 0xA20800)),
    .strokedPath(d: "M13,20 L30,32 L47,20", color: Color(hex: 0xFEB6AA), width: 2.4, cap: .round),
    .filledPath(
        d: "M30,29 C30,29 25,25.5 25,22.3 C25,20 27,18.6 29,19.4 C29.6,19.6 30,20.1 30,20.7 C30,20.1 30.4,19.6 31,19.4 " +
            "C33,18.6 35,20 35,22.3 C35,25.5 30,29 30,29 Z",
        color: Color(hex: 0xFEB6AA)
    ),
]

private let giftGoodLuckShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0xB3D5B9)),
    .filledPath(d: "M30,30 C30,22 24,18 20,22 C16,26 20,32 28,31 Z", color: Color(hex: 0x156631)),
    .filledPath(d: "M30,30 C38,30 42,24 38,20 C34,16 28,20 29,28 Z", color: Color(hex: 0x156631)),
    .filledPath(d: "M30,30 C22,30 18,36 22,40 C26,44 32,40 31,32 Z", color: Color(hex: 0x156631)),
    .filledPath(d: "M30,30 C30,38 36,42 40,38 C44,34 40,28 32,29 Z", color: Color(hex: 0x156631)),
    .strokedLine(x1: 30, y1: 30, x2: 30, y2: 46, color: Color(hex: 0x156631), width: 2.4, cap: .round),
]

private let giftSettleUpShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0xC0CCDD)),
    .filledPath(d: "M18,12 H42 V46 L38,43 L34,46 L30,43 L26,46 L22,43 L18,46 Z", color: Color(hex: 0x253142)),
    .strokedLine(x1: 22, y1: 20, x2: 38, y2: 20, color: Color(hex: 0xC0CCDD), width: 2, cap: .round),
    .strokedLine(x1: 22, y1: 26, x2: 38, y2: 26, color: Color(hex: 0xC0CCDD), width: 2, cap: .round),
    .strokedLine(x1: 22, y1: 32, x2: 34, y2: 32, color: Color(hex: 0xC0CCDD), width: 2, cap: .round),
]

private let splitBillDiceShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0x8BD8D1)),
    .filledRect(x: 14, y: 16, w: 24, h: 24, rx: 5, color: Color(hex: 0xFFFFFF)),
    .filledCircle(cx: 20, cy: 22, r: 2.1, color: Color(hex: 0x00695C)),
    .filledCircle(cx: 32, cy: 22, r: 2.1, color: Color(hex: 0x00695C)),
    .filledCircle(cx: 26, cy: 28, r: 2.1, color: Color(hex: 0x00695C)),
    .filledCircle(cx: 20, cy: 34, r: 2.1, color: Color(hex: 0x00695C)),
    .filledCircle(cx: 32, cy: 34, r: 2.1, color: Color(hex: 0x00695C)),
    .filledRect(x: 26, y: 26, w: 24, h: 24, rx: 5, color: Color(hex: 0x00695C)),
    .filledCircle(cx: 32, cy: 32, r: 2.1, color: Color(hex: 0x8BD8D1)),
    .filledCircle(cx: 44, cy: 32, r: 2.1, color: Color(hex: 0x8BD8D1)),
    .filledCircle(cx: 32, cy: 44, r: 2.1, color: Color(hex: 0x8BD8D1)),
    .filledCircle(cx: 44, cy: 44, r: 2.1, color: Color(hex: 0x8BD8D1)),
]

private let voucherTicketShapes: [ItundaFaceShape] = [
    .filledCircle(cx: 30, cy: 30, r: 28, color: Color(hex: 0x97D5F5)),
    .filledPath(
        d: "M14,24 C14,21.8 15.8,20 18,20 H42 C44.2,20 46,21.8 46,24 V26 C44.3,26 43,27.3 43,29 C43,30.7 44.3,32 46,32 " +
            "V36 C46,38.2 44.2,40 42,40 H18 C15.8,40 14,38.2 14,36 V32 C15.7,32 17,30.7 17,29 C17,27.3 15.7,26 14,26 Z",
        color: Color(hex: 0x005D7F)
    ),
    .strokedLine(x1: 30, y1: 24, x2: 30, y2: 36, color: Color(hex: 0x97D5F5), width: 2, cap: .round, dashOn: 2.5, dashOff: 2.5),
]

private let itundaFaceGiftThemes: [String: [ItundaFaceShape]] = [
    "CONGRATULATIONS": giftCongratulationsShapes,
    "HEARTFELT": giftHeartfeltShapes,
    "GOOD_LUCK": giftGoodLuckShapes,
    "SETTLE_UP": giftSettleUpShapes,
]

public struct GiftBox: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 60, shapes: giftBoxShapes) }
}

public struct VoucherTicket: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 60, shapes: voucherTicketShapes) }
}

public struct SplitBillDice: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 60, shapes: splitBillDiceShapes) }
}

/// Renders itundaface's own glyph for a gift's theme, falling back to the plain
/// (untinted) gift box for a themeless/nil gift -- mirrors giftThemeLabels' own
/// `gift.theme.flatMap { ... } ?? "🎁"` fallback pattern.
public struct GiftThemeGlyph: View {
    let theme: String?
    public var size: CGFloat = 24
    public init(theme: String?, size: CGFloat = 24) {
        self.theme = theme
        self.size = size
    }
    public var body: some View {
        if let theme, let shapes = itundaFaceGiftThemes[theme] {
            ItundaFaceGlyphCanvas(size: size, viewBoxSize: 60, shapes: shapes)
        } else {
            GiftBox(size: size)
        }
    }
}
