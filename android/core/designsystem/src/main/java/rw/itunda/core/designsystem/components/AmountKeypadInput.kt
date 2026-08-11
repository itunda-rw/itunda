package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import java.text.NumberFormat
import java.util.Locale

private val amountKeypadFormatter = NumberFormat.getNumberInstance(Locale.US)

/**
 * Real Toss-style big-number-display + on-screen keypad amount entry (2026-08-12) --
 * the user's own explicit follow-up to the earlier live comma-formatting fix
 * (AmountVisualTransformation), asking to go further and replicate Toss's actual
 * *interaction*, not just format an ordinary text field. `TransferFlow.kt`'s own
 * `TransferAmountScreen` already had this exact real pattern (built directly against
 * real Toss reference screenshots, 2026-07-10) for itunda's flagship send-money
 * flow -- this is the same pattern extracted into a shared, reusable component so
 * every other real money-entry screen (loans, savings, agent cash, gifts, etc.) can
 * use the real interaction instead of a plain text field, matching Toss's own actual
 * 2019 UI overhaul goal of one consistent amount-entry experience across transfer,
 * savings, and loan flows (news1.kr/finance coverage of that redesign).
 *
 * Deliberately NOT extracted FROM `TransferFlow.kt` (which stays untouched, still the
 * original, already-tested implementation for itunda's highest-stakes money flow) --
 * a fresh, parameterized twin instead, so migrating other screens onto this carries
 * zero risk of regressing the one flow that's seen the most real, live verification
 * this session.
 *
 * The caller keeps full ownership of `digits` (pure numeric string, no commas) and
 * whatever submit/validation button it already has -- this only replaces the INPUT
 * widget (a plain `IdsTextField` before) with the real display+keypad interaction,
 * never the surrounding screen's own logic.
 */
@Composable
fun AmountKeypadInput(
    digits: String,
    onDigitsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    currencyLabel: String = "RWF",
    maxDigits: Int = 9,
    // Quick-amount chips add this many units to whatever's already typed, matching
    // TransferAmountScreen's own real "+10,000"/"+100,000" chips exactly.
    quickAmounts: List<Long> = emptyList(),
    // When set, renders a real "Max" chip that fills the field to exactly this value
    // -- same real affordance TransferAmountScreen's own balance-aware "Max" chip
    // already establishes.
    maxAmount: Long? = null,
    helperText: String? = null,
    isHelperTextError: Boolean = false,
) {
    val amount = digits.toLongOrNull() ?: 0L
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (digits.isEmpty()) "0 $currencyLabel" else "${amountKeypadFormatter.format(amount)} $currencyLabel",
                fontSize = if (digits.isEmpty()) 28.sp else 38.sp,
                fontWeight = FontWeight.Bold,
                color = if (digits.isEmpty()) Ids.colors.textTertiary else Ids.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (helperText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    helperText,
                    color = if (isHelperTextError) Ids.colors.danger else Ids.colors.textTertiary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (quickAmounts.isNotEmpty() || maxAmount != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                quickAmounts.forEach { chipAmount ->
                    AmountKeypadChip("+${amountKeypadFormatter.format(chipAmount)}") {
                        val next = amount + chipAmount
                        onDigitsChange(next.toString().take(maxDigits))
                    }
                }
                if (maxAmount != null) {
                    AmountKeypadChip("Max") { onDigitsChange(maxAmount.toString().take(maxDigits)) }
                }
            }
        }
        AmountKeypad(
            onDigit = { d -> if (digits.length < maxDigits) onDigitsChange(digits + d) },
            onDelete = { if (digits.isNotEmpty()) onDigitsChange(digits.dropLast(1)) },
        )
    }
}

@Composable
private fun AmountKeypadChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Ids.colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AmountKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "DEL"),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { if (key == "DEL") onDelete() else onDigit(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "DEL") {
                            Text("⌫", fontSize = 20.sp, color = Ids.colors.textPrimary)
                        } else {
                            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ids.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
