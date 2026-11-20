package rw.itunda.rider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.rider.network.RiderDto
import rw.itunda.rider.network.RiderRatingResponse

@Composable
fun RiderMeTab(
    rider: RiderDto?,
    rating: RiderRatingResponse?,
    deliveredCount: Int,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text("Me", style = IdsTypography.Title1, color = Ids.colors.textPrimary)
        Spacer(modifier = Modifier.height(24.dp))
        InfoRow("Status", if (rider?.available == true) "Online" else "Offline")
        InfoRow(
            "Rating",
            rating?.let { r -> r.average?.let { avg -> String.format(Locale.US, "%.1f (%d reviews)", avg, r.count) } }
                ?: "No reviews yet",
        )
        InfoRow("Deliveries completed", deliveredCount.toString())
        Spacer(modifier = Modifier.weight(1f))
        IdsButton(
            text = "Log out",
            variant = IdsButtonVariant.Tinted,
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = IdsTypography.Body1, color = Ids.colors.textSecondary)
            Text(value, style = IdsTypography.Subtitle1, color = Ids.colors.textPrimary)
        }
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}
