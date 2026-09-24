package rw.itunda.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Toss-Style Typography
 * Real Pretendard typeface (see Pretendard.kt) -- the plain platform Sans-Serif fallback
 * this comment used to describe as the "ideally" state is now actually replaced.
 */
object IdsTypography {
    // Real typeface fix (2026-08-13) -- see Pretendard.kt's own doc comment for the full
    // sourced account. Was FontFamily.SansSerif (the plain platform system font) despite
    // this file's own header comment naming Pretendard as the intended real typeface since
    // this scale was first written.
    private val defaultFontFamily = PretendardFontFamily

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

    // Title1 corrected 2026-07-21 from 24sp/34sp to 22sp/31sp -- was the one real
    // outlier when checked against both the sourced TDS scale above (nearest steps
    // are Typography2 at 26sp and Typography3 at 22sp, ambiguous by size alone) and
    // iOS's already-shipped equivalent (IDS.Typography.title, IDS.swift:126, is
    // scaledFont(size: 22, relativeTo: .title1)) -- iOS had already landed on 22,
    // which resolves the ambiguity and matches Typography3 exactly, so this snaps
    // Android to match rather than leaving a confirmed cross-platform drift.
    val Title1 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 31.sp
    )

    // Added 2026-07-21: Title1 was previously overloaded for two different roles --
    // screen/section headlines (still Title1) and hero currency amounts (e.g.
    // AgentHomeScreen.kt's TillSummary "RWF {expectedCash}", the single most
    // important number on that screen). iOS already splits these: IDS.Typography.title
    // (22pt) vs IDS.Typography.largeAmount (34pt, IDS.swift:131) -- Android had no
    // equivalent of the second, so hero amounts rendered at the same size as a plain
    // headline. 34sp matches iOS's value; TDS's own published scale doesn't have a
    // native step this large (it tops out at Typography1's 30sp), so this mirrors
    // iOS's own deliberate business decision to give money amounts extra emphasis,
    // not an invented number.
    val LargeAmount = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp
    )

    // lineHeight on the four styles below corrected 2026-07-21 to match the sourced
    // TDS steps above exactly (Typography4/5/6/7) now that a direct audit found their
    // fontSize already matched -- was off by rounding only (28 vs 29, 24 vs 25.5, 22
    // vs 22.5, 18 vs 19.5), not a font-size or design change.
    val Title2 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 29.sp
    )

    val Subtitle1 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 25.5.sp
    )

    val Body1 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.5.sp
    )

    val Body2 = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.5.sp
    )

    val Button = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
}
