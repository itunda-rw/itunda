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
import rw.itunda.core.designsystem.theme.Tds

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
        QuickAction("Transfer", Icons.Outlined.ArrowForward, Tds.colors.successTint, onNavigateToTransfer),
        QuickAction("Bills", Icons.Outlined.ReceiptLong, Tds.colors.warningTint, {}),
        QuickAction("MoMo", Icons.Outlined.Smartphone, Tds.colors.pressed, {}),
        QuickAction("Savings", Icons.Outlined.Savings, Tds.colors.dangerTint, {})
    )
    val linkedMoneyRows = listOf(
        HomeRow("BK Bank account", "Salary and card settlement", "RWF 842,000", Icons.Outlined.AccountBalance, Tds.colors.chip),
        HomeRow("MTN MoMo", "Daily spending wallet", "RWF 118,400", Icons.Outlined.Smartphone, Tds.colors.successTint),
        HomeRow("Airtel Money", "Backup cash-out line", "Connected", Icons.Outlined.Payments, Tds.colors.warningTint)
    )
    val serviceRows = listOf(
        HomeRow("Pay CashPower", "Top up electricity instantly", "Open", Icons.Outlined.ReceiptLong, Tds.colors.warningTint),
        HomeRow("Irembo services", "Government and document payments", "Browse", Icons.Outlined.AccountBalance, Tds.colors.pressed),
        HomeRow("My spending", "View monthly categories and trends", "See all", Icons.Outlined.AccountBalanceWallet, Tds.colors.chip)
    )
    val rewardRows = listOf(
        HomeRow("Itunda rewards", "Claim today's cashback and offers", "140 RWF", Icons.Outlined.Payments, Tds.colors.successTint),
        HomeRow("Goal saver", "Rainy day fund progress", "62%", Icons.Outlined.Savings, Tds.colors.pressed)
    )

    LazyColumn(
        modifier = Modifier
            .background(Tds.colors.background),
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
                        Divider(color = Tds.colors.divider)
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
                        Divider(color = Tds.colors.divider)
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
                        Divider(color = Tds.colors.divider)
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
            Text("Good morning", style = IDS.Typography.BodyMedium, color = Tds.colors.textSecondary)
            Text("Itunda", style = IDS.Typography.Header, color = Tds.colors.textPrimary)
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
            .background(Tds.colors.surface)
    ) {
        Icon(icon, contentDescription = null, tint = Tds.colors.iconPrimary)
    }
}

@Composable
private fun AccountSummaryCard(onNavigateToTransfer: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = IDS.Shapes.Card,
        colors = CardDefaults.cardColors(containerColor = Tds.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = IDS.Elevation.Card)
    ) {
        Column(
            modifier = Modifier.padding(IDS.Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(IDS.Spacing.CardGap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)) {
                Text("Itunda total balance", style = IDS.Typography.SectionLabel, color = Tds.colors.textSecondary)
                Text("RWF 1,284,350", style = IDS.Typography.LargeAmount, color = Tds.colors.textPrimary)
                Text("Wallet, bank and mobile money in one place", style = IDS.Typography.BodyMedium, color = Tds.colors.textSecondary)
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
                    .background(Tds.colors.pressed)
                    .clickable { onNavigateToTransfer() }
                    .padding(horizontal = IDS.Spacing.CardPadding, vertical = IDS.Spacing.Inline),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
            ) {
                Text("Send money now", style = IDS.Typography.BodyBold.copy(color = Tds.colors.textBrand))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Tds.colors.textBrand)
            }
        }
    }
}

@Composable
private fun BalanceTile(modifier: Modifier = Modifier, title: String, amount: String) {
    Column(
        modifier = modifier
            .clip(IDS.Shapes.SectionCard)
            .background(Tds.colors.background)
            .padding(IDS.Spacing.Inline),
        verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
    ) {
        Text(title, style = IDS.Typography.Caption, color = Tds.colors.textTertiary)
        Text(amount, style = IDS.Typography.Metric, color = Tds.colors.textPrimary)
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
            .background(Tds.colors.surface)
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
            Icon(action.icon, contentDescription = null, tint = Tds.colors.iconPrimary)
        }
        Text(
            text = action.title,
            style = IDS.Typography.Caption.copy(color = Tds.colors.textSecondary)
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
        colors = CardDefaults.cardColors(containerColor = Tds.colors.surface),
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
                    Text(title, style = IDS.Typography.Title, color = Tds.colors.textPrimary)
                    Text(actionLabel, style = IDS.Typography.BodyMedium.copy(color = Tds.colors.textTertiary))
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
            Icon(row.icon, contentDescription = null, tint = Tds.colors.iconPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)) {
            Text(row.title, style = IDS.Typography.BodyBold, color = Tds.colors.textPrimary)
            Text(row.subtitle, style = IDS.Typography.Caption, color = Tds.colors.textTertiary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IDS.Spacing.Tight)
        ) {
            Text(row.trailing, style = IDS.Typography.BodyMedium.copy(color = Tds.colors.textSecondary))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Tds.colors.textTertiary)
        }
    }
}
