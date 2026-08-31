package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) -- porting the
// same pattern already shipped on bank-mfe (ForeignCurrencyView.tsx's
// OpenForeignAccountFlow) and on Android/iOS for Grow31/WeeklySavings, applied here to
// the one remaining product in that 3-product matrix that never got it. Before this,
// "opening" a foreign currency account on Android was a bare row of "+ Open USD"-style
// pills with zero explanation. Every fact below is real, sourced from
// ForeignCurrencyAccountService.kt's own doc comment/MARGIN_RATE constant -- the 1.5%
// margin, the live mid-market rate, and the rate-alert feature are all real,
// already-shipped backend behavior, not invented copy.
private val CURRENCY_FULL_NAME = mapOf("USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound")

@Composable
internal fun ForeignCurrencyIntroContent(
    availableCurrencies: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    submitting: Boolean,
    onOpen: () -> Unit,
) {
    FixedBottomCta(
        content = {
            Text(
                "Hold and convert real foreign currency",
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A separate account for each currency", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Keep USD, EUR, or GBP in its own account, completely separate from your RWF balance.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Convert at a real live rate", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Move money between RWF and your foreign currency anytime, at the real market rate plus itunda's transparent 1.5% margin -- no hidden fees.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Get notified at your rate", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Set a target rate once the account is open, and itunda tells you the moment the market crosses it -- convert when it's good for you, not just when you happen to check.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a currency", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    availableCurrencies.forEach { code ->
                        val isSelected = selected == code
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Ids.colors.brand else Ids.colors.surfaceSoft)
                                .pressScaleClickable { onSelect(code) }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(code, color = if (isSelected) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(CURRENCY_FULL_NAME[code] ?: code, color = if (isSelected) androidx.compose.ui.graphics.Color.White else Ids.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        cta = {
            IdsButton(
                text = if (submitting) "Opening…" else "Open account",
                enabled = selected != null && !submitting,
                onClick = onOpen,
            )
        },
    )
}
