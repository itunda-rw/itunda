package rw.itunda.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import rw.itunda.core.designsystem.R

/**
 * Toss's real, open-source emoji font (github.com/toss/tossface, v1.6.1),
 * bundled from the actual GitHub release asset (TossFaceFontMac.ttf), not
 * approximated. Covers the full Unicode v14 emoji set with Toss's own flat,
 * minimal-at-small-sizes design instead of the platform's native emoji.
 *
 * License: see core/designsystem/TOSSFACE_LICENSE.txt (bundled per the
 * license's condition 2 -- redistribution requires including the original
 * copyright/license text alongside the font).
 *
 * Use for decorative/expressive emoji in copy (celebratory moments, reward
 * confirmations) -- not a replacement for functional UI icons, which stay
 * as real Material icons (see the rest of this package).
 */
val TossFaceFontFamily = FontFamily(
    Font(R.font.tossface)
)
