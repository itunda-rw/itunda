package rw.itunda.feature.eats.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import rw.itunda.core.designsystem.theme.Ids

/** Exact adaptive colors sampled from the v1038 Eats prototype at 3x. */
internal data class EatsPrototypePalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val surfaceSoft: Color,
    val selected: Color,
    val selectedText: Color,
    val accent: Color,
    val divider: Color,
    val accentTint: Color,
)

private val EatsLightPalette = EatsPrototypePalette(
    background = Color(0xFFFFFFFF),
    primary = Color(0xFF191F28),
    secondary = Color(0xFF6B7684),
    surfaceSoft = Color(0xFFF2F4F6),
    selected = Color(0xFF191F28),
    selectedText = Color(0xFFFFFFFF),
    accent = Color(0xFF5B54D6),
    divider = Color(0xFFE5E8EB),
    accentTint = Color(0xFFF0EDFF),
)

private val EatsDarkPalette = EatsPrototypePalette(
    background = Color(0xFF17171C),
    primary = Color(0xFFF9FAFB),
    secondary = Color(0xFF9EA4AE),
    surfaceSoft = Color(0xFF2C2C35),
    selected = Color(0xFFF5F6F7),
    selectedText = Color(0xFF17171C),
    accent = Color(0xFFB3AEFF),
    divider = Color(0xFF3C3C47),
    accentTint = Color(0xFF292644),
)

@Composable
internal fun eatsPrototypePalette(): EatsPrototypePalette =
    if (Ids.colors.background == EatsDarkPalette.background) EatsDarkPalette else EatsLightPalette
