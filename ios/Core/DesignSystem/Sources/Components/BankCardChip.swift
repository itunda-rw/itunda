//
//  BankCardChip.swift
//  Real EMV chip + tap-to-pay silhouette (2026-08-26, direct user instruction: "all
//  cards designs should resemble real card") -- shared by every card-shaped visual
//  in the app (AccountCardCarousel's account tiles, CardScreenView's issued-card
//  thumbnail, its pre-issuance mockup) so the metal-chip look and contactless mark
//  stay one real View, not local forks per screen. Matches web's BankCardChip.tsx
//  and Android's BankCardChip.kt (both added the same pass). Lives in
//  Components (not ItundaFace) because it's a gradient-shaded flat-icon-style
//  component, not a hand-drawn flat-solid-color glyph -- the gold metallic
//  gradient wouldn't fit ItundaFace's flat convention. Gold gradient + a
//  contact-pad grid is the standard flat-icon convention for "this is a chip
//  card" every major bank app uses; the rotated `wifi` SF Symbol is the same
//  widely-used tap-to-pay substitute (no card network's actual trademarked mark
//  is reproduced).
//

import SwiftUI

public struct BankCardChip: View {
    public var size: CGFloat = 32
    public init(size: CGFloat = 32) { self.size = size }

    public var body: some View {
        let height = size * 0.76
        ZStack {
            LinearGradient(
                colors: [Color(hex: 0xF6E7B4), Color(hex: 0xD9B36C), Color(hex: 0xC89A4E)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            Rectangle().fill(Color.black.opacity(0.22)).frame(width: 1)
            Rectangle().fill(Color.black.opacity(0.22)).frame(height: 1)
        }
        .frame(width: size, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 5))
    }
}

public struct CardContactlessGlyph: View {
    public var size: CGFloat = 18
    public var color: Color = .white.opacity(0.85)
    public init(size: CGFloat = 18, color: Color = .white.opacity(0.85)) {
        self.size = size
        self.color = color
    }

    public var body: some View {
        Image(systemName: "wifi")
            .font(.system(size: size, weight: .semibold))
            .foregroundColor(color)
            .rotationEffect(.degrees(90))
    }
}

// Real itunda petal mark (2026-08-27) -- the exact same geometry as
// ic_launcher_foreground.xml / bank-mfe's public/favicon.svg / Android's own new
// PetalMark composable (BankCardChip.kt, same pass), flattened to one silhouette
// (right facet's outer curve + left facet's outer curve, sharing the same two real
// endpoints -- the internal seam line is simply not drawn) for legibility at the small
// lockup size CardDesignPicker/CardScreenView's issued-card thumbnail need it at. Not a
// new or reinterpreted mark. Path coordinates are in a 0..100 space (matching the SVG
// source this was ported from), scaled to the view's own frame by PetalMarkShape.
private struct PetalMarkShape: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 100
        let sy = rect.height / 100
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        path.move(to: pt(42, 16))
        path.addCurve(to: pt(56, 88), control1: pt(74, 8), control2: pt(94, 44))
        path.addCurve(to: pt(42, 16), control1: pt(24, 86), control2: pt(8, 48))
        path.closeSubpath()
        return path
    }
}

public struct PetalMark: View {
    public var size: CGFloat = 12
    public var color: Color = .white
    public init(size: CGFloat = 12, color: Color = .white) {
        self.size = size
        self.color = color
    }

    public var body: some View {
        PetalMarkShape().fill(color).frame(width: size, height: size)
    }
}
