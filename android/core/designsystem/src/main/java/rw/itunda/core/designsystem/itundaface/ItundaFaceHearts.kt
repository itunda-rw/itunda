package rw.itunda.core.designsystem.itundaface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// itundaface: like/wishlist heart-toggle glyphs (2026-08-22), ported from
// bank-mfe's icons/ItundaFaceHearts.tsx / github.com/itunda-rw/itundaface.
// Lives in core/designsystem, not any one feature module -- the real usage
// (Marketplace/Jobs/RealEstate/Eats-favorites/Commerce) spans 5+ separate
// Konsist-enforced feature modules, the widest-reaching itundaface group so
// far on Android.
//
// Deliberately the SAME shared-seam heart silhouette as ReactionHeart in
// talk/impl's ItundaFaceReactions.kt (identical path data, identical
// #ef4a63/#c72e4c two-facet palette) -- itundaface's own real rule is the
// same heart everywhere a heart means the same thing, not a redrawn one per
// feature. The outline (unfavorited) state traces the exact same silhouette
// as a single stroke with no internal seam, so toggling reads as one glyph
// filling in, not two unrelated icons swapping. This also replaces every
// screen's existing Icons.Filled.Favorite/Icons.Outlined.FavoriteBorder
// Material heart with itundaface's own, for the same "one heart" consistency
// -- not just the raw ♡ Unicode text labels.
//
// Compile-verified only this pass; no physical device was connected this
// session.

private val heartFilledShapes = listOf(
    Shape2D.FilledPath("M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 L40,18.4 Z", 0xFFEF4A63),
    Shape2D.FilledPath("M40,18.4 C38,14.4 33.6,10 26.6,10 C17.1,10 8,17.7 8,29.6 C8,52.6 40,74 40,74 L40,18.4 Z", 0xFFC72E4C),
)

private val heartOutlineShapes = listOf(
    Shape2D.StrokedPath(
        "M40,18.4 C42,14.4 46.4,10 53.4,10 C62.9,10 72,17.7 72,29.6 C72,52.6 40,74 40,74 " +
            "C40,74 8,52.6 8,29.6 C8,17.7 17.1,10 26.6,10 C33.6,10 38,14.4 40,18.4 Z",
        0xFF9099A8,
        5f,
        join = StrokeJoin.Round,
    ),
)

@Composable fun HeartFilled(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, heartFilledShapes, modifier)
@Composable fun HeartOutline(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 80f, heartOutlineShapes, modifier)

/** Renders itundaface's own heart toggle for a given favorited state --
 * drop-in swap for Icons.Filled.Favorite/Icons.Outlined.FavoriteBorder or a
 * plain ♥/♡ pair. */
@Composable
fun WishlistHeart(favorited: Boolean, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    if (favorited) HeartFilled(size, modifier) else HeartOutline(size, modifier)
}
