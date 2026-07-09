package rw.itunda.core.designsystem.sdui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rw.itunda.core.designsystem.components.TdsButton
import rw.itunda.core.designsystem.components.TdsListRow
import rw.itunda.core.network.SduiComponent

/**
 * SDUI Renderer
 * Takes Server-Driven UI components and maps them to Toss Design System (TDS) composables.
 */
@Composable
fun SduiRenderer(components: List<SduiComponent>, onAction: (String, Map<String, String>) -> Unit) {
    Column {
        components.forEach { component ->
            when (component.type) {
                "BUTTON" -> {
                    val text = component.data["text"] as? String ?: "Button"
                    val action = component.actions?.firstOrNull()
                    TdsButton(
                        text = text,
                        onClick = {
                            if (action != null) {
                                onAction(action.actionType, action.payload)
                            }
                        }
                    )
                }
                "LIST_ROW" -> {
                    val title = component.data["title"] as? String ?: ""
                    val subtitle = component.data["subtitle"] as? String
                    val rightText = component.data["rightText"] as? String
                    val action = component.actions?.firstOrNull()
                    TdsListRow(
                        title = title,
                        subtitle = subtitle,
                        rightText = rightText,
                        onClick = {
                            if (action != null) {
                                onAction(action.actionType, action.payload)
                            }
                        }
                    )
                }
                "SPACER" -> {
                    val size = (component.data["size"] as? Number)?.toInt() ?: 16
                    Spacer(modifier = Modifier.height(size.dp))
                }
                else -> {
                    // Fallback for unknown components
                    Text(text = "Unknown component: ${component.type}")
                }
            }
        }
    }
}
