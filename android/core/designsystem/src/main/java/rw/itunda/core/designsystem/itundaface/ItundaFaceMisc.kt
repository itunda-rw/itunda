package rw.itunda.core.designsystem.itundaface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// itundaface: misc single-use glyphs (2026-08-22), ported from bank-mfe's
// icons/ItundaFaceMisc.tsx / github.com/itunda-rw/itundaface's svg/misc/*.svg.
// Lives in core/designsystem since real usage is scattered across several
// feature modules (same reasoning as gifts/hearts/security). Starts with
// PackageGlyph (Marketplace escrow delivery address); the rest of the web
// misc tail (flame/bike/camera/clock/etc.) follows in later passes.
// Compile-verified only this pass; no physical device was connected this
// session.

private val packageShapes = listOf(
    Shape2D.FilledPath("M12,2.5 L21,7 V17 L12,21.5 L3,17 V7 Z", 0xFFC8935A),
    Shape2D.FilledPath("M12,2.5 L21,7 L12,11.5 L3,7 Z", 0xFFE0AC74),
    Shape2D.StrokedPath("M12,11.5 V21.5", 0xFF8A5F33, 1.2f),
    Shape2D.StrokedPath("M7.5,4.7 L16.5,9.2", 0xFF8A5F33, 1.4f, StrokeCap.Round),
)

private val flameShapes = listOf(
    Shape2D.FilledPath(
        "M12,22 C7.5,22 5,18.8 5,15.2 C5,11.5 7.4,8.6 8.4,5.6 C8.7,4.7 9.7,4.6 10.1,5.4 C10.9,7 11,8.8 12,9 " +
            "C13,8.3 12.8,5.2 12.2,3.2 C11.9,2.2 12.8,1.5 13.6,2.1 C17.4,4.9 19,9.5 19,13.5 C19,18.5 16,22 12,22 Z",
        0xFFF0740A,
    ),
    Shape2D.FilledPath(
        "M12,20 C9.5,20 8,18 8,15.8 C8,13.8 9.3,12.3 10.2,10.9 C10.6,10.3 11.4,10.5 11.5,11.2 C11.6,12.2 12.1,12.6 12.6,12.2 " +
            "C13.1,11.8 13,10.4 12.7,9.4 C12.5,8.7 13.2,8.2 13.8,8.6 C15.6,9.9 16.5,12.1 16.5,14.2 C16.5,17.6 14.6,20 12,20 Z",
        0xFFFFB02E,
    ),
)

private val bikeShapes = listOf(
    Shape2D.StrokedCircle(5.5f, 17f, 4f, 0xFF0A8A72, 1.8f),
    Shape2D.StrokedCircle(18.5f, 17f, 4f, 0xFF0A8A72, 1.8f),
    Shape2D.StrokedPath("M5.5,17 L10,8 H15 M10,8 L13,13 M13,13 L18.5,17 M13,13 L8.5,17", 0xFF0A8A72, 1.8f, StrokeCap.Round),
    Shape2D.FilledCircle(15f, 8f, 1.3f, 0xFF0A8A72),
    Shape2D.StrokedPath("M9,8 H11.4", 0xFF0A8A72, 1.8f, StrokeCap.Round),
)

private val electricBikeShapes = bikeShapes + listOf(
    Shape2D.FilledCircle(18.5f, 6f, 5.6f, 0xFFFFB02E),
    Shape2D.FilledPath("M19.6,2.4 L16.8,6.6 H18.7 L17.9,9.6 L21,5.2 H19 Z", 0xFF7A5300),
)

private val cameraShapes = listOf(
    Shape2D.FilledPath(
        "M10,22 H26 L30,14 H42 L46,22 H62 C64.2,22 66,23.8 66,26 V58 C66,60.2 64.2,62 62,62 H10 " +
            "C7.8,62 6,60.2 6,58 V26 C6,23.8 7.8,22 10,22 Z",
        0xFF415676,
    ),
    Shape2D.FilledCircle(36f, 42f, 14f, 0xFFC0CCDD),
    Shape2D.FilledCircle(36f, 42f, 8f, 0xFF415676),
    Shape2D.FilledCircle(56f, 30f, 2.6f, 0xFFC0CCDD),
)

private val clockShapes = listOf(
    Shape2D.StrokedCircle(36f, 36f, 27f, 0xFF415676, 4.4f),
    Shape2D.StrokedPath("M36,20 V37 L48,45", 0xFF415676, 4.4f, StrokeCap.Round),
)

