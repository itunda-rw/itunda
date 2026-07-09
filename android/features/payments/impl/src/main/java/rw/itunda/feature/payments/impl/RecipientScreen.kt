package rw.itunda.feature.payments.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.ids.IDS
import rw.itunda.core.designsystem.theme.Tds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientScreen(
    onBack: () -> Unit = {},
    onRecipientSelected: (name: String, detail: String) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tds.colors.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Back",
                style = IDS.Typography.BodyMedium,
                color = Tds.colors.textSecondary,
                modifier = Modifier.clickable { onBack() }.padding(end = 16.dp)
            )
            Text(
                text = "Send Money",
                style = IDS.Typography.Header,
                color = Tds.colors.textPrimary
            )
        }

        // Search Bar (Like Toss)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search name, account, or phone") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = Tds.colors.surface,
                unfocusedBorderColor = Tds.colors.surface,
                focusedBorderColor = Tds.colors.brand
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Categories / Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton("My Accounts")
            QuickActionButton("MTN MoMo")
            QuickActionButton("Airtel")
            QuickActionButton("Bank")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Recent Contacts",
            style = IDS.Typography.BodyBold,
            color = Tds.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Recent Contacts List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val contacts = listOf(
                Pair("Jean de Dieu", "078 123 4567"),
                Pair("Marie Claire", "BK - 0001234567"),
                Pair("Landlord", "Airtel - 073 987 6543")
            )

            items(contacts.size) { index ->
                val contact = contacts[index]
                ContactItem(
                    name = contact.first,
                    detail = contact.second,
                    onClick = { onRecipientSelected(contact.first, contact.second) }
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(title: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Tds.colors.surface)
            .clickable { /* Handle action */ }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = IDS.Typography.BodyMedium,
            color = Tds.colors.textPrimary
        )
    }
}

@Composable
fun ContactItem(name: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Tds.colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.first().toString(),
                style = IDS.Typography.Title,
                color = Tds.colors.brand
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(text = name, style = IDS.Typography.BodyBold, color = Tds.colors.textPrimary)
            Text(text = detail, style = IDS.Typography.BodyMedium, color = Tds.colors.textSecondary)
        }
    }
}
