package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.app.network.NotificationDto
import rw.itunda.core.designsystem.theme.Ids

/**
 * Real account settings screen, matching the real Toss reference screenshots
 * (user-provided, 2026-07-12): "내 정보" (my info -- real name/phone from
 * /api/v1/auth/profile), a real notifications list (/api/v1/notifications, with
 * mark-as-read already real on the backend, just never surfaced anywhere), and
 * logout. "보안"-style rows (인증수단/안심차단서비스 등) are intentionally NOT rendered
 * as fake interactive rows -- there's no real 2FA/device-management backend behind
 * them yet, and a tappable row that goes nowhere is worse than not claiming the
 * feature at all.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSettingsData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = Ids.colors.textPrimary)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Text("My info", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.chip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Ids.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            profile?.let { "${it.firstName} ${it.lastName}" } ?: "—",
                            color = Ids.colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(profile?.phoneNumber ?: "", color = Ids.colors.textTertiary, fontSize = 14.sp)
                    }
                }
                androidx.compose.material3.Divider(color = Ids.colors.divider)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notifications", color = Ids.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (unreadCount > 0) {
                        Text(
                            "Mark all read",
                            color = Ids.colors.brand,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { viewModel.markAllNotificationsRead() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (notifications.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No notifications", color = Ids.colors.textTertiary, fontSize = 14.sp)
                    }
                }
            } else {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(notification, onClick = { viewModel.markNotificationRead(notification.id) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogout)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, tint = Ids.colors.danger)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Log out", color = Ids.colors.danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !notification.isRead, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (notification.isRead) Ids.colors.chip else Ids.colors.pressed),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ids.colors.textPrimary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                notification.title,
                color = Ids.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold
            )
            Text(notification.body, color = Ids.colors.textTertiary, fontSize = 13.sp)
        }
    }
}
