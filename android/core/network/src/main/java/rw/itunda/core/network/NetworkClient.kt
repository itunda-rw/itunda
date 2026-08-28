package rw.itunda.core.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Real fix (2026-08-26): split out of ApiService.kt once that file grew past its
// file-size-lint baseline -- ApiService.kt is the DTOs + Retrofit interface
// definitions; this file is the concrete client singleton (OkHttp/Retrofit/
// WebSocket wiring) plus its own small cluster of error-classification helpers,
// a distinct concern that doesn't belong crammed at the end of a model file.
// Real device binding (2026-07-21) -- shared, public so any money-moving call site
// can check it, not just MainViewModel's original three (sendTransfer/
// depositToSavingsGoal/claimInterest). A 403 alone isn't enough (other real 403s
// exist elsewhere in this backend); the real `ApiError.code` field in the response
// body is what DeviceVerificationFilter actually sets, so that's what's checked.
// Mirrors bank-mfe's own `err.code === 'DEVICE_NOT_VERIFIED'` check on its ApiError
// exactly.
fun isDeviceNotVerifiedError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "DEVICE_NOT_VERIFIED"
    } catch (_: Exception) {
        false
    }
}

// Same pattern as isDeviceNotVerifiedError above -- CertificateController's
// /certificate/issue real-403s with code KYC_REQUIRED when the caller's identity
// isn't verified yet (CertificateUserNotVerifiedException), so CreditScoreScreen/
// CertificateScreen can show a real, specific message instead of a generic failure.
fun isKycRequiredError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "KYC_REQUIRED"
    } catch (_: Exception) {
        false
    }
}

// Generic form of isDeviceNotVerifiedError/isKycRequiredError above, for call sites
// (like the Youth account's birth-date/age gate, 2026-07-28) that need to distinguish
// between multiple real ApiError codes on the same HTTP status rather than just a
// single yes/no check.
fun apiErrorCode(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString
} catch (_: Exception) {
    null
}

// Same pattern as apiErrorCode above, for the `message` field of core.web.ApiError --
// found missing 2026-08-08 (Toss Simplicity21 "adding innovation upon innovation"
// research pass, checking whether P2P transfer's own mature/assumed-solid error
// handling actually was): bank-mfe's ApiError already parses and shows this real
// backend text (lib/api.ts), but MainViewModel.backendErrorMessage's status-code-only
// switch meant a account-frozen/family-spend-limit/self-payment/rate-limit decline all
// fell through to a generic "Something went wrong" on Android (and iOS, mirrored) --
// distinct backend errors the sender could otherwise never tell apart.
fun apiErrorMessage(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("message")?.asString
} catch (_: Exception) {
    null
}

// Real, minimal fire-and-forget analytics helper (2026-08-10) -- same "a failed
// best-effort call must never surface to the user or block the real action it's
// attached to" reasoning SessionManager.registerDeviceToken already establishes.
suspend fun recordAnalyticsEvent(eventName: String, metadata: String? = null) {
    try {
        NetworkClient.apiService.recordAnalyticsEvent(RecordAnalyticsEventRequest(eventName, metadata = metadata))
    } catch (_: Exception) {
        // Best-effort, see doc comment above.
    }
}

// Network Client Singleton
object NetworkClient {
    // Was hardcoded to "http://10.0.2.2:8080/" -- the emulator-only loopback alias, at
    // the wrong port (services/backend listens on 4001). Real base URL is now passed
    // in from :app's own real BuildConfig.API_BASE_URL via init() below (2026-07-22
    // change, made while relocating this whole file from :app to :core:network so
    // Feature modules can depend on it directly -- a Gradle library module can't read
    // an application module's BuildConfig, and passing the value in at runtime avoids
    // needing to relocate the buildConfigField/`-PapiBaseUrl=` override plumbing too).
    // Overridable at build time for a physical device -- see app/build.gradle.kts's
    // apiBaseUrl comment (2026-07-11 fix, still the actual place that's set).
    private var BASE_URL: String = "http://10.0.2.2:4001/"

