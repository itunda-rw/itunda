package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

private const val DIAL_PREFIX = "+250"
private const val LOCAL_DIGITS = 9

/** The local part the user has typed (digits after +250), from the full number the login flow keeps. */
internal fun localDigits(full: String): String = full.removePrefix(DIAL_PREFIX).filter { it.isDigit() }

internal fun isCompletePhone(full: String): Boolean = localDigits(full).length == LOCAL_DIGITS

/** "+250" in ink, then "788 123 456" -- typed digits in ink, the rest of the example in light grey. */
@Composable
internal fun PhoneDisplay(full: String) {
    val typed = localDigits(full)
    val example = "788123456"
    fun group(s: String) = buildString { s.forEachIndexed { i, c -> if (i == 3 || i == 6) append(' '); append(c) } }
    val shown = typed + example.drop(typed.length)
    val grouped = group(shown)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$DIAL_PREFIX ", color = Ids.colors.textSecondary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            // typed part in ink, remaining example digits in grey, spaces kept in sync with the grouping
            val typedLen = group(typed).length
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(androidx.compose.ui.text.AnnotatedString(grouped.take(typedLen), androidx.compose.ui.text.SpanStyle(color = Ids.colors.textPrimary)))
                    append(androidx.compose.ui.text.AnnotatedString(grouped.drop(typedLen), androidx.compose.ui.text.SpanStyle(color = Ids.colors.divider)))
                },
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ids.colors.brand))
    }
}

/** The prototype's own 3x4 digit pad: 1-9, a gap, 0, backspace. Edits the full "+250..." number. */
@Composable
internal fun PhoneKeypad(full: String, onChange: (String) -> Unit) {
    fun press(d: String) {
        val cur = localDigits(full)
        when {
            d == "<" -> onChange(if (cur.length <= 1) "" else DIAL_PREFIX + cur.dropLast(1))
            cur.length >= LOCAL_DIGITS -> Unit
            cur.isEmpty() && d == "0" -> Unit // numbers are entered without the leading 0
            else -> onChange(DIAL_PREFIX + cur + d)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(62.dp).then(if (key.isEmpty()) Modifier else Modifier.pressScaleClickable { press(key) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "" -> Unit
                            "<" -> Icon(IdsIcons.Back, "Delete", Modifier.size(28.dp), tint = Ids.colors.textPrimary)
                            else -> Text(key, color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
