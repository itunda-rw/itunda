package rw.itunda.app.CoreBank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.DesignSystem.IDS

/**
 * Fact-checked Toss Bottom Navigation Structure:
 * Toss uses a 5-tab system (Home, Benefits, Invest, Pay, Menu) often with a floating/clean style.
 * Adapted for Itunda (Rwanda): Home, Benefits, Transfer, Pay, Menu.
 */
enum class BottomTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    BENEFITS("Benefits", Icons.Outlined.CardGiftcard),
    TRANSFER("Transfer", Icons.Outlined.SwapHoriz),
    PAY("Pay", Icons.Outlined.CreditCard),
    MENU("Menu", Icons.Outlined.GridView)
}

@Composable
fun MainTabScreen() {
    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    var showMySpending by remember { mutableStateOf(false) }
    var showRecipient by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    if (showMySpending) {
        MySpendingScreen(onBack = { showMySpending = false })
    } else if (showRecipient) {
        RecipientScreen(
            onBack = { showRecipient = false },
            onRecipientSelected = { name, detail -> 
                showRecipient = false
                showTransfer = true 
            }
        )
    } else if (showTransfer) {
        TransferScreen(
            onBack = { showTransfer = false },
            onTransferComplete = { amount -> 
                showTransfer = false
                // TODO: Trigger actual backend API call
                println("Transfer amount: $amount RWF")
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                TossFloatingTabBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            },
            containerColor = IDS.Colors.Background
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (currentTab) {
                    BottomTab.HOME -> BankScreen(
                        onNavigateToSpending = { showMySpending = true },
                        onNavigateToTransfer = { showRecipient = true }
                    )
                    BottomTab.BENEFITS -> PlaceholderScreen("Benefits", "Cashback, coupons, and daily rewards")
                    BottomTab.TRANSFER -> PlaceholderScreen("Transfer", "Send to bank, wallet, or mobile money")
                    BottomTab.PAY -> PlaceholderScreen("Pay", "Scan QR and pay merchants around Kigali")
                    BottomTab.MENU -> MenuScreen()
                }
            }
        }
    }
}

@Composable
fun TossFloatingTabBar(currentTab: BottomTab, onTabSelected: (BottomTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .shadow(
                elevation = IDS.Elevation.FloatingTab,
                shape = IDS.Shapes.TabBar,
                spotColor = IDS.Colors.Shadow
            )
            .clip(IDS.Shapes.TabBar)
            .background(IDS.Colors.Card)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.title,
                    modifier = Modifier.size(IDS.Size.TabBarIcon),
                    tint = if (isSelected) IDS.Colors.IconPrimary else IDS.Colors.IconSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tab.title,
                    fontSize = 10.sp,
                    color = if (isSelected) IDS.Colors.TextPrimary else IDS.Colors.TextSecondary,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IDS.Colors.BackgroundPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = IDS.Spacing.ScreenHorizontal)
                .clip(IDS.Shapes.SectionCard)
                .background(IDS.Colors.Card)
                .padding(IDS.Spacing.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
        ) {
            Text(text = title, style = IDS.Typography.Title)
            Text(text = subtitle, style = IDS.Typography.BodyMedium)
        }
    }
}
