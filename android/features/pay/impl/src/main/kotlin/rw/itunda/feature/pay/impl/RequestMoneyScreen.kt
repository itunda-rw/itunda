package rw.itunda.feature.pay.impl

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsToast
import rw.itunda.core.designsystem.components.formatMoney
import rw.itunda.core.designsystem.components.pressScaleClickable
import java.math.BigDecimal
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.theme.PretendardFontFamily

// v1038 palette overrides only roles that differ; shared IDS tokens are reused where they match.
private data class RequestMoneyPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val chip: Color,
    val tint: Color,
    val tintText: Color,
    val brand: Color,
    val onBrand: Color,
    val error: Color,
)

@Composable
private fun requestMoneyPalette(): RequestMoneyPalette {
    val dark = Ids.colors.background.luminance() < 0.5f
    return if (dark) {
        RequestMoneyPalette(
            background = Ids.colors.background,
            text = Color(0xFFF9FAFB),
            secondary = Color(0xFF9EA4AE),
            divider = Color(0xFF3A3A44),
            chip = Ids.colors.surfaceSoft,
            tint = Color(0xFF2A2850),
            tintText = Color(0xFFB3AEFF),
            brand = Ids.colors.brand,
            onBrand = Color(0xFF17171C),
            error = Color(0xFFFF7B6E),
        )
    } else {
        RequestMoneyPalette(
            background = Ids.colors.background,
            text = Color(0xFF191F28),
            secondary = Color(0xFF6B7684),
            divider = Color(0xFFE5E8EB),
            chip = Ids.colors.surfaceSoft,
            tint = Color(0xFFEEEDFF),
            tintText = Color(0xFF5B54D6),
            brand = Ids.colors.brand,
            onBrand = Color(0xFF17171C),
            error = Color(0xFFC4281B),
        )
    }
}

@Composable
fun RequestMoneyScreen(
    onBack: () -> Unit,
    state: RequestMoneyState = defaultRequestMoneyState,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val language = context.resources.configuration.locales[0].language
    val strings = requestMoneyStrings(language)
    val palette = requestMoneyPalette()
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val amount = state.amount
    val note = state.note
    val valid = amount in MIN_REQUEST_AMOUNT..MAX_REQUEST_AMOUNT
    val invalid = amount > MAX_REQUEST_AMOUNT || (amount > 0L && amount < MIN_REQUEST_AMOUNT)
    val amountHint = when {
        amount > MAX_REQUEST_AMOUNT -> strings.maximumError
        amount in 1L until MIN_REQUEST_AMOUNT -> strings.minimumError
        else -> strings.amountHint
    }
    val code = if (valid) requestMoneyCode(amount) else "—"
    val requestText = if (valid) {
        "Please pay me ${formatMoney(BigDecimal.valueOf(amount))} RWF on itunda. Code: $code" +
            if (note.isNotEmpty()) " · $note" else ""
    } else {
        ""
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        RequestMoneyTopBar(strings.title, palette, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp),
            ) {
                Text(
                    strings.amount,
                    color = palette.secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.28).sp,
                    fontFamily = PretendardFontFamily,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val amountText = if (amount == 0L) "" else formatMoney(BigDecimal.valueOf(amount))
                    BasicTextField(
                        value = amountText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            state.amount = when {
                                digits.isEmpty() -> 0L
                                else -> (digits.toLongOrNull() ?: MAX_INPUT_AMOUNT).coerceAtMost(MAX_INPUT_AMOUNT)
                            }
                        },
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = strings.amountAccessibility
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            color = palette.text,
                            fontSize = 40.sp,
                            lineHeight = 56.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            fontFamily = PretendardFontFamily,
                        ),
                        cursorBrush = SolidColor(palette.text),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (amountText.isEmpty()) {
                                    Text(
                                        "0",
                                        color = palette.secondary,
                                        fontSize = 40.sp,
                                        lineHeight = 56.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-1).sp,
                                        fontFamily = PretendardFontFamily,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Text(
                        "RWF",
                        color = palette.secondary,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        fontFamily = PretendardFontFamily,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Text(
                    amountHint,
                    color = if (invalid) palette.error else palette.secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.28).sp,
                    fontFamily = PretendardFontFamily,
                    modifier = Modifier.padding(top = 6.dp).heightIn(min = 20.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(2_000L, 5_000L, 10_000L, 25_000L).forEach { increment ->
                        RequestMoneyChip(
                            text = "+${formatMoney(BigDecimal.valueOf(increment))}",
                            palette = palette,
                            modifier = Modifier,
                        ) {
                            state.amount = (state.amount + increment).coerceAtMost(MAX_INPUT_AMOUNT)
                        }
                    }
                }
                Text(
                    strings.note,
                    color = palette.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    fontFamily = PretendardFontFamily,
                    modifier = Modifier.padding(top = 28.dp),
                )
                RequestMoneyNoteField(
                    value = note,
                    strings = strings,
                    palette = palette,
                    onValueChange = { state.note = it },
                )
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.chip)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        strings.requestCode,
                        color = palette.secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.28).sp,
                        fontFamily = PretendardFontFamily,
                    )
                    Text(
                        code,
                        color = if (valid) palette.text else palette.secondary,
                        fontSize = 24.sp,
                        lineHeight = 33.6.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = PretendardFontFamily,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val codeDescription = if (valid) {
                        "${formatMoney(BigDecimal.valueOf(amount))} RWF · ${strings.validFor}"
                    } else {
                        strings.enterAmount
                    }
                    Text(
                        codeDescription,
                        color = palette.secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.28).sp,
                        fontFamily = PretendardFontFamily,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(110.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .background(palette.background)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RequestMoneyActionButton(
                text = strings.copy,
                background = palette.tint,
                foreground = palette.tintText,
                enabled = valid,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            ) {
                clipboard.setText(AnnotatedString(requestText))
                IdsToast.show(coroutineScope, strings.copied)
            }
            RequestMoneyActionButton(
                text = strings.share,
                background = palette.brand,
                foreground = palette.onBrand,
                enabled = valid,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            ) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, requestText)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        }
    }
}

