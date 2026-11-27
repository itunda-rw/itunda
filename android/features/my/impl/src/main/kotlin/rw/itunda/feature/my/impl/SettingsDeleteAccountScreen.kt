package rw.itunda.feature.my.impl

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Prototype v1038's Delete account destination is a pre-deletion checklist, not a
 * destructive API call. Its three rows show the same informational toast actions;
 * this screen deliberately does not claim to delete an account.
 */
@Composable
fun SettingsDeleteAccountScreen(
    language: String,
    profileName: String,
    onBack: () -> Unit,
) {
    val copy = deleteAccountCopy(language, profileName)
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar(title = "", onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
            text = copy.title,
            color = Ids.colors.textPrimary,
            fontSize = 26.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 14.dp),
        )
        DeleteAccountRow("cashin3d", copy.moveMoney) {
            Toast.makeText(context, copy.moveToast, Toast.LENGTH_SHORT).show()
        }
        DeleteAccountRow("receipt", copy.reviewData) {
            Toast.makeText(context, copy.reviewToast, Toast.LENGTH_SHORT).show()
        }
        DeleteAccountRow("verify", copy.closeServices) {
            Toast.makeText(context, copy.servicesToast, Toast.LENGTH_SHORT).show()
        }
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            copy.bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Ids.colors.textTertiary),
                    )
                    Text(
                        text = bullet,
                        color = Ids.colors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun DeleteAccountRow(
    artName: String,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            ItundaArt(artName, ItundaArtSet.Proto, 34.dp, fit = true)
        }
        Text(
            text = title,
            color = Ids.colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        Icon(
            imageVector = IdsIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Ids.colors.textTertiary,
        )
    }
}

private data class DeleteAccountCopy(
    val title: String,
    val moveMoney: String,
    val reviewData: String,
    val closeServices: String,
    val moveToast: String,
    val reviewToast: String,
    val servicesToast: String,
    val bullets: List<String>,
)

private fun deleteAccountCopy(language: String, profileName: String): DeleteAccountCopy = when (language) {
    "rw" -> DeleteAccountCopy(
        title = "Kuzuza ibintu 3\nkugira ngo usibe konti",
        moveMoney = "Imura amafaranga asigaye",
        reviewData = "Reba amakuru azasibwa",
        closeServices = "Funga serivisi zihujwe",
        moveToast = "Gukuramo amafaranga asigaye",
        reviewToast = "Gusuzuma amakuru",
        servicesToast = "Serivisi zihujwe",
        bullets = listOf(
            "Buri wese ashobora gusiba konti ye.",
            "Amakuru yawe arasibwa, uretse ayo amategeko adusaba kubika igihe runaka.",
            "Ibyiza, amatangazo, ama-voucher n'ibikuponi byose birabura.",
            "Ikarita yawe ya itunda irafungwa.",
            "Niba ukibereyemo inguzanyo cyangwa indi serivisi, gusiba bishobora guhagarikwa.",
        ),
    )
    "fr" -> DeleteAccountCopy(
        title = "Terminez 3 étapes\npour supprimer le compte",
        moveMoney = "Transférer l’argent restant",
        reviewData = "Voir les données supprimées",
        closeServices = "Fermer les services liés",
        moveToast = "Retrait du solde",
        reviewToast = "Revue des données",
        servicesToast = "Services liés",
        bullets = listOf(
            "Tout le monde peut supprimer son compte.",
            "Vos données sont effacées, sauf celles que la loi impose de conserver.",
            "Offres, événements, bons et coupons sont perdus.",
            "Votre carte itunda est fermée.",
            "Si vous devez encore de l'argent (prêt, service), la suppression peut être bloquée.",
        ),
    )
    else -> DeleteAccountCopy(
        title = "Finish 3 things\nto delete your account",
        moveMoney = "Move your remaining money",
        reviewData = "Review data to be deleted",
        closeServices = "Close linked services",
        moveToast = "Opening: move your balance out",
        reviewToast = "Opening: data review",
        servicesToast = "Opening: linked services",
        bullets = listOf(
            "Anyone can delete their account.",
            "Your personal data is erased, except what the law requires us to keep for a set period. We can't show you everything kept about $profileName afterwards.",
            "Offers, events, vouchers and coupons are all lost.",
            "Your itunda card is closed.",
            "If you still owe money on a loan or other service, deletion may be blocked. Check that service's terms.",
        ),
    )
}
