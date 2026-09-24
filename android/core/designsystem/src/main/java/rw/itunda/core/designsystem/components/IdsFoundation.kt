package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography

/** Core visual primitives. Product screens should prefer these over raw Material components. */
@Composable
fun IdsSurface(
    modifier: Modifier = Modifier,
    color: Color = Ids.colors.surface,
    shape: Shape = androidx.compose.foundation.shape.RectangleShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = color,
        shape = shape,
        content = content,
    )
}

enum class IdsTextRole { Display, Headline, Title, SectionTitle, Body, SecondaryBody, Label, Caption, Amount }

@Composable
fun IdsText(
    text: String,
    role: IdsTextRole = IdsTextRole.Body,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val style = when (role) {
        IdsTextRole.Display -> IdsTypography.Display
        IdsTextRole.Headline -> IdsTypography.Headline
        IdsTextRole.Title -> IdsTypography.Title
        IdsTextRole.SectionTitle -> IdsTypography.SectionTitle
        IdsTextRole.Body -> IdsTypography.Body
        IdsTextRole.SecondaryBody -> IdsTypography.SecondaryBody
        IdsTextRole.Label -> IdsTypography.Label
        IdsTextRole.Caption -> IdsTypography.Caption
        IdsTextRole.Amount -> IdsTypography.Amount
    }
    Text(text = text, modifier = modifier, style = style, color = color ?: Ids.colors.textPrimary)
}

@Composable
fun IdsIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Ids.colors.iconPrimary,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun IdsDivider(
    modifier: Modifier = Modifier,
    color: Color = Ids.colors.divider,
) {
    Divider(modifier = modifier.fillMaxWidth(), color = color, thickness = IdsComponentTokens.Divider.thickness)
}

@Composable
fun IdsInset(
    modifier: Modifier = Modifier,
    color: Color = Ids.colors.surfaceSoft,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Ids.layout.sectionCornerRadius),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.background(color = color, shape = shape)) {
        content()
    }
}
