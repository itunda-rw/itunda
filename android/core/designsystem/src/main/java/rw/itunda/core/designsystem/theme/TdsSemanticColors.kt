package rw.itunda.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-reactive semantic tokens -- the fix for a real bug found 2026-07-10:
 * TdsColors, TdsButton, and TdsListRow were all hardcoded constants that never
 * changed between light/dark, even though TdsTheme() built a real Material
 * ColorScheme nothing actually read. IDS.kt (core/designsystem/ids) had zero
 * dark-mode values at all. ItundaAppScreen.kt had a third, ad-hoc set of
 * inline dark-mode colors, hand-tuned but duplicated nowhere else.
 *
 * Light values are the real Toss light palette -- TdsColors' Gray900/Gray700/
 * Gray500/Gray200/Gray100 and the separately-ported IDS.Colors arrived at the
 * same hex values independently, which is why they're used directly below
 * rather than invented. Dark values are the real palette already hand-tuned
 * in ItundaAppScreen.kt (kept for continuity) plus a true-black background,
 * matching the actual Toss app's dark mode (reference: user-provided Toss
 * Bank screenshots, 2026-07-10) rather than TdsDarkColors' previous navy
 * background (#191F28), which didn't match anything.
 */
data class TdsSemanticColors(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val brand: Color,
    val textBrand: Color,
    val divider: Color,
    val chip: Color,
    val pressed: Color,
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val danger: Color,
    val dangerTint: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val shadow: Color,
)

val TdsLightSemanticColors = TdsSemanticColors(
    background = Color(0xFFF2F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF2F4F6),
    textPrimary = Color(0xFF191F28),
    textSecondary = Color(0xFF4E5968),
    textTertiary = Color(0xFF8B95A1),
    brand = Color(0xFF3182F6),
    textBrand = Color(0xFF3182F6),
    divider = Color(0xFFE5E8EB),
    chip = Color(0xFFF2F4F6),
    pressed = Color(0xFFEAF2FF),
    success = Color(0xFF04C065),
    successTint = Color(0xFFE8F3FF),
    warning = Color(0xFFFFA000),
    warningTint = Color(0xFFFFF4D6),
    danger = Color(0xFFF04452),
    dangerTint = Color(0xFFFFECEB),
    iconPrimary = Color(0xFF2C3643),
    iconSecondary = Color(0xFF6B7684),
    shadow = Color(0x14000000),
)

val TdsDarkSemanticColors = TdsSemanticColors(
    background = Color(0xFF000000),
    surface = Color(0xFF17181D),
    surfaceSoft = Color(0xFF23242B),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF989EAA),
    textTertiary = Color(0xFF575C66),
    brand = Color(0xFF4C8FFF),
    textBrand = Color(0xFF4C8FFF),
    divider = Color(0xFF2B2D35),
    chip = Color(0xFF23242B),
    pressed = Color(0xFF1F3053),
    success = Color(0xFF20D394),
    successTint = Color(0xFF10321F),
    warning = Color(0xFFFFC24C),
    warningTint = Color(0xFF3A2E10),
    danger = Color(0xFFFF6B7A),
    dangerTint = Color(0xFF3A1418),
    iconPrimary = Color(0xFFE8EAED),
    iconSecondary = Color(0xFF989EAA),
    shadow = Color(0x40000000),
)

val LocalTdsSemanticColors = staticCompositionLocalOf { TdsLightSemanticColors }

/** Short, ergonomic access point: `Tds.colors.textPrimary`. */
object Tds {
    val colors: TdsSemanticColors
        @Composable get() = LocalTdsSemanticColors.current
}
