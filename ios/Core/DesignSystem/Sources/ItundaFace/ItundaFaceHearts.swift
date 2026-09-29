//
//  ItundaFaceHearts.swift
//  itundaface: like/wishlist heart-toggle glyphs, ported from bank-mfe's
//  icons/ItundaFaceHearts.tsx / github.com/itunda-rw/itundaface, matching
//  Android's ItundaFaceHearts.kt -- the widest-reaching group, since real
//  usage spans Marketplace/Property/Jobs/Eats/Community/My. Moved here from
//  App/Sources (2026-09-02, My Feature-module decomposition) -- MyTabView,
//  now in FeatureMy, needs WishlistHeart, and the 6 other real consumers
//  (Hood/Shop/Eats screens) all stay in :App and already import
//  CoreDesignSystem.
//
//  Deliberately the SAME shared-seam heart silhouette as ItundaFaceReactions
//  .swift's reactionHeartShapes (identical path data, identical
//  #ef4a63/#c72e4c two-facet palette) -- itundaface's own real rule is the
//  same heart everywhere a heart means the same thing, not a redrawn one
//  per feature. Replaces the real `Image(systemName: "heart"/"heart.fill")`
//  SF Symbol itself at every real toggle site, not just raw emoji text.
//

import SwiftUI
import CoreDesignSystem

private let heartFilledShapes: [ItundaFaceShape] = [
    .filledPath(d: "M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 L40,18.4 Z", color: Color(hex: 0xEF4A63)),
    .filledPath(d: "M40,18.4 C38,14.4 33.6,10 26.6,10 C17.1,10 8,17.7 8,29.6 C8,52.6 40,74 40,74 L40,18.4 Z", color: Color(hex: 0xC72E4C)),
]

private let heartOutlineShapes: [ItundaFaceShape] = [
    .strokedPath(
        d: "M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 " +
            "C40,74 8,52.6 8,29.6 C8,17.7 17.1,10 26.6,10 C33.6,10 38,14.4 40,18.4 Z",
        color: Color(hex: 0x9099A8),
        width: 5,
        join: .round
    ),
]

public struct HeartFilled: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: heartFilledShapes) }
}

public struct HeartOutline: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 80, shapes: heartOutlineShapes) }
}

/// Renders itundaface's own heart toggle for a given favorited state --
/// drop-in swap for `Image(systemName: favorited ? "heart.fill" : "heart")`
/// or a plain ♥/♡ pair.
public struct WishlistHeart: View {
    let favorited: Bool
    public var size: CGFloat = 24
    public init(favorited: Bool, size: CGFloat = 24) {
        self.favorited = favorited
        self.size = size
    }
    public var body: some View {
        if favorited { HeartFilled(size: size) } else { HeartOutline(size: size) }
    }
}
