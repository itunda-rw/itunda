package rw.itunda.feature.my.impl

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import rw.itunda.core.designsystem.components.ProtoTopBar
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.PublicUser

/**
 * Prototype v1038 Settings list. This composable lives in the :my feature module
 * so the settings list can be rendered by the screenshots module; the :app caller
 * supplies real navigation and platform-backed notification-channel state.
 */
@Composable
fun SettingsHomeScreen(
    profile: PublicUser?,
    language: String,
    textSizeLabel: String,
    deviceModel: String,
    deviceCount: Int,
    sendLimitLabel: String,
    payLabel: String,
    moneyNotificationsEnabled: Boolean,
    generalNotificationsEnabled: Boolean,
    onMoneyNotificationsClick: () -> Unit,
    onGeneralNotificationsClick: () -> Unit,
    onBack: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenTextSize: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenManageContacts: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSendLimits: () -> Unit,
    onOpenPay: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var showProfile by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    val copy = settingsHomeCopy(language)
    val context = LocalContext.current
    val profileName = profile?.let { "${it.firstName} ${it.lastName}".trim() }
        ?.takeIf { it.isNotBlank() } ?: "Amina Uwase"

    if (showProfile && profile != null) {
        ProfileDetail(profile, onBack = { showProfile = false })
        return
    }
    if (showDeleteAccount) {
        SettingsDeleteAccountScreen(
            language = language,
            profileName = profileName,
            onBack = { showDeleteAccount = false },
        )
        return
    }

    val languageLabel = when (language) {
        "rw" -> "Kinyarwanda"
        "fr" -> "Français"
        else -> "English"
    }
    val sizeLabel = when (textSizeLabel.lowercase()) {
        "large" -> copy.large
        "largest" -> copy.largest
        else -> copy.defaultSize
    }
    val paymentLabel = when (payLabel) {
        "Pay balance" -> copy.payBalance
        "Bank" -> copy.bank
        else -> payLabel
    }
    val name = profileName
    val initial = profile?.firstName?.firstOrNull()?.uppercase() ?: name.firstOrNull()?.uppercase() ?: "A"

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        ProtoTopBar(title = copy.settings, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScaleClickable(onClick = {
                            if (profile != null) showProfile = true
                            else Toast.makeText(context, copy.profileUnavailable, Toast.LENGTH_SHORT).show()
                        })
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Ids.colors.brand.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initial, color = Ids.colors.brand, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            color = Ids.colors.textPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                        )
                        Text(copy.myInfo, color = Ids.colors.textSecondary, fontSize = 14.sp)
                    }
                    Icon(
                        IdsIcons.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Ids.colors.textTertiary,
                    )
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsHomeRow(copy.display, onClick = onOpenDisplay)
                    SettingsHomeRow(copy.textSize, value = sizeLabel, onClick = onOpenTextSize)
                    SettingsHomeRow(copy.language, value = languageLabel, onClick = onOpenLanguage)
                    SettingsHomeRow(copy.manageContacts, onClick = onOpenManageContacts)
                    SettingsHomeRow(copy.notifications, onClick = onOpenNotifications)
                }
            }
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsHomeSectionHeading(copy.authentication)
                    SettingsHomeRow(copy.security, onClick = onOpenSecurity)
                    SettingsHomeRow(
                        title = copy.devices,
                        subtitle = "$deviceModel${copy.thisDevice}",
                        value = deviceCount.toString(),
                        onClick = onOpenDevices,
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsHomeSectionHeading(copy.transferPayment)
                    SettingsHomeRow(copy.sendLimits, value = sendLimitLabel, onClick = onOpenSendLimits)
                    SettingsHomeRow(copy.pay, value = paymentLabel, onClick = onOpenPay)
                }
            }
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsHomeSectionHeading(copy.notifications)
                    SettingsHomeToggleRow(copy.money, moneyNotificationsEnabled, onMoneyNotificationsClick)
                    SettingsHomeToggleRow(copy.general, generalNotificationsEnabled, onGeneralNotificationsClick)
                    SettingsHomeRow(copy.markAllRead, onClick = onMarkAllRead)
                }
            }
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsHomeSectionHeading(copy.legalDocuments)
                    SettingsHomeRow(copy.creditPolicy, onClick = { onOpenLegal("credit_data_policy") })
                    SettingsHomeRow(copy.privacyPolicy, onClick = { onOpenLegal("privacy_policy") })
                    SettingsHomeRow(copy.termsConsent, onClick = { onOpenLegal("terms_of_service") })
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ids.colors.chip)
                        .pressScaleClickable(onClick = onSignOut),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(copy.logOut, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = copy.deleteAccount,
                    color = Ids.colors.danger,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScaleClickable(onClick = { showDeleteAccount = true })
                        .padding(top = 18.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsHomeSectionHeading(text: String) {
    Text(
        text = text,
        color = Ids.colors.textTertiary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsHomeRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ids.colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
            )
            subtitle?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        value?.let {
            Text(it, color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = IdsIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Ids.colors.textTertiary,
        )
    }
}

@Composable
private fun SettingsHomeToggleRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Ids.colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f),
        )
        SettingsHomeSwitch(checked = checked, onClick = onClick)
    }
}

