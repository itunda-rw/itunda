package rw.itunda.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

// Real Toss TDS ProgressStepper component (tossmini-docs.toss.im/tds-mobile/components/
// progress-stepper) -- see docs/UI_UX_GUIDELINES.md's own product-feel research: shows
// "step 2 of 3"-style progress in a multi-step flow so the user has a visual sense of
// how many steps remain, not just what screen they're on. Already ported to bank-mfe
// (web's own ProgressStepper in BankDashboard.tsx, applied to TransferFlow and every
// Create*Form wizard since 2026-08-19) -- this is the first Android port. Real gap
// found live (2026-08-31, market-readiness/product-feel initiative): Android's own
// multi-step money flows (ItundaAppScreen.kt's TransferStep-driven send-money flow)
// have had zero step indicator this whole time, unlike web's identical flow.
@Composable
fun IdsProgressStepper(activeStepIndex: Int, steps: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        steps.forEachIndexed { i, label ->
            Column(
                modifier = Modifier.wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (i <= activeStepIndex) Ids.colors.brand else Ids.colors.divider),
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (i == activeStepIndex) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        i == activeStepIndex -> Ids.colors.brand
                        i < activeStepIndex -> Ids.colors.textSecondary
                        else -> Ids.colors.textTertiary
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 1,
                )
            }
            if (i < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                        .height(1.dp)
                        .background(if (i < activeStepIndex) Ids.colors.brand else Ids.colors.divider),
                )
            }
        }
    }
}
