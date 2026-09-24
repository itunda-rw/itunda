package rw.itunda.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Component-level tokens for IDS.
 *
 * Components should consume these tokens instead of embedding repeated dimensions.
 * This keeps the visual contract stable while allowing the implementation to evolve.
 */
object IdsComponentTokens {
    object Button {
        val largeHeight = 56.dp
        val mediumHeight = 48.dp
        val smallHeight = 36.dp
        val largeRadius = 12.dp
        val smallRadius = 10.dp
        val iconSize = 18.dp
        val smallIconSize = 14.dp
        val iconGap = 6.dp
        val smallHorizontalPadding = 16.dp
        val pressedScale = 0.98f
    }

    object TextField {
        val radius = 12.dp
        val supportingTextStart = 16.dp
        val supportingTextTop = 4.dp
        val passwordIconSize = 20.dp
    }

    object Card {
        val borderWidth = 1.dp
    }

    object Divider {
        val thickness = 0.5.dp
    }

    object IconButton {
        val iconSize = 20.dp
    }
}
