package rw.itunda.app.CoreBank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.DesignSystem.IDS
import java.text.NumberFormat
import java.util.Locale

@Composable
fun TransferScreen(
    onBack: () -> Unit = {},
    onTransferComplete: (amount: String) -> Unit = {}
) {
    var amountText by remember { mutableStateOf("") }
    
    // Format the number to RWF with commas
    val formattedAmount = remember(amountText) {
        if (amountText.isEmpty()) ""
        else {
            val number = amountText.toLongOrNull() ?: 0L
            val formatter = NumberFormat.getNumberInstance(Locale.US)
            formatter.format(number)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IDS.Colors.Background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Back",
                style = IDS.Typography.BodyMedium,
                color = IDS.Colors.TextSecondary,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                text = "Transfer",
                style = IDS.Typography.BodyBold
            )
            Spacer(modifier = Modifier.width(40.dp)) // balance layout
        }

        // Amount Input Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "How much to send?",
                style = IDS.Typography.BodyMedium,
                color = IDS.Colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (amountText.isEmpty()) "0 RWF" else "$formattedAmount RWF",
                fontSize = if (amountText.isEmpty()) 32.sp else 40.sp,
                color = if (amountText.isEmpty()) IDS.Colors.TextSecondary.copy(alpha = 0.5f) else IDS.Colors.TextPrimary,
                style = IDS.Typography.Header,
                textAlign = TextAlign.Center
            )
        }

        // Keypad and Confirm Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(IDS.Colors.Background)
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            NumericKeypad(
                onNumberClick = { num ->
                    if (amountText.length < 9) { // Max limit to prevent overflow
                        amountText += num
                    }
                },
                onDeleteClick = {
                    if (amountText.isNotEmpty()) {
                        amountText = amountText.dropLast(1)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (amountText.isNotEmpty()) {
                        onTransferComplete(amountText)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (amountText.isNotEmpty()) IDS.Colors.PrimaryBlue else IDS.Colors.Card
                ),
                enabled = amountText.isNotEmpty()
            ) {
                Text(
                    text = "Next",
                    style = IDS.Typography.BodyBold,
                    color = if (amountText.isNotEmpty()) Color.White else IDS.Colors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "DEL")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        keys.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (key == "DEL") onDeleteClick()
                                else onNumberClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            style = IDS.Typography.Header,
                            fontSize = 24.sp,
                            color = IDS.Colors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}
