package rw.itunda.core.network

/**
 * Toss-Style Server-Driven UI (SDUI) Response Model
 * The server decides what UI components to render and their data.
 */
data class SduiResponse(
    val screenId: String,
    val version: String,
    val components: List<SduiComponent>
)

data class SduiComponent(
    val type: String, // e.g., "HEADER", "BALANCE_CARD", "TRANSFER_LIST"
    val data: Map<String, Any>, // The dynamic data for the component
    val actions: List<SduiAction>? = null // Intents when the component is tapped
)

data class SduiAction(
    val actionType: String, // e.g., "NAVIGATE", "API_CALL", "DEEP_LINK"
    val payload: Map<String, String>
)

/**
 * BFF (Backend For Frontend) Client Interface
 * Abstracts the network layer so features don't talk directly to microservices,
 * but instead talk to a unified BFF that provides SDUI or aggregated JSON.
 */
interface BffClient {
    /**
     * Fetches the Server-Driven UI layout and data for a specific screen.
     */
    suspend fun getScreen(screenName: String, params: Map<String, String> = emptyMap()): Result<SduiResponse>

    /**
     * Standard API execution for specific feature intentions.
     */
    suspend fun executeAction(action: SduiAction): Result<Map<String, Any>>
}
