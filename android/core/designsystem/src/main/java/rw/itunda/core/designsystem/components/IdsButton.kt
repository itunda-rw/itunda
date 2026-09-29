package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.designsystem.theme.IdsComponentTokens

/**
 * Real fix, 2026-07-21, grounded in Toss's own published account of this exact
 * failure mode ("디자인 시스템 다시 생각해보기" / "Rethinking Design System",
 * toss.tech/article/rethinking-design-system): a design system that only offers one
 * rigid shape stops being adopted -- teams fork/rewrite locally instead, and the
 * fork silently drifts from the real tokens over time. That's not hypothetical here:
 * before this fix, `ItundaAppScreen.kt` had three separate local button composables
 * (`PrimaryAction`, `SmallBlueButton`, `TopIconButton`) that never touched this file
 * at all -- and one of them, `PrimaryAction`'s "unfilled" variant, hardcoded
 * `Color(0xFF1F3053)` as a raw literal that happened to equal
 * `IdsSemanticColors.kt`'s real `pressed` dark-mode token at the moment it was
 * written, with nothing keeping the two in sync if the token ever changes.
 *
 * Matches Toss's own real solution shape: a single "Flat," props-based API (not a
 * Compound/sub-component API -- real TDS docs describe button variants as props,
 * not slots, unlike Card) that covers every real shape found in this codebase
 * through `variant`/`size` instead of a new one-off composable per screen.
 */
enum class IdsButtonVariant { Filled, Tinted, Tertiary, Destructive }
enum class IdsButtonSize { Large, Medium, Small }

@Composable
fun IdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: IdsButtonVariant = IdsButtonVariant.Filled,
    size: IdsButtonSize = IdsButtonSize.Large,
    // Real Toss reference (user-provided, 2026-08-03): the actual Toss Home account
    // card's own two buttons are "+ 채우기" / "↗ 보내기", a leading glyph before the
    // label on both -- not a decoration specific to those two, real TDS buttons take
    // an optional leading icon generally. Default null preserves every existing call
    // site (this app's own icon-less "Send"/"Log in"/etc. buttons) unchanged.
    icon: ImageVector? = null,
    // Real keyboard-docking reference (2026-08-12) -- see
    // IdsKeyboardDockedButton's own doc comment. Null (the default) preserves this
    // button's own normal rounded corners for every existing call site; only a
    // caller that specifically wants the flush, sharp-cornered "docked to the
    // keyboard" look passes one in.
    shape: androidx.compose.ui.graphics.Shape? = null,
) {
    val heightDp = when (size) {
        IdsButtonSize.Large -> IdsComponentTokens.Button.largeHeight
        IdsButtonSize.Medium -> IdsComponentTokens.Button.mediumHeight
        IdsButtonSize.Small -> IdsComponentTokens.Button.smallHeight
    }
    val horizontalPadding = when (size) {
        IdsButtonSize.Large, IdsButtonSize.Medium -> ButtonDefaults.ContentPadding
        IdsButtonSize.Small -> androidx.compose.foundation.layout.PaddingValues(horizontal = IdsComponentTokens.Button.smallHorizontalPadding, vertical = 0.dp)
    }
    val widthModifier = if (size == IdsButtonSize.Large) Modifier.fillMaxWidth() else Modifier
    val (containerColor, contentColor) = when (variant) {
        IdsButtonVariant.Filled -> Ids.colors.brand to IdsColors.White
        IdsButtonVariant.Tinted -> Ids.colors.pressed to Ids.colors.textBrand
        IdsButtonVariant.Tertiary -> Color.Transparent to Ids.colors.textBrand
        IdsButtonVariant.Destructive -> Ids.colors.danger to IdsColors.White
    }
    // Real Toss disabled-CTA reference (2026-08-12, direct user screenshot: a bottom
    // "Confirm" bar that's a dim TINT of the same brand blue while its required input
    // is empty, turning fully solid the moment it's valid) -- a neutral gray disabled
    // state (the old unconditional Ids.colors.divider below) reads as "broken/
    // unavailable" rather than "not ready yet, same action, just waiting on you."
    // Only applies to Filled -- Tinted's own disabled state (a secondary style, not
    // shown in the reference) keeps the existing neutral fallback.
    val (disabledContainerColor, disabledContentColor) = when (variant) {
        IdsButtonVariant.Filled -> Ids.colors.brand.copy(alpha = 0.35f) to IdsColors.White
        IdsButtonVariant.Tinted -> Ids.colors.divider to Ids.colors.textTertiary
        IdsButtonVariant.Tertiary -> Color.Transparent to Ids.colors.textTertiary
        IdsButtonVariant.Destructive -> Ids.colors.danger.copy(alpha = 0.35f) to IdsColors.White
    }
    // Real Toss micro-interaction reference (2026-08-11) -- "시각적 신호가 탭이 발생하는
    //정확한 순간에 햅틱/사용자 액션과 동기화되어야 한다" (visual cues synchronized
    // precisely with the tap): a subtle press-scale on every primary button in the app,
    // not just the transfer-success moment, so the whole app reads as tactile/alive
    // rather than one screen having motion and everything else staying flat static.
    // Applied here (not per call site) so it's automatically consistent everywhere
    // IdsButton is already used, matching this file's own "single Flat props-based API"
    // design-system principle instead of a one-off per screen.
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource, enabled)

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        modifier = modifier
            .semantics { role = Role.Button }
            .then(widthModifier)
             .scale(pressScale)
            .height(heightDp),
        shape = shape ?: RoundedCornerShape(if (size == IdsButtonSize.Small) IdsComponentTokens.Button.smallRadius else IdsComponentTokens.Button.largeRadius),
        contentPadding = horizontalPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (size == IdsButtonSize.Small) IdsComponentTokens.Button.smallIconSize else IdsComponentTokens.Button.iconSize),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (size == IdsButtonSize.Small) IdsComponentTokens.Button.smallIconSize else IdsComponentTokens.Button.iconSize))
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(IdsComponentTokens.Button.iconGap))
            }
            Text(
                text = text,
                style = if (size == IdsButtonSize.Small) IdsTypography.Body2 else IdsTypography.Button
            )
        }
    }
}

