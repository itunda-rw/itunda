package rw.itunda.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val TdsLightColors = lightColorScheme(
    primary = TdsLightSemanticColors.brand,
    onPrimary = TdsColors.White,
    background = TdsLightSemanticColors.background,
    onBackground = TdsLightSemanticColors.textPrimary,
    surface = TdsLightSemanticColors.surface,
    onSurface = TdsLightSemanticColors.textPrimary,
    error = TdsLightSemanticColors.danger
)

// Real black, matching the actual Toss app's dark mode (was navy #191F28
// before -- didn't match Toss or this project's own hand-tuned dark accents
// elsewhere; see TdsSemanticColors.kt for the full account).
private val TdsDarkColors = darkColorScheme(
    primary = TdsDarkSemanticColors.brand,
    onPrimary = TdsColors.White,
    background = TdsDarkSemanticColors.background,
    onBackground = TdsDarkSemanticColors.textPrimary,
    surface = TdsDarkSemanticColors.surface,
    onSurface = TdsDarkSemanticColors.textPrimary,
    error = TdsDarkSemanticColors.danger
)

@Composable
fun TdsTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TdsDarkColors else TdsLightColors
    val semanticColors = if (darkTheme) TdsDarkSemanticColors else TdsLightSemanticColors

    CompositionLocalProvider(LocalTdsSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
