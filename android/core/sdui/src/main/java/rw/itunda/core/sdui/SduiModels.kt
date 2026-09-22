package rw.itunda.core.sdui

/**
 * Shared Server-Driven UI contracts.
 *
 * These are transport/domain-neutral contracts so rendering and networking
 * can depend on them without creating a DesignSystem -> Network dependency.
 */
data class SduiResponse(
    val screenId: String,
    val version: String,
    val components: List<SduiComponent>
)

data class SduiComponent(
    val type: String,
    val data: Map<String, Any>,
    val actions: List<SduiAction>? = null
)

data class SduiAction(
    val actionType: String,
    val payload: Map<String, String>
)
