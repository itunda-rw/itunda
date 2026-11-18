package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

@Composable
internal fun rememberPayPalette(): PayPalette = payPalette(false, MaterialTheme.colorScheme.background.luminance() < 0.5f)

/**
 * Prototype Pay sub-screen frame: 70dp bar (48dp back button at x6,y16, 24dp arrow, optional 20sp title at x58),
 * then [content]; [cta] is pinned to the bottom. Colours come from [PayPalette], type from [PayTextScope].
 */
@Composable
internal fun PaySubScreen(
    onBack: () -> Unit, title: String? = null, cta: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.(PayPalette) -> Unit,
) {
    val p = rememberPayPalette()
    PayTextScope {
        Column(Modifier.fillMaxSize().background(p.page)) {
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                Box(Modifier.padding(start = 6.dp, top = 16.dp).size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.size(24.dp), tint = p.text)
                }
                if (title != null) {
                    Text(title, color = p.text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, modifier = Modifier.padding(start = 58.dp, top = 26.dp))
                }
            }
            content(p)
            cta?.invoke(this)
        }
    }
}

/** Prototype `.btn`: brand fill with dark text, or the soft-brand disabled state. */
@Composable
internal fun PayBtn(
    label: String, p: PayPalette, enabled: Boolean = true, height: Dp = 58.dp, radius: Dp = 18.dp, size: Int = 18, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(radius)).background(if (enabled) p.brand else p.tint)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Color(0xFF17171C) else p.tintFg.copy(alpha = 0.6f), fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02 * size).sp) }
}
