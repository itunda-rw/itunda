package rw.itunda.app.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder
import rw.itunda.auth.JwtService

@Configuration
@EnableWebSocket
class MessagingWebSocketConfig(
    private val messagingWebSocketHandler: MessagingWebSocketHandler,
    private val jwtService: JwtService,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(messagingWebSocketHandler, "/ws/messaging")
            .addInterceptors(JwtHandshakeInterceptor(jwtService))
            // Same permissive-origin posture SecurityConfig's CORS bean documents this
            // repo not having settled on a fixed production origin list yet -- see that
            // class's own comment.
            .setAllowedOriginPatterns("*")
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
private class JwtHandshakeInterceptor(private val jwtService: JwtService) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val token = UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst("token") ?: return false
        val decoded = jwtService.verify(token) ?: return false
        if (decoded.isRefresh) return false
        attributes[WS_USER_ID_ATTR] = decoded.userId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit
}