    // Must be initialized once, from ItundaApplication.onCreate(), before any request
    // fires -- see that file. Held nullable rather than lateinit so a request made
    // before init() (which should never happen, but interceptors must never crash the
    // whole app over it) just goes out unauthenticated instead of throwing.
    private var tokenStore: TokenStore? = null

    // Real device binding (2026-07-21 port) -- same nullable-not-lateinit reasoning
    // as tokenStore above.
    private var deviceStore: DeviceStore? = null

    fun init(context: Context, baseUrl: String) {
        BASE_URL = baseUrl
        tokenStore = TokenStore(context.applicationContext)
        deviceStore = DeviceStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    fun currentDeviceStore(): DeviceStore =
        deviceStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    // Real bearer-token injection (2026-07-11) -- previously commented out entirely
    // (see this file's git history / the removed "TODO: Inject Token" line), which is
    // exactly the gap SaroniteBridge.kt's ItundaSaroniteHostBridge.getAuthToken()
    // named as the reason it honestly returned null instead of a fabricated token.
    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore?.getAccessToken()
        val newRequest = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        chain.proceed(newRequest)
    }

    private val diagnosticLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        android.util.Log.d("ITUNDA_NET", "--> ${request.method} ${request.url}")
        try {
            val response = chain.proceed(request)
            android.util.Log.d("ITUNDA_NET", "<-- ${response.code} ${request.url}")
            response
        } catch (e: Exception) {
            android.util.Log.e("ITUNDA_NET", "<-- FAILED ${request.url}: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    // Real silent session-refresh (2026-08-15) -- a dead-endpoint sweep found
    // AuthApi.refresh() had ZERO callers anywhere in this app, meaning an expired 24h
    // access token (JwtService's real expiry) just surfaced as a raw, unrecoverable
    // 401 on every subsequent screen with no path forward. Toss-style: resolve it for
    // the user invisibly rather than forcing a re-login the moment a session merely
    // aged out, same philosophy as the AlreadyX resolve-forward fixes made the same
    // day. Standard OkHttp Authenticator pattern -- runs synchronously on a background
    // thread (same constraint as authInterceptor above), so refreshSync's blocking
    // Call is used, not the suspend fun.
    private val refreshLock = Any()

    private fun responseChainLength(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private val refreshAuthenticator = okhttp3.Authenticator { _, response ->
        // Already retried this exact request once with a fresh token and still got a
        // 401 -- the refresh token itself is the problem (expired/blacklisted), not
        // just the access token. Give up rather than looping forever.
        if (responseChainLength(response) >= 2) return@Authenticator null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        synchronized(refreshLock) {
            val currentToken = tokenStore?.getAccessToken()
            // Another in-flight request already refreshed while this one waited for
            // the lock -- just retry with the token that's now stored, no redundant
            // network call.
            if (currentToken != null && currentToken != failedToken) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }
            val refreshToken = tokenStore?.getRefreshToken() ?: return@Authenticator null
            val refreshed = try {
                authApi.refreshSync(RefreshRequest(refreshToken)).execute()
            } catch (_: Exception) {
                null
            }
            val body = refreshed?.takeIf { it.isSuccessful }?.body()
            if (body == null) {
                // Refresh token itself is invalid/expired (7-day expiry, or already
                // consumed) -- a real logout, not a dead end: SessionManager's own
                // real login screen takes over from here instead of every remaining
                // screen showing raw 401s forever.
                SessionManager.forceLocalLogout()
                return@Authenticator null
            }
            tokenStore?.saveSession(body.user.id, body.accessToken, body.refreshToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer ${body.accessToken}")
                .build()
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(diagnosticLoggingInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(refreshAuthenticator)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val hoodApi: HoodApi by lazy { retrofit.create(HoodApi::class.java) }

    // Real WebSocket live-transport (2026-07-18) -- see
    // rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment for the real
    // backend push shape this mirrors exactly. Ported from bank-mfe's own
    // connectMessagingSocket, which established this session's push-payload contract.
    // Now routes both "message" (1:1) and "group_message" pushes -- group chat gained a
    // real mobile UI the same day this was extended.
    private val gson = Gson()

    fun connectMessagingSocket(onPush: (MessagingSocketPush) -> Unit): WebSocket {
        val token = tokenStore?.getAccessToken().orEmpty()
        val wsUrl = BASE_URL.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "ws/messaging?token=$token"
        val request = Request.Builder().url(wsUrl).build()
        return okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        when (json.get("type")?.asString) {
                            "message" -> onPush(MessagingSocketPush.DirectMessage(gson.fromJson(json.get("message"), MessageDto::class.java)))
                            "group_message" -> {
                                val groupId = json.get("groupConversationId")?.asString ?: return
                                onPush(MessagingSocketPush.GroupMessagePush(groupId, gson.fromJson(json.get("message"), GroupMessageDto::class.java)))
                            }
                            "presence" -> {
                                val userId = json.get("userId")?.asString ?: return
                                val online = json.get("online")?.asBoolean ?: return
                                onPush(MessagingSocketPush.PresenceChange(userId, online))
                            }
                            "typing" -> {
                                val userId = json.get("userId")?.asString ?: return
                                onPush(
                                    MessagingSocketPush.TypingChange(
                                        conversationId = json.get("conversationId")?.asString,
                                        groupConversationId = json.get("groupConversationId")?.asString,
                                        userId = userId,
                                    ),
                                )
                            }
                            "reaction" -> {
                                val messageId = json.get("messageId")?.asString ?: return
                                val reactionsType = object : com.google.gson.reflect.TypeToken<List<ReactionGroupDto>>() {}.type
                                val reactions: List<ReactionGroupDto> = gson.fromJson(json.get("reactions"), reactionsType)
                                onPush(
                                    MessagingSocketPush.ReactionChange(
                                        conversationId = json.get("conversationId")?.asString,
                                        groupConversationId = json.get("groupConversationId")?.asString,
                                        messageId = messageId,
                                        reactions = reactions,
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Real, non-critical -- a malformed/unexpected push shouldn't
                        // crash the socket listener; the 4s poll stays as the real
                        // fallback delivery path regardless.
                    }
                }
            },
        )
    }

    // Real typing indicator send (2026-07-19) -- best-effort, matching bank-mfe's own
    // sendTyping helper; OkHttp's WebSocket.send already silently no-ops on a
    // closed/never-connected socket.
    fun sendTyping(socket: WebSocket, conversationId: String? = null, groupConversationId: String? = null) {
        val payload = mutableMapOf<String, Any>("type" to "typing")
        conversationId?.let { payload["conversationId"] = it }
        groupConversationId?.let { payload["groupConversationId"] = it }
        socket.send(gson.toJson(payload))
    }
}

sealed class MessagingSocketPush {
    data class DirectMessage(val message: MessageDto) : MessagingSocketPush()
    data class GroupMessagePush(val groupConversationId: String, val message: GroupMessageDto) : MessagingSocketPush()
    // Real online/offline presence (2026-07-19) -- see
    // rw.itunda.core.realtime.RealtimeMessagePublisher.publishPresenceChange's own doc
    // comment for the real transition-only/1:1-only scoping.
    data class PresenceChange(val userId: String, val online: Boolean) : MessagingSocketPush()
    // Real typing indicator (2026-07-19) -- see
    // MessagingWebSocketHandler.handleTextMessage's own doc comment on the backend.
    // Ephemeral, never persisted; server-ratelimited to one relay per (user,
    // conversation) per 2s. Exactly one of conversationId/groupConversationId is set.
    data class TypingChange(val conversationId: String?, val groupConversationId: String?, val userId: String) : MessagingSocketPush()
    // Real live reaction push (2026-07-19) -- see
    // MessagingWebSocketHandler.publishReactionChange/publishGroupReactionChange's own
    // doc comments. Exactly one of conversationId/groupConversationId is set.
    data class ReactionChange(
        val conversationId: String?, val groupConversationId: String?, val messageId: String, val reactions: List<ReactionGroupDto>,
    ) : MessagingSocketPush()
}
