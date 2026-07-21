package rw.itunda.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-reactive semantic tokens -- the fix for a real bug found 2026-07-10:
 * IdsColors, IdsButton, and IdsListRow were all hardcoded constants that never
 * changed between light/dark, even though IdsTheme() built a real Material
 * ColorScheme nothing actually read. IDS.kt (core/designsystem/ids) had zero
 * dark-mode values at all. ItundaAppScreen.kt had a third, ad-hoc set of
 * inline dark-mode colors, hand-tuned but duplicated nowhere else.
 *
 * Light values are the real Toss light palette -- IdsColors' Gray900/Gray700/
 * Gray500/Gray200/Gray100 and the separately-ported IDS.Colors arrived at the
 * same hex values independently, which is why they're used directly below
 * rather than invented.
 *
 * Dark values corrected 2026-07-21, superseding the previous true-black
 * background: the 2026-07-10 fix above was based on eyeballing user-provided
 * screenshots, which can't reliably distinguish true black from a very dark
 * grey (especially compressed/OLED). Toss's actual currently-published
 * `@toss/tds-colors@0.1.0` npm package (registry.npmjs.org, real maintainers
 * incl. toss-build-bot, updated through March 2026) ships raw adaptive dark
 * values directly as CSS custom properties -- `--adaptiveBackground: #17171c`
 * (not black), `--adaptiveBackgroundLevel01: #202027`, `--adaptiveBackgroundLevel02:
 * #2c2c35`, `--adaptiveBlue500: #3485fa`, `--adaptiveHairlineBorder: #3c3c47`.
 * Mapped by role, not guessed: Level01/02 are named elevation steps above the
 * base background, matching this struct's surface/surfaceSoft split exactly.
 * `pressed`/`success`/`warning`/`danger`/the tint colors are deliberately left
 * untouched -- the real package only exposes the raw numbered scale, not which
 * step its own semantic "danger"/"pressed" roles point to, and guessing would
 * repeat the exact mistake this comment is fixing.
 */
data class IdsSemanticColors(
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

val IdsLightSemanticColors = IdsSemanticColors(
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
    successTint = Color(0xFFF5FAFF),
    warning = Color(0xFFFFA000),
    warningTint = Color(0xFFFFF4D6),
    danger = Color(0xFFF04452),
    dangerTint = Color(0xFFFFECEB),
    iconPrimary = Color(0xFF2C3643),
    iconSecondary = Color(0xFF6B7684),
    shadow = Color(0x14000000),
)

val IdsDarkSemanticColors = IdsSemanticColors(
    background = Color(0xFF17171C),
    surface = Color(0xFF202027),
    surfaceSoft = Color(0xFF2C2C35),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF989EAA),
    textTertiary = Color(0xFF575C66),
    brand = Color(0xFF3485FA),
    textBrand = Color(0xFF3485FA),
    divider = Color(0xFF3C3C47),
    chip = Color(0xFF2C2C35),
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

val LocalIdsSemanticColors = staticCompositionLocalOf { IdsLightSemanticColors }

/** Short, ergonomic access point: `Ids.colors.textPrimary`. */
object Ids {
    val colors: IdsSemanticColors
        @Composable get() = LocalIdsSemanticColors.current

    // Added 2026-07-11 -- see IdsLayout.kt's own header for the full reasoning.
    val layout: IdsLayout
        get() = IdsLayout
}
