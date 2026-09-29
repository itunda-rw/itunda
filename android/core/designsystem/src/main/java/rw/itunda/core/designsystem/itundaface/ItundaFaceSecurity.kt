package rw.itunda.core.designsystem.itundaface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// itundaface: security/trust glyphs (2026-08-22), ported from bank-mfe's
// icons/ItundaFaceSecurity.tsx / github.com/itunda-rw/itundaface -- the 🔒
// group: device step-up verification, escrow payment-held messaging, "Pay
// via itunda" CTA, and frozen-card status. Lives in core/designsystem since
// real usage spans app/, merchantapp/, and 2 feature modules (maps, market-
// place). No badge circle -- renders inline in running text of varying
// context, matching how HeartFilled/HeartOutline already render inline.
// Fixed itunda-indigo, not tintable, same "one palette" rule as every other
// itundaface glyph. Compile-verified only this pass; no physical device was
// connected this session.

private val lockShapes = listOf(
    Shape2D.StrokedPath("M8,10 V7.5 C8,4.5 9.8,2.5 12,2.5 C14.2,2.5 16,4.5 16,7.5 V10", 0xFF483EB6, 2.4f, StrokeCap.Round),
    Shape2D.FilledRect(5.5f, 10f, 13f, 11.5f, 3f, 0xFF483EB6),
    Shape2D.FilledCircle(12f, 14.8f, 1.6f, 0xFFC0C6FF),
    Shape2D.FilledRect(11.1f, 15.6f, 1.8f, 3f, 0.9f, 0xFFC0C6FF),
)

// Ported from ItundaFaceMisc.tsx (web) -- needed here alongside LockGlyph for
// the map bookmark-folder public/private share toggle (Public uses the globe,
// Private uses the lock, same real pairing as web's MapAroundYouSection).
private val globeShapes = listOf(
    Shape2D.StrokedCircle(36f, 36f, 26f, 0xFF483EB6, 3.2f),
    Shape2D.StrokedEllipse(36f, 36f, 11f, 26f, 0xFF483EB6, 3.2f),
    Shape2D.StrokedLine(10f, 36f, 62f, 36f, 0xFF483EB6, 3.2f),
    Shape2D.StrokedLine(14f, 22f, 58f, 22f, 0xFF483EB6, 2.6f),
    Shape2D.StrokedLine(14f, 50f, 58f, 50f, 0xFF483EB6, 2.6f),
)

@Composable fun LockGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 24f, lockShapes, modifier)
@Composable fun GlobeGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 72f, globeShapes, modifier)
