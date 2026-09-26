package rw.itunda.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IdsSelect(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    disabledOptions: Set<String> = emptySet(),
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(selected ?: label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val optionEnabled = option !in disabledOptions
                DropdownMenuItem(
                    text = { Text(option) },
                    enabled = optionEnabled,
                    onClick = {
                        if (optionEnabled) {
                            expanded = false
                            onSelected(option)
                        }
                    },
                )
            }
        }
    }
}
