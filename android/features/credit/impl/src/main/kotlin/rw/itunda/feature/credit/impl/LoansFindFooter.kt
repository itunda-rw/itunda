package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private val notes = listOf(
    "Approval rates by credit score are sample data for this prototype, based on statistics from partner lenders.",
    "The rate and limit shown are estimates. Your real result can differ when the lender reviews your income, work and account history.",
    "Loans come from itunda's partner lenders only, not every lender.",
    "Using a loan can lower your credit score. Missing payments can hurt you.",
    "You have the right to a clear explanation. Read the terms before you sign.",
    "itunda Bank is not a licensed bank.",
)

/** Prototype "Exclusively at itunda" card (358 wide at x16, r24) followed by the grey footnote band. */
@Composable
fun LoansFindFooter(onExclusive: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.surfaceSoft)
                .pressScaleClickable(onClick = onExclusive).padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Exclusively at itunda", color = Ids.colors.textTertiary, fontSize = 17.sp, lineHeight = 23.8.sp)
                Spacer(Modifier.height(6.dp))
                Text("Exclusive · See\npreferential rate", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2563EB)))
            Box(Modifier.offset((-10).dp).size(34.dp).clip(CircleShape).background(Color(0xFFF5B301)))
            Text("+21", color = Ids.colors.textPrimary, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth().background(Ids.colors.surfaceSoft).padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 120.dp)) {
            notes.forEach { Text("• $it", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 22.sp) }
        }
    }
}

/** Whole prototype "Find loan" first screen: hero, band, partner rows, exclusive card, notes + sticky CTA. */
@Composable
fun LoansFindPage(lowestRate: String?, onCheckRate: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            LoansFindHero(lowestRate)
            Box(Modifier.fillMaxWidth().height(12.dp).background(Ids.colors.surfaceSoft))
            LoansFindPartners()
            LoansFindFooter(onExclusive = onCheckRate)
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Ids.colors.background).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)) {
            IdsButton(text = "Check your loan rate", onClick = onCheckRate)
        }
    }
}
