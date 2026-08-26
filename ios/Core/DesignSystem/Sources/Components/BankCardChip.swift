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
