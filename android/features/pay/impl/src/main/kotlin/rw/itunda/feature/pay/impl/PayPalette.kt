package rw.itunda.feature.pay.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype v1038 Pay colours, measured from the rendered HTML (computed styles, light, dark
 * and the always-dark Scan view). Pay uses greys that differ from [Ids.colors] by a few
 * hex steps, so they live here, local to this screen, instead of drifting the shared tokens.
 */
internal data class PayPalette(
    val page: Color, val soft: Color, val text: Color, val tt: Color,
    val brand: Color, val brandText: Color, val tint: Color, val tintFg: Color,
    val title: Color, val en: Color, val gear: Color, val segOff: Color, val segOn: Color,
    val ring: Color, val scanTrack: Color = Color(0xFF34343D), val scanChip: Color = Color(0xFF1E1E24),
) {
    companion object {
        val Brand = Color(0xFF8F89FF)
        val Light = PayPalette(
            page = Color(0xFFFFFFFF), soft = Color(0xFFF2F4F6), text = Color(0xFF191F28), tt = Color(0xFF6B7684),
            brand = Brand, brandText = Color(0xFF5A51EC), tint = Color(0xFFEEEDFF), tintFg = Color(0xFF5B54D6),
            title = Color(0xFF7C8594), en = Color(0xFF6B7684), gear = Color(0xFF6B7684), segOff = Color(0xFF6B7684),
            segOn = Color(0xFF17171C), ring = Color(0x298F89FF),
        )
        val Dark = PayPalette(
            page = Color(0xFF17171C), soft = Color(0xFF2C2C35), text = Color(0xFFF9FAFB), tt = Color(0xFF9EA4AE),
            brand = Brand, brandText = Color(0xFF8F89FF), tint = Color(0xFF2A2850), tintFg = Color(0xFFB3AEFF),
            title = Color(0xFF9099A6), en = Color(0xFFB4BBC7), gear = Color(0xFFB4BBC7), segOff = Color(0xFF9EA4AE),
            segOn = Color(0xFF17171C), ring = Color(0x298F89FF),
        )
        /** Scan is always dark, on a pure-black page. */
        val Scan = Dark.copy(page = Color.Black, soft = Color(0xFF1E1E24), tt = Color(0xFFA0A4AE), segOff = Color(0xFFA0A4AE), segOn = Color.White, en = Color(0xFFB4BBC7))
        val ScanOnDark = Scan.copy(scanTrack = Color(0xFF3A3A44), scanChip = Color(0xFF26262E))
    }
}

internal fun payPalette(scan: Boolean, dark: Boolean): PayPalette =
    if (scan) (if (dark) PayPalette.ScanOnDark else PayPalette.Scan) else if (dark) PayPalette.Dark else PayPalette.Light
