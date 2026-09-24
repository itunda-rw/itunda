package rw.itunda.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val IdsLightColors = lightColorScheme(
    primary = IdsLightSemanticColors.brand,
    onPrimary = IdsColors.White,
    background = IdsLightSemanticColors.background,
    onBackground = IdsLightSemanticColors.textPrimary,
    surface = IdsLightSemanticColors.surface,
    onSurface = IdsLightSemanticColors.textPrimary,
    error = IdsLightSemanticColors.danger
)

// Real black, matching the actual Toss app's dark mode (was navy #191F28
// before -- didn't match Toss or this project's own hand-tuned dark accents
// elsewhere; see IdsSemanticColors.kt for the full account).
private val IdsDarkColors = darkColorScheme(
    primary = IdsDarkSemanticColors.brand,
    onPrimary = IdsColors.White,
    background = IdsDarkSemanticColors.background,
    onBackground = IdsDarkSemanticColors.textPrimary,
    surface = IdsDarkSemanticColors.surface,
    onSurface = IdsDarkSemanticColors.textPrimary,
    error = IdsDarkSemanticColors.danger
)

@Composable
fun IdsTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IdsDarkColors else IdsLightColors
    val semanticColors = if (darkTheme) IdsDarkSemanticColors else IdsLightSemanticColors

    CompositionLocalProvider(LocalIdsSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