private val speechBubbleShapes = listOf(
    Shape2D.FilledPath(
        "M10,14 H62 C64.2,14 66,15.8 66,18 V46 C66,48.2 64.2,50 62,50 H32 L18,62 V50 H10 " +
            "C7.8,50 6,48.2 6,46 V18 C6,15.8 7.8,14 10,14 Z",
        0xFF483EB6,
    ),
    Shape2D.FilledCircle(24f, 32f, 3.4f, 0xFFC0C6FF),
    Shape2D.FilledCircle(36f, 32f, 3.4f, 0xFFC0C6FF),
    Shape2D.FilledCircle(48f, 32f, 3.4f, 0xFFC0C6FF),
)

private val pinShapes = listOf(
    Shape2D.FilledPath("M36,8 C24,8 16,16.5 16,27.5 C16,42 36,66 36,66 C36,66 56,42 56,27.5 C56,16.5 48,8 36,8 Z", 0xFFE0455C),
    Shape2D.FilledCircle(36f, 27f, 9f, 0xFFFFD7DC),
)

private val moneyBagShapes = listOf(
    Shape2D.StrokedPath("M28,14 C28,9 31.6,5 36,5 C40.4,5 44,9 44,14", 0xFF483EB6, 3.2f, StrokeCap.Round),
    Shape2D.FilledPath("M22,17 H50 L57,45 C59,53 51,62 42,62 H30 C21,62 13,53 15,45 Z", 0xFF483EB6),
    Shape2D.StrokedCircle(36f, 40f, 8f, 0xFFC0C6FF, 2.6f),
    Shape2D.StrokedLine(36f, 34.5f, 36f, 45.5f, 0xFFC0C6FF, 2.6f, StrokeCap.Round),
)

private val shoppingBagShapes = listOf(
    Shape2D.FilledRect(14f, 28f, 44f, 36f, 4f, 0xFFE0455C),
    Shape2D.StrokedPath("M22,28 V20 C22,14.5 26.5,10 32,10", 0xFFE0455C, 4f, StrokeCap.Round),
    Shape2D.StrokedPath("M50,28 V20 C50,14.5 45.5,10 40,10", 0xFFE0455C, 4f, StrokeCap.Round),
    Shape2D.StrokedLine(14f, 38f, 58f, 38f, 0xFFFFD7DC, 2.6f, alpha = 0.8f),
)

private val priceDropShapes = listOf(
    Shape2D.FilledPath("M36,52 L14,20 H58 Z", 0xFFE0455C),
    Shape2D.FilledRect(14f, 58f, 44f, 6f, 3f, 0xFFE0455C),
)

private val linkShapes = listOf(
    Shape2D.StrokedEllipse(24f, 48f, 17f, 11f, 0xFF5C55D8, 6.4f),
    Shape2D.StrokedEllipse(48f, 24f, 17f, 11f, 0xFF483EB6, 6.4f),
)

private val bellShapes = listOf(
    Shape2D.FilledPath("M36,10 C25,10 18,18 18,29 V42 L12,52 H60 L54,42 V29 C54,18 47,10 36,10 Z", 0xFFDCCB8A),
    Shape2D.FilledPath("M28,54 C28,60 31.5,64 36,64 C40.5,64 44,60 44,54 Z", 0xFF665400),
)

private val bellMutedShapes = listOf(
    Shape2D.FilledPath("M36,10 C25,10 18,18 18,29 V42 L12,52 H60 L54,42 V29 C54,18 47,10 36,10 Z", 0xFF9099A8),
    Shape2D.FilledPath("M28,54 C28,60 31.5,64 36,64 C40.5,64 44,60 44,54 Z", 0xFF6A7280),
)

@Composable fun PackageGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 24f, packageShapes, modifier)
@Composable fun FlameGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 24f, flameShapes, modifier)
@Composable fun BikeGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 24f, bikeShapes, modifier)
@Composable fun ElectricBikeGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 24f, electricBikeShapes, modifier)
@Composable fun BikeTypeGlyph(electric: Boolean, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    if (electric) ElectricBikeGlyph(size, modifier) else BikeGlyph(size, modifier)
}
@Composable fun CameraGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, cameraShapes, modifier)
@Composable fun ClockGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, clockShapes, modifier)
@Composable fun SpeechBubbleGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, speechBubbleShapes, modifier)
@Composable fun PinGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, pinShapes, modifier)
@Composable fun MoneyBagGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, moneyBagShapes, modifier)
@Composable fun ShoppingBagGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, shoppingBagShapes, modifier)
@Composable fun PriceDropGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, priceDropShapes, modifier)
@Composable fun LinkGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, linkShapes, modifier)
@Composable fun BellGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, bellShapes, modifier)
@Composable fun BellMutedGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, bellMutedShapes, modifier)
@Composable fun FollowBellGlyph(followed: Boolean, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    if (followed) BellGlyph(size, modifier) else BellMutedGlyph(size, modifier)
}
