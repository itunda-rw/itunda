package rw.itunda.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TdsLightColors = lightColorScheme(
    primary = TdsColors.Blue500,
    onPrimary = TdsColors.White,
    background = TdsColors.Gray50,
    onBackground = TdsColors.Gray900,
    surface = TdsColors.White,
    onSurface = TdsColors.Gray900,
    error = TdsColors.Red500
)

private val TdsDarkColors = darkColorScheme(
    primary = TdsColors.Blue500,
    onPrimary = TdsColors.White,
    background = TdsColors.Gray900,
    onBackground = TdsColors.White,
    surface = TdsColors.Gray800,
    onSurface = TdsColors.White,
    error = TdsColors.Red500
)

@Composable
fun TdsTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TdsDarkColors else TdsLightColors
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
