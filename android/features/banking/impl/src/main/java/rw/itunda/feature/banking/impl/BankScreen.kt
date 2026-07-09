package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import rw.itunda.core.designsystem.ids.IDS

// Named to avoid ambiguous type inference when used as one branch of an
// inline if/else assigned directly to a () -> Unit parameter (a real
// compile error the original ported file had, fixed 2026-07-10).
private val NoOpClick: () -> Unit = {}

private data class QuickAction(
    val title: String,
    val icon: ImageVector,
    val background: Color,
    val onClick: () -> Unit
)

private data class HomeRow(
    val title: String,
    val subtitle: String,
    val trailing: String,
    val icon: ImageVector,
    val iconBackground: Color
)

@Composable
fun BankScreen(
    onNavigateToSpending: () -> Unit = {},
    onNavigateToTransfer: () -> Unit = {}
) {
    val quickActions = listOf(
        QuickAction("Transfer", Icons.Outlined.ArrowForward, IDS.Colors.SuccessTint, onNavigateToTransfer),
        QuickAction("Bills", Icons.Outlined.ReceiptLong, IDS.Colors.WarningTint, {}),
        QuickAction("MoMo", Icons.Outlined.Smartphone, IDS.Colors.Pressed, {}),
        QuickAction("Savings", Icons.Outlined.Savings, IDS.Colors.DangerTint, {})
    )
    val linkedMoneyRows = listOf(
        HomeRow("BK Bank account", "Salary and card settlement", "RWF 842,000", Icons.Outlined.AccountBalance, IDS.Colors.BackgroundTertiary),
        HomeRow("MTN MoMo", "Daily spending wallet", "RWF 118,400", Icons.Outlined.Smartphone, IDS.Colors.SuccessTint),
        HomeRow("Airtel Money", "Backup cash-out line", "Connected", Icons.Outlined.Payments, IDS.Colors.WarningTint)
    )
    val serviceRows = listOf(
        HomeRow("Pay CashPower", "Top up electricity instantly", "Open", Icons.Outlined.ReceiptLong, IDS.Colors.WarningTint),
        HomeRow("Irembo services", "Government and document payments", "Browse", Icons.Outlined.AccountBalance, IDS.Colors.Pressed),
        HomeRow("My spending", "View monthly categories and trends", "See all", Icons.Outlined.AccountBalanceWallet, IDS.Colors.BackgroundTertiary)
    )
    val rewardRows = listOf(
        HomeRow("Itunda rewards", "Claim today's cashback and offers", "140 RWF", Icons.Outlined.Payments, IDS.Colors.SuccessTint),
        HomeRow("Goal saver", "Rainy day fund progress", "62%", Icons.Outlined.Savings, IDS.Colors.Pressed)
    )

    LazyColumn(
        modifier = Modifier
            .background(IDS.Colors.BackgroundPrimary),
        contentPadding = PaddingValues(
            start = IDS.Spacing.ScreenHorizontal,
            top = IDS.Spacing.ScreenTop,
            end = IDS.Spacing.ScreenHorizontal,
            bottom = IDS.Spacing.Section
        ),
        verticalArrangement = Arrangement.spacedBy(IDS.Spacing.CardGap)
    ) {
        item {
            HomeTopBar()
        }
        item {
            AccountSummaryCard(onNavigateToTransfer = onNavigateToTransfer)
        }
        item {
            QuickActionsRow(actions = quickActions)
        }
        item {
            HomeSectionCard(
                title = "Connected money",
                actionLabel = "Manage"
            ) {
                linkedMoneyRows.forEachIndexed { index, row ->
                    CompactListRow(row = row)
                    if (index != linkedMoneyRows.lastIndex) {
                        Divider(color = IDS.Colors.Divider)
                    }
                }
            }
        }
        item {
            HomeSectionCard(
                title = "For life in Rwanda",
                actionLabel = "More"
            ) {
                serviceRows.forEachIndexed { index, row ->
                    CompactListRow(
                        row = row,
                        onClick = if (row.title == "My spending") onNavigateToSpending else NoOpClick
                    )
                    if (index != serviceRows.lastIndex) {
                        Divider(color = IDS.Colors.Divider)
                    }
                }
            }
        }
        item {
            HomeSectionCard(
                title = "Rewards and savings",
                actionLabel = "View"
            ) {
                rewardRows.forEachIndexed { index, row ->
                    CompactListRow(row = row)
                    if (index != rewardRows.lastIndex) {
                        Divider(color = IDS.Colors.Divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = IDS.Spacing.Inline),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)) {
            Text("Good morning", style = IDS.Typography.BodyMedium)
            Text("Itunda", style = IDS.Typography.Header)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Inline)) {
            TopBarActionButton(icon = Icons.Outlined.Notifications)
            TopBarActionButton(icon = Icons.Outlined.Person)
        }
    }
}

@Composable
private fun TopBarActionButton(icon: ImageVector) {
    IconButton(
        onClick = {},
        modifier = Modifier
            .size(IDS.Size.TopBarAction)
            .clip(CircleShape)
            .background(IDS.Colors.BackgroundSecondary)
    ) {
        Icon(icon, contentDescription = null, tint = IDS.Colors.IconPrimary)
    }
}

@Composable
private fun AccountSummaryCard(onNavigateToTransfer: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IDS.Shapes.Card,
        colors = CardDefaults.cardColors(containerColor = IDS.Colors.RaisedCard),
        elevation = CardDefaults.cardElevation(defaultElevation = IDS.Elevation.Card)
    ) {
        Column(
            modifier = Modifier.padding(IDS.Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IDS.Spacing.CardGap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)) {
                Text("Itunda total balance", style = IDS.Typography.SectionLabel)
                Text("RWF 1,284,350", style = IDS.Typography.LargeAmount)
                Text("Wallet, bank and mobile money in one place", style = IDS.Typography.BodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Inline)
            ) {
                BalanceTile(
                    modifier = Modifier.weight(1f),
                    title = "Main wallet",
                    amount = "RWF 324,000"
                )
                BalanceTile(
                    modifier = Modifier.weight(1f),
                    title = "Spend today",
                    amount = "RWF 18,200"
                )
            }
            Row(
                modifier = Modifier
                    .clip(IDS.Shapes.Pill)
                    .background(IDS.Colors.Pressed)
                    .clickable { onNavigateToTransfer() }
                    .padding(horizontal = IDS.Spacing.CardPadding, vertical = IDS.Spacing.Inline),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
            ) {
                Text("Send money now", style = IDS.Typography.BodyBold.copy(color = IDS.Colors.TextBrand))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = IDS.Colors.TextBrand)
            }
        }
    }
}

