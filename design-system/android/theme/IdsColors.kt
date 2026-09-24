package rw.itunda.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Toss-Style Color System
 * Focuses on high-contrast blues, clean grays, and semantic clarity.
 *
 * Full numbered scale corrected/completed 2026-07-13 against Toss's own official,
 * directly-fetched TDS docs (tossmini-docs.toss.im/tds-mobile/foundation/colors) --
 * previously unverified against a real source (docs/ARCHITECTURE.md's own §6 item 6
 * had explicitly noted TDS's non-color token surface was never confirmed sourced;
 * this closes that gap for typography too, see IdsTypography.kt). Found a real,
 * live mislabeling this pass: the old `Blue600`/`Blue100` constants held the real
 * TDS's blue700/blue50 values respectively, not blue600/blue100 -- both platforms
 * (this file and ios/.../IdsTheme.swift) had independently drifted the same way,
 * so it was internally consistent, just numbered wrong relative to the real scale.
 * Kept the old names as deprecated aliases (same values) rather than a silent
 * rename, since call sites reference them by name; new code should use the
 * correctly-numbered constants below.
 */
object IdsColors {
    // GENERATED:BEGIN -- do not hand-edit; regenerate with packages/design-tokens/generate-tokens.js from tokens.json (see doc comment above).
    val Grey50 = Color(0xFFF9FAFB)
    val Grey100 = Color(0xFFF2F4F6)
    val Grey200 = Color(0xFFE5E8EB)
    val Grey300 = Color(0xFFD1D6DB)
    val Grey400 = Color(0xFFB0B8C1)
    val Grey500 = Color(0xFF8B95A1)
    val Grey600 = Color(0xFF6B7684)
    val Grey700 = Color(0xFF4E5968)
    val Grey800 = Color(0xFF333D4B)
    val Grey900 = Color(0xFF191F28)

    val Blue50 = Color(0xFFF5FAFF)
    val Blue100 = Color(0xFFE9F3FF)
    val Blue200 = Color(0xFFD1E6FF)
    val Blue300 = Color(0xFF64A8FF)
    val Blue400 = Color(0xFF4593FC)
    val Blue500 = Color(0xFF3182F6)
    val Blue600 = Color(0xFF2272EB)
    val Blue700 = Color(0xFF1B64DA)
    val Blue800 = Color(0xFF1957C2)
    val Blue900 = Color(0xFF194AA6)

    val Red50 = Color(0xFFFFF7F7)
    val Red100 = Color(0xFFFFEDEE)
    val Red200 = Color(0xFFFFDADC)
    val Red300 = Color(0xFFFB8890)
    val Red400 = Color(0xFFF66570)
    val Red500 = Color(0xFFF04452)
    val Red600 = Color(0xFFE42939)
    val Red700 = Color(0xFFD22030)
    val Red800 = Color(0xFFBC1B2A)
    val Red900 = Color(0xFFA51926)

    val Green500 = Color(0xFF04C065)
    val White = Color(0xFFFFFFFF)
    // GENERATED:END

    // Semantic aliases used across existing screens -- kept for source compatibility.
    val Gray900 = Grey900 // Primary text
    val Gray800 = Grey800 // Secondary text
    val Gray700 = Grey700 // Tertiary text
    val Gray600 = Grey600 // Placeholder
    val Gray500 = Grey500
    val Gray400 = Grey400 // Disabled elements
    val Gray300 = Grey300 // Borders
    val Gray200 = Grey200 // Divider
    val Gray100 = Grey100 // Background (Cards)
    val Gray50 = Grey50   // Background (Screen)
}
