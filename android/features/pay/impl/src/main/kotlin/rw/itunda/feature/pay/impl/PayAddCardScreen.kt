package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

/** What the caller learns about a new card: never the full number or CVC, only the last 4 digits. */
data class PayNewCard(val brand: String, val last4: String, val name: String, val expiry: String)

/**
 * Prototype `addcard`: preview card, Card number / Name on card / Expiry / CVC, "Add card" (enabled only
 * for a complete, valid entry) and the encryption note. The "fill a sample card" link is a prototype
 * helper and is not part of the product, so it is omitted (its 50dp of space is kept to keep positions).
 */
@Composable
fun PayAddCardScreen(onBack: () -> Unit, onAdd: (PayNewCard) -> Unit) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    val digits = number.filter(Char::isDigit)
    val month = expiry.take(2).toIntOrNull() ?: 0
    val valid = digits.length == 16 && expiry.length == 5 && month in 1..12 && cvc.length == 3 && name.trim().length > 1
    PaySubScreen(onBack = onBack, title = "Add a card") { p ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp)) {
            CardPreview(digits, name.trim().uppercase(), expiry)
            CardField("Card number", number, "1234 5678 9012 3456", p) { v -> number = v.filter(Char::isDigit).take(16).chunked(4).joinToString(" ") }
            CardField("Name on card", name, "AMINA UWASE", p, KeyboardType.Text) { name = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    CardField("Expiry", expiry, "MM/YY", p) { v ->
                        val d = v.filter(Char::isDigit).take(4)
                        expiry = if (d.length > 2) d.take(2) + "/" + d.drop(2) else d
                    }
                }
                Box(Modifier.weight(1f)) { CardField("CVC", cvc, "123", p) { cvc = it.filter(Char::isDigit).take(3) } }
            }
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(if (valid) p.brand else p.soft)
                    .pressScaleClickable(enabled = valid) {
                        val brand = when (digits.first()) { '4' -> "Visa"; '5' -> "Mastercard"; else -> "Card" }
                        onAdd(PayNewCard(brand, digits.takeLast(4), name.trim(), expiry))
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Add card", color = if (valid) Color(0xFF17171C) else p.tt, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(53.6.dp))
            Text(
                "Your card details are encrypted. Only the last 4 digits are shown in the app.", color = p.tt, fontSize = 13.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CardPreview(digits: String, holder: String, expiry: String) {
    val shown = (digits + "••••••••••••••••").take(16).chunked(4).joinToString(" ")
    val line = (if (holder.isNotEmpty()) "$holder  " else "") + expiry.ifEmpty { "MM/YY" }
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp))
            .cssGradient158(0f to Color(0xFFA9A5FF), 0.46f to Color(0xFF7472F4), 1f to Color(0xFF4B43C9)),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(shown, color = Color.White, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(line, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 19.6.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun CardField(label: String, value: String, hint: String, p: PayPalette, type: KeyboardType = KeyboardType.Number, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(label, color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(keyboardType = type), cursorBrush = SolidColor(p.brandText),
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(p.soft),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, color = p.tt, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.Medium)
                    inner()
                }
            },
        )
    }
}