@Composable
private fun BalanceTile(modifier: Modifier = Modifier, title: String, amount: String) {
    Column(
        modifier = modifier
            .clip(IDS.Shapes.SectionCard)
            .background(IDS.Colors.BackgroundPrimary)
            .padding(IDS.Spacing.Inline),
        verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
    ) {
        Text(title, style = IDS.Typography.Caption)
        Text(amount, style = IDS.Typography.Metric)
    }
}

@Composable
private fun QuickActionsRow(actions: List<QuickAction>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Inline)
    ) {
        actions.forEach { action ->
            QuickActionItem(
                modifier = Modifier.weight(1f),
                action = action
            )
        }
    }
}

@Composable
private fun QuickActionItem(modifier: Modifier = Modifier, action: QuickAction) {
    Column(
        modifier = modifier
            .clip(IDS.Shapes.SectionCard)
            .background(IDS.Colors.BackgroundSecondary)
            .clickable(onClick = action.onClick)
            .padding(vertical = IDS.Spacing.Inline),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
    ) {
        Box(
            modifier = Modifier
                .size(IDS.Size.QuickActionIcon)
                .clip(IDS.Shapes.IconContainer)
                .background(action.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(action.icon, contentDescription = null, tint = IDS.Colors.IconPrimary)
        }
        Text(
            text = action.title,
            style = IDS.Typography.Caption.copy(color = IDS.Colors.TextSecondary)
        )
    }
}

@Composable
private fun HomeSectionCard(
    title: String,
    actionLabel: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IDS.Shapes.SectionCard,
        colors = CardDefaults.cardColors(containerColor = IDS.Colors.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = IDS.Elevation.Card)
    ) {
        Column(
            modifier = Modifier.padding(IDS.Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IDS.Spacing.RowGap),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = IDS.Typography.Title)
                    Text(actionLabel, style = IDS.Typography.BodyMedium.copy(color = IDS.Colors.TextTertiary))
                }
                content()
            }
        )
    }
}

@Composable
private fun CompactListRow(row: HomeRow, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IDS.Shapes.Button)
            .clickable(onClick = onClick)
            .padding(vertical = IDS.Spacing.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Inline)
    ) {
        Box(
            modifier = Modifier
                .size(IDS.Size.RowIcon)
                .clip(IDS.Shapes.IconContainer)
                .background(row.iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(row.icon, contentDescription = null, tint = IDS.Colors.IconPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)) {
            Text(row.title, style = IDS.Typography.BodyBold)
            Text(row.subtitle, style = IDS.Typography.Caption)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
        ) {
            Text(row.trailing, style = IDS.Typography.BodyMedium.copy(color = IDS.Colors.TextSecondary))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = IDS.Colors.TextTertiary)
        }
    }
}
