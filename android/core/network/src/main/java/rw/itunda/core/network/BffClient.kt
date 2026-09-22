package rw.itunda.core.network

import rw.itunda.core.sdui.SduiAction
import rw.itunda.core.sdui.SduiResponse

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