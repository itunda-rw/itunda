package rw.itunda.core.designsystem.ids

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ported from mobile_clients/android's rw.itunda.app.DesignSystem.IDS (2026-07-10),
 * reconciled with Tds* on 2026-07-10 (see ARCHITECTURE.md §2/§3 and the UI/UX pass
 * that added real light+dark theming). `IDS.Colors` is gone -- it was a second,
 * light-only-hardcoded color system with zero dark-mode values, the root cause of
 * BankScreen/MySpendingScreen/RecipientScreen having no working dark mode. Those
 * screens now use `Tds.colors` (rw.itunda.core.designsystem.theme.TdsSemanticColors),
 * which is real, theme-reactive, and shared with the rest of the app.
 *
 * What's left here (Typography/Shapes/Spacing/Size/Elevation) is genuinely
 * theme-invariant layout/type scale, not colors, so it stays as a real, still-used
 * part of the design system rather than something to also migrate. Typography
 * styles below no longer embed a `color` (they used to hardcode the old light-only
 * IDS.Colors values directly into the TextStyle, which would have rendered
 * dark-on-dark and been unreadable in dark mode even after call sites started
 * passing `color = Tds.colors.X` separately -- Compose's Text `color` param only
 * wins over `style.color` when explicitly non-default) -- callers must pass
 * `color = Tds.colors.X` explicitly, which every call site in this repo now does.
 */
object IDS {
    object Typography {
        val Header = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp
        )
        val Title = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        val SectionLabel = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        val BodyBold = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        val BodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
        val Caption = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
        val LargeAmount = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp
        )
        val Metric = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }

    object Shapes {
        val Screen = RoundedCornerShape(0.dp)
        val Card = RoundedCornerShape(28.dp)
        val SectionCard = RoundedCornerShape(26.dp)
        val Button = RoundedCornerShape(18.dp)
        val Pill = RoundedCornerShape(999.dp)
        val IconContainer = RoundedCornerShape(18.dp)
        val TabBar = RoundedCornerShape(28.dp)

        val IconBackground = IconContainer
    }

    object Spacing {
        val ScreenHorizontal = 20.dp
        val ScreenTop = 18.dp
        val Section = 24.dp
        val CardPadding = 24.dp
        val CardGap = 16.dp
        val RowGap = 14.dp
        val Inline = 12.dp
        val Tight = 8.dp
        val Hairline = 1.dp
    }

    object Size {
        val TopBarAction = 44.dp
        val QuickActionIcon = 48.dp
        val RowIcon = 44.dp
        val TabBarIcon = 26.dp
    }

    object Elevation {
        val FloatingTab = 14.dp
        val Card = 3.dp
    }
}
