package rw.itunda.core.designsystem.itundaface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// itundaface: gift-theme + split-bill glyphs (2026-08-22), ported from
// bank-mfe's icons/ItundaFaceGifts.tsx / github.com/itunda-rw/itundaface's
// svg/gifts/*.svg. Lives in core/designsystem (not talk/impl) because the
// real GIFT_THEME_LABELS source of truth lives in core/network and is
// consumed by BOTH talk/impl (chat gift bubbles/composer) and payments/impl
// (TransferFlow's gift-theme picker) -- two separate Konsist-enforced
// feature modules, so the glyphs need a common home both already depend on,
// same reasoning as ItundaFaceGlyphCanvas's own promotion earlier this pass.
//
// Every path's "d" data is copy-pasted byte-identical from the real,
// already-shipped web SVGs. Compile-verified only this pass; no physical
// device was connected this session.

private val giftBoxShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFFC0C6FF),
    Shape2D.FilledRect(14f, 26f, 32f, 22f, 2f, 0xFF282565),
    Shape2D.FilledRect(14f, 20f, 32f, 8f, 2f, 0xFF483EB6),
    Shape2D.FilledRect(28f, 20f, 4f, 28f, 0f, 0xFF7C7BFD),
    Shape2D.FilledPath("M22,20 C16,20 15,12 22,12 C27,12 28,17 28,20 Z", 0xFF7C7BFD),
    Shape2D.FilledPath("M38,20 C44,20 45,12 38,12 C33,12 32,17 32,20 Z", 0xFF7C7BFD),
)

private val giftCongratulationsShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFFDCCB8A),
    Shape2D.FilledPath("M18,44 L36,18 L42,46 Z", 0xFF665400),
    Shape2D.FilledCircle(14f, 16f, 2.6f, 0xFF665400),
    Shape2D.FilledCircle(24f, 8f, 2.2f, 0xFF665400),
    Shape2D.FilledCircle(36f, 8f, 2.6f, 0xFF665400),
    Shape2D.FilledCircle(46f, 15f, 2.2f, 0xFF665400),
    Shape2D.FilledCircle(20f, 24f, 1.8f, 0xFF665400),
)

private val giftHeartfeltShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFFFEB6AA),
    Shape2D.FilledRect(13f, 18f, 34f, 24f, 3f, 0xFFA20800),
    Shape2D.StrokedPath("M13,20 L30,32 L47,20", 0xFFFEB6AA, 2.4f, StrokeCap.Round),
    Shape2D.FilledPath(
        "M30,29 C30,29 25,25.5 25,22.3 C25,20 27,18.6 29,19.4 C29.6,19.6 30,20.1 30,20.7 C30,20.1 30.4,19.6 31,19.4 " +
            "C33,18.6 35,20 35,22.3 C35,25.5 30,29 30,29 Z",
        0xFFFEB6AA,
    ),
)

private val giftGoodLuckShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFFB3D5B9),
    Shape2D.FilledPath("M30,30 C30,22 24,18 20,22 C16,26 20,32 28,31 Z", 0xFF156631),
    Shape2D.FilledPath("M30,30 C38,30 42,24 38,20 C34,16 28,20 29,28 Z", 0xFF156631),
    Shape2D.FilledPath("M30,30 C22,30 18,36 22,40 C26,44 32,40 31,32 Z", 0xFF156631),
    Shape2D.FilledPath("M30,30 C30,38 36,42 40,38 C44,34 40,28 32,29 Z", 0xFF156631),
    Shape2D.StrokedLine(30f, 30f, 30f, 46f, 0xFF156631, 2.4f, StrokeCap.Round),
)

