package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Real EMV chip + tap-to-pay silhouette (2026-08-26, direct user instruction: "all
// cards designs should resemble real card") -- shared by every card-shaped visual in
// the app (AccountCardCarousel, CardScreen's issued-card thumbnail,
// ProductExplainerScreen's pre-issuance mockup via CardScreen.kt's NO_CARD state) so
// the metal-chip look and contactless mark stay one real component, not local forks
// per screen. Lives in core/designsystem (not the itundaface package) because it's a
// gradient-shaded flat-icon-style component, not a hand-drawn Shape2D glyph -- the
// gold metallic gradient wouldn't fit itundaface's flat-solid-color convention. Gold
// gradient + a contact-pad grid is the standard flat-icon convention for "this is a
// chip card" every major bank app uses; the rotated Wifi glyph is the same
// widely-used tap-to-pay substitute (no card network's actual trademarked mark is
// reproduced).

@Composable
fun BankCardChip(size: Dp = 32.dp) {
    val chipHeight = size * 0.76f
    Box(
        modifier = Modifier
            .size(width = size, height = chipHeight)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF6E7B4), Color(0xFFD9B36C), Color(0xFFC89A4E)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.Black.copy(alpha = 0.22f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.22f)),
        )
    }
}

@Composable
fun CardContactlessGlyph(size: Dp = 18.dp, tint: Color = Color.White.copy(alpha = 0.85f)) {
    Icon(
        imageVector = Icons.Filled.Wifi,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size).rotate(90f),
    )
}
