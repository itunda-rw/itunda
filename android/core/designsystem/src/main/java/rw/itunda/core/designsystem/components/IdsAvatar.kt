package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import rw.itunda.core.designsystem.theme.IdsColors

/**
 * Real fix, found live 2026-08-05 while visually auditing the app on a real emulator
 * (not just structural dumps -- the user flagged the whole app still looks unstyled
 * despite prior design-token work, and this was one of the concrete, verifiable bugs
 * behind that): Talk's `ConversationRow` rendered `Icons.Outlined.Send` -- a paper
 * airplane, the SEND-message icon -- as the OTHER PERSON's avatar. `GroupRow` had no
 * avatar at all. Neither is a style opinion; both read as broken to any real user.
 *
 * No shared avatar component existed anywhere in the design system to render either
 * one correctly, so every call site improvised. This is the real fix: a photo if one
 * exists, else a deterministic colored-initials circle (Kakao/Toss's own real avatar
 * fallback pattern) -- never a random unrelated glyph.
 *
 * Colors are drawn from the existing real IdsColors numbered scale (not invented) --
 * a small, deliberately muted set so initials avatars read as calm UI chrome, not
 * competing brand-colored blobs.
 */
private val avatarPalette = listOf(
    IdsColors.Blue400,
    IdsColors.Red400,
    IdsColors.Green500,
    IdsColors.Grey600,
    IdsColors.Blue700,
    IdsColors.Red700,
)

private fun colorFor(name: String): Color {
    val key = name.trim().ifEmpty { "?" }
    val index = (key.sumOf { it.code } % avatarPalette.size + avatarPalette.size) % avatarPalette.size
    return avatarPalette[index]
}

private fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Composable
fun IdsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    size: Dp = 48.dp,
) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            modifier = modifier.size(size).background(IdsColors.Grey200, CircleShape),
        )
    } else {
        Box(
            modifier = modifier.size(size).background(colorFor(name), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsFor(name),
                color = IdsColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}