private val giftSettleUpShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFFC0CCDD),
    Shape2D.FilledPath("M18,12 H42 V46 L38,43 L34,46 L30,43 L26,46 L22,43 L18,46 Z", 0xFF253142),
    Shape2D.StrokedLine(22f, 20f, 38f, 20f, 0xFFC0CCDD, 2f, StrokeCap.Round),
    Shape2D.StrokedLine(22f, 26f, 38f, 26f, 0xFFC0CCDD, 2f, StrokeCap.Round),
    Shape2D.StrokedLine(22f, 32f, 34f, 32f, 0xFFC0CCDD, 2f, StrokeCap.Round),
)

private val splitBillDiceShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFF8BD8D1),
    Shape2D.FilledRect(14f, 16f, 24f, 24f, 5f, 0xFFFFFFFF),
    Shape2D.FilledCircle(20f, 22f, 2.1f, 0xFF00695C),
    Shape2D.FilledCircle(32f, 22f, 2.1f, 0xFF00695C),
    Shape2D.FilledCircle(26f, 28f, 2.1f, 0xFF00695C),
    Shape2D.FilledCircle(20f, 34f, 2.1f, 0xFF00695C),
    Shape2D.FilledCircle(32f, 34f, 2.1f, 0xFF00695C),
    Shape2D.FilledRect(26f, 26f, 24f, 24f, 5f, 0xFF00695C),
    Shape2D.FilledCircle(32f, 32f, 2.1f, 0xFF8BD8D1),
    Shape2D.FilledCircle(44f, 32f, 2.1f, 0xFF8BD8D1),
    Shape2D.FilledCircle(32f, 44f, 2.1f, 0xFF8BD8D1),
    Shape2D.FilledCircle(44f, 44f, 2.1f, 0xFF8BD8D1),
)

private val voucherTicketShapes = listOf(
    Shape2D.FilledCircle(30f, 30f, 28f, 0xFF97D5F5),
    Shape2D.FilledPath(
        "M14,24 C14,21.8 15.8,20 18,20 H42 C44.2,20 46,21.8 46,24 V26 C44.3,26 43,27.3 43,29 C43,30.7 44.3,32 46,32 " +
            "V36 C46,38.2 44.2,40 42,40 H18 C15.8,40 14,38.2 14,36 V32 C15.7,32 17,30.7 17,29 C17,27.3 15.7,26 14,26 Z",
        0xFF005D7F,
    ),
    Shape2D.StrokedLine(30f, 24f, 30f, 36f, 0xFF97D5F5, 2f, StrokeCap.Round, dashOn = 2.5f, dashOff = 2.5f),
)

@Composable fun GiftBox(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, giftBoxShapes, modifier)
@Composable fun GiftCongratulations(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, giftCongratulationsShapes, modifier)
@Composable fun GiftHeartfelt(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, giftHeartfeltShapes, modifier)
@Composable fun GiftGoodLuck(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, giftGoodLuckShapes, modifier)
@Composable fun GiftSettleUp(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, giftSettleUpShapes, modifier)
@Composable fun SplitBillDice(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, splitBillDiceShapes, modifier)
@Composable fun VoucherTicket(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 60f, voucherTicketShapes, modifier)

private val ITUNDAFACE_GIFT_THEMES: Map<String, List<Shape2D>> = mapOf(
    "CONGRATULATIONS" to giftCongratulationsShapes,
    "HEARTFELT" to giftHeartfeltShapes,
    "GOOD_LUCK" to giftGoodLuckShapes,
    "SETTLE_UP" to giftSettleUpShapes,
)

/** Renders itundaface's own glyph for a gift's theme, falling back to the plain
 * (untinted) gift box for a themeless/null gift -- mirrors GIFT_THEME_LABELS'
 * own `gift.theme?.let { ... } ?: "🎁"` fallback pattern. */
@Composable
fun GiftThemeGlyph(theme: String?, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val shapes = theme?.let { ITUNDAFACE_GIFT_THEMES[it] } ?: giftBoxShapes
    ItundaFaceGlyphCanvas(size, 60f, shapes, modifier)
}

@Composable fun DiceGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = SplitBillDice(size, modifier)