/** The icon-only circular shape `TopIconButton` used to duplicate locally in ItundaAppScreen.kt. */
@Composable
fun IdsIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactive = enabled && !loading
    val pressScale = rememberPressScale(interactionSource, interactive)
    Box(
        modifier = modifier
            .size(Ids.layout.minTouchTarget)
            .scale(pressScale)
            .clickable(
                enabled = interactive,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .background(
                if (enabled) Ids.colors.surfaceSoft else Ids.colors.surfaceSoft.copy(alpha = 0.5f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(IdsComponentTokens.Button.smallIconSize),
                color = Ids.colors.brand,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(IdsComponentTokens.IconButton.iconSize),
                tint = if (enabled) Ids.colors.textPrimary else Ids.colors.textTertiary,
            )
        }
    }
}


/** IDS 3.0 shared text-field primitive. */
@Composable
fun IdsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    successText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val supporting = errorText ?: successText ?: supportingText
    val validationColor = when {
        errorText != null -> Ids.colors.danger
        successText != null -> Ids.colors.success
        else -> Ids.colors.textSecondary
    }
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = errorText != null,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        supportingText = supporting?.let { message ->
            { Text(message, color = validationColor) }
        },
    )
}


/** IDS 3.0 shared select primitive: label, semantic selection control, and supporting/error state. */
@Composable
fun IdsSelect(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    successText: String? = null,
    enabled: Boolean = true,
) {
    val expandedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val supporting = errorText ?: successText ?: supportingText
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.material3.OutlinedButton(
            onClick = { expandedState.value = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(label, color = Ids.colors.textSecondary, style = IdsTypography.Caption)
                Text(value.ifEmpty { "Select" }, color = Ids.colors.textPrimary)
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expandedState.value,
            onDismissRequest = { expandedState.value = false },
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expandedState.value = false
                    },
                )
            }
        }
        if (supporting != null) {
            Text(
                supporting,
                color = when {
                    errorText != null -> Ids.colors.danger
                    successText != null -> Ids.colors.success
                    else -> Ids.colors.textSecondary
                },
                style = IdsTypography.Caption,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** IDS 3.0 native checkbox with a 44dp interaction target and explicit accessible label. */
@Composable
fun IdsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(label, color = Ids.colors.textPrimary, modifier = Modifier.padding(start = 8.dp))
    }
}

/** IDS 3.0 radio primitive with a complete accessible label row. */
@Composable
fun IdsRadio(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
        Text(label, color = Ids.colors.textPrimary, modifier = Modifier.padding(start = 8.dp))
    }
}

/** IDS 3.0 switch primitive with a complete accessible label row. */
@Composable
fun IdsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}


/** IDS 3.0 tabs primitive with deterministic selected state and native semantics. */
@Composable
fun IdsTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.TabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)),
        modifier = modifier,
    ) {
        labels.forEachIndexed { index, label ->
            androidx.compose.material3.Tab(
                selected = index == selectedIndex,
                onClick = { onSelectedIndexChange(index) },
                text = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}


/** IDS 3.0 shared progress primitive for screen-level loading states. */
@Composable
fun IdsLoading(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Ids.colors.brand,
        )
        label?.let {
            Text(
                text = it,
                style = IdsTypography.Body2,
                color = Ids.colors.textSecondary,
            )
        }
    }
}

/** IDS 3.0 shared inline error primitive. */
@Composable
fun IdsErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier,
        style = IdsTypography.Body1,
        color = Ids.colors.danger,
    )
}


/** IDS 3.0 canonical empty-state primitive shared by Android product surfaces. */
@Composable
fun IdsEmptyState(
    title: String,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = IdsTypography.Title1,
            color = Ids.colors.textPrimary,
        )
        message?.let {
            Text(
                text = it,
                style = IdsTypography.Body2,
                color = Ids.colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (actionText != null && onAction != null) {
            IdsButton(
                text = actionText,
                onClick = onAction,
                size = IdsButtonSize.Medium,
            )
        }
    }
}
