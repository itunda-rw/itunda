package rw.itunda.app.DesignSystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object IDS {
    object Colors {
        val Brand = Color(0xFF3182F6)
        val BackgroundPrimary = Color(0xFFF4F6F8)
        val BackgroundSecondary = Color(0xFFFFFFFF)
        val BackgroundTertiary = Color(0xFFEDF2F7)
        val Card = Color(0xFFFFFFFF)
        val RaisedCard = Color(0xFFFFFFFF)
        val Pressed = Color(0xFFEAF2FF)
        val Divider = Color(0xFFE5E8EB)
        val TextPrimary = Color(0xFF191F28)
        val TextSecondary = Color(0xFF4E5968)
        val TextTertiary = Color(0xFF8B95A1)
        val TextBrand = Brand
        val SuccessTint = Color(0xFFE8F3FF)
        val WarningTint = Color(0xFFFFF4D6)
        val DangerTint = Color(0xFFFFECEB)
        val IconPrimary = Color(0xFF2C3643)
        val IconSecondary = Color(0xFF6B7684)
        val IconTertiary = Color(0xFFDDE3EA)
        val Shadow = Color(0x14000000)

        // Backward-compatible aliases for older mobile screens.
        val PrimaryBlue = Brand
        val Background = BackgroundPrimary
        val PositiveBackground = SuccessTint
    }

    object Typography {
        val Header = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            color = Colors.TextPrimary
        )
        val Title = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Colors.TextPrimary
        )
        val SectionLabel = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Colors.TextSecondary
        )
        val BodyBold = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Colors.TextPrimary
        )
        val BodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = Colors.TextSecondary
        )
        val Caption = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Colors.TextTertiary
        )
        val LargeAmount = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            color = Colors.TextPrimary
        )
        val Metric = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Colors.TextPrimary
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

        // Backward-compatible alias for older mobile screens.
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
