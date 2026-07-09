package com.itunda.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = PrimaryLight,
    onPrimaryContainer = Primary,
    secondary          = Secondary,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = Secondary,
    background         = Background,
    surface            = Surface,
    surfaceVariant     = SurfaceVariant,
    error              = Error,
    onBackground       = OnBackground,
    onSurface          = OnSurface,
    onSurfaceVariant   = TextSecondary,
    outline            = CardBorder,
    outlineVariant     = Divider,
    // NavigationBar colours
    surfaceContainer   = Surface,          // nav bar background = white
)

@Composable
fun ItundaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = ItundaTypography,
        shapes      = ItundaShapes,
        content     = content
    )
}
