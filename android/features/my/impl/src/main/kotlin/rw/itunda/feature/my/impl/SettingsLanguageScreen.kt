package rw.itunda.feature.my.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype v1038 Language screen.
 *
 * The prototype keeps the picker open after a selection and updates the app language
 * immediately. The caller owns the persisted locale; this screen only renders the
 * selected state and reports the selected language code.
 */
@Composable
fun SettingsLanguageScreen(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val heading = when (selectedLanguage) {
        "rw" -> "Itunda ikoreshe\nururimi uwuhe?"
        "fr" -> "Quelle langue\npour itunda ?"
        else -> "Which language should\nitunda use?"
    }
    val footnote = when (selectedLanguage) {
        "rw" -> "Bikoreshwa mu kohereza no kwishyura. Ikinyarwanda n’Igifaransa ntibyasuzumwe n’abavuga ururimi kavukire."
        "fr" -> "Couvre l’envoi et l’onglet Payer. Le kinyarwanda et le français n’ont pas été relus par un locuteur natif."
        else -> "Covers the Send flow and the Pay tab. Kinyarwanda and French have not been checked by a native speaker."
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Ids.colors.background),
    ) {
        ProtoTopBar(title = "", onBack = onBack)
        Text(
            text = heading,
            color = Ids.colors.textPrimary,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 14.dp),
        )
        listOf("en" to "English", "rw" to "Kinyarwanda", "fr" to "Français").forEach { (code, label) ->
            val selected = selectedLanguage == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClickable(onClick = { onLanguageSelected(code) })
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = Ids.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (selected) Ids.colors.brand else Ids.colors.chip),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = footnote,
            color = Ids.colors.textTertiary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
        )
    }
}