@Composable
// v1038 uses a 48dp back target, 20sp title and 4dp gap; BackTopBar has different fixed metrics.
private fun RequestMoneyTopBar(
    title: String,
    palette: RequestMoneyPalette,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(palette.background)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(48.dp)
                .pressScaleClickable { onBack() }
                .semantics {
                    role = Role.Button
                    contentDescription = "Back"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(IdsIcons.Back, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.text)
        }
        Text(
            title,
            color = palette.text,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            fontFamily = PretendardFontFamily,
        )
    }
}

@Composable
private fun RequestMoneyChip(
    text: String,
    palette: RequestMoneyPalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(palette.chip)
            .pressScaleClickable { onClick() }
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = palette.text,
            fontSize = 14.sp,
            lineHeight = 19.6.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.28).sp,
            fontFamily = PretendardFontFamily,
        )
    }
}

@Composable
private fun RequestMoneyNoteField(
    value: String,
    strings: RequestMoneyStrings,
    palette: RequestMoneyPalette,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(40).trim()) },
            modifier = Modifier.fillMaxWidth()
                .height(42.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = strings.noteAccessibility },
            singleLine = true,
            textStyle = TextStyle(
                color = palette.text,
                fontSize = 16.sp,
                lineHeight = 22.4.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.32).sp,
                fontFamily = PretendardFontFamily,
            ),
            cursorBrush = SolidColor(palette.text),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            strings.notePlaceholder,
                            color = palette.secondary,
                            fontSize = 16.sp,
                            lineHeight = 22.4.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.32).sp,
                            fontFamily = PretendardFontFamily,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(if (focused) palette.text else palette.divider),
        )
    }
}

// v1038's 58dp actions use screen-specific tint colours not represented by IdsButton variants.
@Composable
private fun RequestMoneyActionButton(
    text: String,
    background: Color,
    foreground: Color,
    enabled: Boolean,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .alpha(if (enabled) 1f else 0.35f)
            .pressScaleClickable(enabled = enabled) { onClick() }
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.32).sp,
            fontFamily = PretendardFontFamily,
        )
    }
}
