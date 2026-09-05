package rw.itunda.feature.banking.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.theme.Ids

// Real fix (2026-08-26): split out of Grow31SavingsScreen.kt once that file grew past
// its file-size-lint baseline. The product-intro content itself, called from the main
// screen which stays behind -- flipped from private (file-scoped in Kotlin too, at top
// level) to internal.

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13, 2026-08-26) --
// porting the same pattern already proven on web across 3 real products
// (ForeignCurrencyView's OpenForeignAccountFlow, and web's own Grow31/WeeklySavings
// 'intro' steps in BankDashboard.tsx). Before this, tapping "+ New 31-day plan" went
// straight into the creation form -- no explanation of the real bonus mechanics
// (base 1% + the streak-gated tier ladder) before the user committed.
@Composable
internal fun Grow31IntroContent(onContinue: () -> Unit) {
    FixedBottomCta(
        content = {
            Text(
                "Save a little every day, earn more the longer you keep it up",
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("One small deposit, every day, for $TERM_DAYS days", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Pick a fixed amount you can realistically save every single day. A base 1% rate applies from day one.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("The longer your unbroken streak, the higher your bonus", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Your bonus rate is locked in by the longest unbroken run of daily deposits you reach:",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 7, 14, 21, 31).forEach { days ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (days == TERM_DAYS) "$days days (full term)" else "$days-day streak",
                                color = Ids.colors.textSecondary, fontSize = 13.sp,
                            )
                            Text("+${grow31BonusRateForStreak(days).toInt()}%", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Miss a day? You keep what you already earned", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "A missed day resets your current streak, but the longest streak you already reached still locks in that bonus rate at maturity -- it isn't lost.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
        },
        cta = {
            IdsButton(text = "Continue", onClick = onContinue)
        },
    )
}
