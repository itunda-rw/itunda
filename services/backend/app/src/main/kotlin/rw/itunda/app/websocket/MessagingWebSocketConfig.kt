package rw.itunda.app.websocket

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean
import org.springframework.web.util.UriComponentsBuilder
import rw.itunda.auth.JwtService
import rw.itunda.auth.TokenBlocklistService

@Configuration
@EnableWebSocket
class MessagingWebSocketConfig(
    private val messagingWebSocketHandler: MessagingWebSocketHandler,
    private val jwtService: JwtService,
    private val tokenBlocklistService: TokenBlocklistService,
    @Value("\${itunda.cors.allowed-origins:http://localhost:5000,http://localhost:5001,http://localhost:5002,http://localhost:5003,http://localhost:5004,http://localhost:5005}")
    private val allowedOrigins: List<String>,
    @Value("\${itunda.websocket.max-text-message-bytes:65536}")
    private val maxTextMessageBytes: Int,
) : WebSocketConfigurer {
    /**
     * Put a hard ceiling on a complete WebSocket text frame before it reaches the
     * JSON parser. The only client-to-server message currently supported is a
     * small typing event, so accepting multi-megabyte frames only creates a
     * memory-exhaustion path. Keep this deliberately configurable for a future
     * protocol change, but fail fast on an unsafe deployment value.
     */
    @org.springframework.context.annotation.Bean
    fun webSocketContainer(): ServletServerContainerFactoryBean {
        require(maxTextMessageBytes in 1..1_048_576) {
            "itunda.websocket.max-text-message-bytes must be between 1 and 1048576"
        }
        return ServletServerContainerFactoryBean().apply {
            setMaxTextMessageBufferSize(maxTextMessageBytes)
        }
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(messagingWebSocketHandler, "/ws/messaging")
            .addInterceptors(JwtHandshakeInterceptor(jwtService, tokenBlocklistService))
            // A JWT protects the user identity, but the browser Origin still needs an
            // explicit allowlist to prevent cross-site WebSocket handshakes. Keep this
            // aligned with SecurityConfig's REST CORS property.
            .setAllowedOrigins(*allowedOrigins.toTypedArray())
    }
}

/**
 * Verifies the same real JWT this backend's REST API already trusts, carried as a
 * `?token=` query param -- a native WebSocket client (browser `WebSocket`, OkHttp,
 * `URLSessionWebSocketTask`) cannot set a custom `Authorization` header on the
 * handshake request the way a normal HTTP call can, so a query param is the standard,
 * honest way real WebSocket APIs solve this (the same approach Socket.IO/most
 * real-time SDKs use). Runs before Spring Security's own filter chain would otherwise
 * 401 the handshake (see SecurityConfig's permitAll rule for the ws path -- this
 * interceptor IS the real auth check for that path, not a bypass of one).
 */
private class JwtHandshakeInterceptor(
    private val jwtService: JwtService,
    private val tokenBlocklistService: TokenBlocklistService,
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val token = UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst("token") ?: return false
        val decoded = jwtService.verify(token) ?: return false
        // Match JwtAuthenticationFilter: a signature-valid token that was revoked at
        // logout must not regain access through the WebSocket handshake path.
        if (decoded.isRefresh || tokenBlocklistService.isBlacklisted(decoded.jti)) return false
        attributes[WS_USER_ID_ATTR] = decoded.userId
        attributes[WS_TOKEN_ID_ATTR] = decoded.jti
        attributes[WS_TOKEN_EXPIRES_AT_ATTR] = decoded.expiresAt
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit
}
