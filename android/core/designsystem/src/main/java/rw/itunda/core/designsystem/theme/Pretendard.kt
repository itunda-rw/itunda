package rw.itunda.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import rw.itunda.core.designsystem.R

/**
 * Real typeface fix (2026-08-13, direct user feedback: "we are still far away from toss") --
 * IdsTypography's own doc comment named this exact gap since it was written ("Ideally mapped
 * to Toss Product Sans or Pretendard if custom font added") and it was never actually closed:
 * every real style below used to render in the plain platform system font
 * (FontFamily.SansSerif -- Roboto on Android), which every other platform in this repo (iOS,
 * web) ALSO turned out to still be doing, confirmed by grep before this fix -- not an
 * Android-only gap.
 *
 * Pretendard (github.com/orioncactus/pretendard, SIL Open Font License 1.1, permits bundling
 * in commercial software) is the real, widely-recognized typeface Korean fintech apps
 * (including ones deliberately matching Toss's own visual register) actually use as a free,
 * open, redistributable stand-in for Toss's own proprietary in-house typeface -- not a guess,
 * this is the same real choice this exact class of app makes in practice. 4 real static weights
 * (Regular/Medium/SemiBold/Bold) covering every FontWeight IdsTypography's own scale actually
 * uses, not the full 9-weight family this repo doesn't need.
 *
 * License: see core/designsystem/PRETENDARD_LICENSE.txt (bundled per the OFL's own
 * redistribution condition).
 */
val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)
