package rw.itunda.app.CoreBank

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import rw.itunda.app.DesignSystem.IDS
import rw.itunda.app.SaroniteHost.*

data class MenuItem(val title: String, val activityClass: Class<*>?, val isNative: Boolean = false)

val menuItems = listOf(
    MenuItem("Wallet Balance", WalletBalanceMiniAppActivity::class.java),
    MenuItem("Pay Bills (Electricity, Water)", PayBillsMiniAppActivity::class.java),
    MenuItem("Reward Tasks", RewardTasksMiniAppActivity::class.java),
    MenuItem("Settings (Native)", null, isNative = true)
)

@Composable
fun MenuScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IDS.Colors.Background)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Apps in Itunda",
            style = IDS.Typography.Header,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(menuItems) { item ->
                MenuListItem(item) {
                    if (!item.isNative && item.activityClass != null) {
                        // Toss 'Brownfield' pattern: Launch concrete RN Module in Native wrapper
                        val intent = Intent(context, item.activityClass)
                        context.startActivity(intent)
                    } else {
                        // Launch Native Setting Activity
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom tab bar
            }
        }
    }
}

@Composable
fun MenuListItem(item: MenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IDS.Shapes.Card)
            .background(IDS.Colors.Card)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(IDS.Shapes.IconBackground)
                .background(IDS.Colors.Background),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder Icon
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.title,
            style = IDS.Typography.BodyBold
        )
    }
}
