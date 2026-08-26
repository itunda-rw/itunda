package rw.itunda.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.theme.Ids

// Real fix (2026-08-26): split out of WeeklySavingsScreen.kt once that file grew past
// its file-size-lint baseline. The product-intro content itself, called from the main
// screen which stays behind -- flipped from private (file-scoped in Kotlin too, at top
// level) to internal.

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13, 2026-08-26) --
// porting the same pattern already proven on web/Grow31SavingsScreen.kt. Real
// mechanics sourced from WeeklySavingsService.kt's BASE_RATE/BONUS_RATE/
// ESCALATION_STEP_WEEKS: unlike Grow31's partial-credit longest-streak bonus, this
// one is all-or-nothing -- one missed installment or any early withdrawal forfeits
// the bonus permanently. Stated explicitly rather than reusing Grow31's softer
// framing.
@Composable
internal fun WeeklySavingsIntroContent(onContinue: () -> Unit) {
    FixedBottomCta(
        content = {
            Text(
                "A weekly habit that grows on its own",
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A real $TERM_WEEKS-week term deposit", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Like KakaoBank's 26주적금: your weekly amount auto-debits from your main account every week for $TERM_WEEKS weeks -- nothing to top up manually.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Base 5% + a 3% bonus for staying unbroken", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "The 3% bonus is all-or-nothing: miss even one week's installment, or withdraw early, and the bonus is forfeited for good -- unlike a 31-day plan's partial-credit streak, this one doesn't have a middle ground.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your weekly amount can step up automatically", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Choose an escalation rate and your weekly amount compounds up every $ESCALATION_STEP_WEEKS weeks -- start small and build up, instead of committing to one fixed amount for all $TERM_WEEKS weeks.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                )
            }
        },
        cta = {
            IdsButton(text = "Continue", onClick = onContinue)
        },
    )
}
