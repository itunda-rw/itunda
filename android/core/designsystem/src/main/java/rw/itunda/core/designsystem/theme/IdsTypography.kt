package rw.itunda.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Toss-Style Typography
 * Uses a clean Sans-Serif font with high legibility.
 * (Ideally mapped to Toss Product Sans or Pretendard if custom font added)
 */
object IdsTypography {
    private val defaultFontFamily = FontFamily.SansSerif

    // Real TDS typography scale (2026-07-13), sourced by directly fetching Toss's own
    // official docs (tossmini-docs.toss.im/tds-mobile/foundation/typography) --
    // previously docs/ARCHITECTURE.md's backlog explicitly said TDS's non-color token
    // surface was never confirmed sourced from anywhere real; this closes that for
    // typography (spacing/elevation genuinely still aren't published, per that same
    // note, and remain itunda's own tuned values -- see IdsLayout.kt). Named
    // Typography1-7 to match the real TDS naming exactly, kept separate from the
    // Title1/Subtitle1/etc. names below rather than replacing their values outright --
    // those are itunda's own established semantic scale, already wired at real call
    // sites across the app, and none of their sizes exactly match a real TDS step
    // (e.g. Title1 was 24sp; the nearest real steps are 22 or 26), so silently
    // snapping them to a different visual size is a real design decision that needs
    // its own live-verified pass, not a byproduct of adding these reference tokens.
    val Typography1 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp)
    val Typography2 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 35.sp)
    val Typography3 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 31.sp)
    val Typography4 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 29.sp)
    val Typography5 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.5.sp)
    val Typography6 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.5.sp)
    val Typography7 = TextStyle(fontFamily = defaultFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp)

    val Title1 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 34.sp
    )
    
    val Title2 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )

    val Subtitle1 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    )

    val Body1 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    val Body2 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    
    val Button = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
}
