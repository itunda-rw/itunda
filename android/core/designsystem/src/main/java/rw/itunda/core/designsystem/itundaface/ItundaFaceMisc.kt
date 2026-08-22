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

@Composable fun PackageGlyph(size: Dp = 24.dp, modifier: Modifier = Modifier) = ItundaFaceGlyphCanvas(size, 24f, packageShapes, modifier)
