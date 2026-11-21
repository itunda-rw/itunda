package rw.itunda.feature.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype v1038 Home colours, measured from the rendered HTML (computed styles, light and
 * dark). Home uses several greys and a tile gradient that are not in [Ids.colors], so they
 * live here, local to this screen, instead of drifting the shared tokens.
 */
internal data class HomePalette(
    val text: Color, val mu: Color, val pk: Color, val tt: Color,
    val tint: Color, val tintFg: Color, val lk: Color,
    val tileA: Color, val tileB: Color, val chip: Color, val line: Color,
    val hair: Color, val glow: Color, val footShade: Color, val setFg: Color, val ring: Color,
) {
    val barOn = Color(0xFF8F89FF)
    val logo = Color(0xFF8E919B)
    val bankTitle = Color(0xFFC9CBD2)
    val unread = Color(0xFFD92D20)
    val setBg = Color(0x246E788C)
}

@Composable
internal fun homePalette(): HomePalette = if (Ids.colors.background.luminance() < 0.5f) HomePalette(
    text = Color(0xFFF9FAFB), mu = Color(0xFFB4BBC7), pk = Color(0xFFA9B1BE), tt = Color(0xFF8B95A1),
    tint = Color(0xFF2A2850), tintFg = Color(0xFFB3AEFF), lk = Color(0xFF8F89FF),
    tileA = Color(0xFF2A2850), tileB = Color(0xFF43407C), chip = Color(0xFF2C2C35), line = Color(0xFF3A3A44),
    hair = Color(0xFF29292E), glow = Color(0xFF37355A), footShade = Color(0x12F9FAFB), setFg = Color(0xFF9EA4AE), ring = Color(0xFF3A3A44),
) else HomePalette(
    text = Color(0xFF191F28), mu = Color(0xFF566170), pk = Color(0xFF5F6A78), tt = Color(0xFF6B7684),
    tint = Color(0xFFEEEDFF), tintFg = Color(0xFF5B54D6), lk = Color(0xFF5A51EC),
    tileA = Color(0xFFEEEDFF), tileB = Color(0xFFC9C6FA), chip = Color(0xFFF2F4F6), line = Color(0xFFE5E8EB),
    hair = Color(0xFFE9ECF0), glow = Color(0xFFE0DEFF), footShade = Color(0x0D191F28), setFg = Color(0xFF6B7684), ring = Color(0xFFF3F4F6),
)