@Composable
private fun SettingsHomeSwitch(checked: Boolean, onClick: () -> Unit) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(durationMillis = 220),
    )
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(if (checked) Ids.colors.brand else Ids.colors.divider)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private data class SettingsHomeCopy(
    val settings: String,
    val myInfo: String,
    val display: String,
    val textSize: String,
    val language: String,
    val manageContacts: String,
    val notifications: String,
    val authentication: String,
    val security: String,
    val devices: String,
    val thisDevice: String,
    val transferPayment: String,
    val sendLimits: String,
    val pay: String,
    val bank: String,
    val payBalance: String,
    val money: String,
    val general: String,
    val markAllRead: String,
    val legalDocuments: String,
    val creditPolicy: String,
    val privacyPolicy: String,
    val termsConsent: String,
    val logOut: String,
    val deleteAccount: String,
    val defaultSize: String,
    val large: String,
    val largest: String,
    val profileUnavailable: String,
)

private fun settingsHomeCopy(language: String): SettingsHomeCopy = when (language) {
    "rw" -> SettingsHomeCopy(
        settings = "Igenamiterere",
        myInfo = "Amakuru yanjye",
        display = "Imiterere & kunyeganyega",
        textSize = "Ingano y'inyandiko",
        language = "Ururimi",
        manageContacts = "Gucunga abo uziranye",
        notifications = "Amamenyesha",
        authentication = "Kwemeza n'Umutekano",
        security = "Umutekano",
        devices = "Ibikoresho",
        thisDevice = " (iki gikoresho)",
        transferPayment = "Kohereza no Kwishyura",
        sendLimits = "Imipaka yo kohereza",
        pay = "Kwishyura",
        bank = "Banki",
        payBalance = "Pay balance",
        money = "Amamenyesha y'amafaranga",
        general = "Amamenyesha rusange",
        markAllRead = "Yose yasomwe",
        legalDocuments = "Inyandiko z'Amategeko",
        creditPolicy = "Politiki yo gukoresha amakuru y'inguzanyo",
        privacyPolicy = "Politiki y'ibanga",
        termsConsent = "Amabwiriza n'uruhushya",
        logOut = "Sohoka",
        deleteAccount = "Gusiba konti",
        defaultSize = "Bisanzwe",
        large = "Binini",
        largest = "Binini cyane",
        profileUnavailable = "Amakuru y'umwirondoro ntaraboneka.",
    )
    "fr" -> SettingsHomeCopy(
        settings = "Paramètres",
        myInfo = "Mes informations",
        display = "Affichage et vibrations",
        textSize = "Taille du texte",
        language = "Langue",
        manageContacts = "Gérer les contacts",
        notifications = "Notifications",
        authentication = "Authentification et sécurité",
        security = "Sécurité",
        devices = "Appareils",
        thisDevice = " (cet appareil)",
        transferPayment = "Virement et paiement",
        sendLimits = "Plafonds d’envoi",
        pay = "Payer",
        bank = "Banque",
        payBalance = "Solde Pay",
        money = "Notifications d'argent",
        general = "Notifications générales",
        markAllRead = "Tout marquer comme lu",
        legalDocuments = "Documents légaux",
        creditPolicy = "Politique d'utilisation des données de crédit",
        privacyPolicy = "Politique de confidentialité",
        termsConsent = "Conditions et consentement",
        logOut = "Se déconnecter",
        deleteAccount = "Supprimer le compte",
        defaultSize = "Par défaut",
        large = "Grand",
        largest = "Très grand",
        profileUnavailable = "Les informations du profil ne sont pas disponibles.",
    )
    else -> SettingsHomeCopy(
        settings = "Settings",
        myInfo = "My info",
        display = "Display & haptics",
        textSize = "Text size",
        language = "Language",
        manageContacts = "Manage contacts",
        notifications = "Notifications",
        authentication = "Authentication & Security",
        security = "Security",
        devices = "Devices",
        thisDevice = " (this device)",
        transferPayment = "Transfer & Payment",
        sendLimits = "Send limits",
        pay = "Pay",
        bank = "Bank",
        payBalance = "Pay balance",
        money = "Money notifications",
        general = "General notifications",
        markAllRead = "Mark all read",
        legalDocuments = "Legal Documents",
        creditPolicy = "Credit data usage policy",
        privacyPolicy = "Privacy policy",
        termsConsent = "Terms & consent",
        logOut = "Log out",
        deleteAccount = "Delete account",
        defaultSize = "Default",
        large = "Large",
        largest = "Largest",
        profileUnavailable = "Profile information isn't available yet.",
    )
}
