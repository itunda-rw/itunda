package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** Prototype Ikimina intro top: headline, one-line explainer, an 8-member round (member 5 is "this round") and the pot. */
@Composable
fun IkiminaIntroHeader() {
    Column(Modifier.fillMaxWidth()) {
        Text("Save together,\ntake turns", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
        Text(
            "Everyone in the group puts in the same amount each round. Each round, one member takes the whole pot.",
            color = Ids.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1..8).forEach { n ->
                val on = n == 5
                Box(
                    Modifier.size(if (on) 30.dp else 26.dp).clip(CircleShape).background(if (on) Ids.colors.brand else Ids.colors.surfaceSoft),
                    contentAlignment = Alignment.Center,
                ) { Text("$n", color = if (on) Ids.colors.background else Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("80,000 RWF", color = Ids.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "the pot this round, with 8 members paying 10,000 RWF", color = Ids.colors.textTertiary, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
