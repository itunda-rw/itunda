package rw.itunda.feature.my.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.PublicUser

/**
 * Prototype "Profile" screen: avatar, full name and a soft block of read-only facts
 * (phone, verification). The prototype's editable name + Save is NOT built: the
 * backend has no name-update endpoint, and a Save that did nothing would be a fake
 * control. The one real edit that exists -- the profile photo -- stays below.
 */
@Composable
internal fun ProfileDetail(user: PublicUser, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Ids.colors.background).verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(44.dp).clip(CircleShape).pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(IdsIcons.Back, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary) }
        Text("Profile", color = Ids.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                ItundaArt("shopkeeper", ItundaArtSet.Face, 80.dp)
            }
            Text("${user.firstName} ${user.lastName}", color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 18.dp, vertical = 8.dp)) {
                FactRow("Phone", user.phoneNumber.let { if (it.length > 7) it.take(4) + " ••• " + it.takeLast(3) else it })
                FactRow("Verification", if (user.kycVerified) "Verified" else "Not verified")
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) { ProfilePhotoCard() }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp)
        Text(value, color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
