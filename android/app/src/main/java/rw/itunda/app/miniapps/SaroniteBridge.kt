package rw.itunda.app.miniapps

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ViewManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real, app-embedded copy of the pattern proven standalone in
 * packages/saronite/packages/brownfield-module (see that module's own
 * build.gradle.kts -- real Gradle project, genuinely compiles in isolation).
 * This is the first time it's actually wired into a real, launchable itunda
 * app rather than just verified in isolation -- see ARCHITECTURE.md §2/§8.
 *
 * itunda's app has no login flow yet (NetworkClient.kt has a literal
 * `// TODO: Inject Token` where the auth header would go) -- getAuthToken()
 * honestly returns null below rather than a fabricated token, which means
 * every mini-app data call below will hit the real SARONITE_NOT_AUTHENTICATED
 * path until a real session exists. That's a correct, real failure mode, not
 * a bug to hide.
 */
class ItundaSaroniteHostBridge : SaroniteHostBridge {
    override fun getApiBaseUrl(): String = "http://10.0.2.2:8080/"
    override fun getAuthToken(): String? = null
    override fun getSchemeUri(): String = "itunda://saronite"
    override fun onCloseView(activity: Activity?) {
        activity?.finish()
    }
}

interface SaroniteHostBridge {
    fun getApiBaseUrl(): String
    fun getAuthToken(): String?
    fun getSchemeUri(): String
    fun onCloseView(activity: Activity?)
}

class SaroniteBrownfieldModule(
    reactContext: ReactApplicationContext,
    private val hostBridge: SaroniteHostBridge,
) : ReactContextBaseJavaModule(reactContext) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun getName(): String = "SaroniteBrownfieldModule"

    override fun getConstants(): MutableMap<String, Any> =
        mutableMapOf("schemeUri" to hostBridge.getSchemeUri())

    @ReactMethod
    fun closeView(promise: Promise) {
        try {
            hostBridge.onCloseView(currentActivity)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SARONITE_CLOSE_VIEW_FAILED", e)
        }
    }

    @ReactMethod
    fun openURL(url: String, promise: Promise) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactApplicationContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SARONITE_OPEN_URL_FAILED", e)
        }
    }

    @ReactMethod
    fun getWalletBalance(promise: Promise) {
        authorizedCall(get("api/v1/wallet"), promise, ::parseWalletBalance)
    }

    @ReactMethod
    fun getPendingBills(promise: Promise) {
        authorizedCall(get("bills/pending"), promise, ::parsePendingBills)
    }

    @ReactMethod
    fun payBill(billId: String, amount: Double, accountNumber: String, provider: String, promise: Promise) {
        val body = JsonObject().apply {
            addProperty("billId", billId)
            addProperty("amount", amount)
            addProperty("accountNumber", accountNumber)
            addProperty("provider", provider)
        }
        authorizedCall(post("bills/pay", body), promise, ::parsePayBillResult)
    }

    @ReactMethod
    fun getRewardTasks(promise: Promise) {
        authorizedCall(get("rewards/tasks"), promise, ::parseRewardTasks)
    }

    @ReactMethod
    fun claimRewardTask(taskId: String, promise: Promise) {
        val body = JsonObject().apply { addProperty("taskId", taskId) }
        authorizedCall(post("rewards/claim", body), promise, ::parseClaimRewardResult)
    }

    private fun get(path: String): Request.Builder? {
        val token = hostBridge.getAuthToken() ?: return null
        return Request.Builder()
            .url("${hostBridge.getApiBaseUrl()}$path")
            .header("Authorization", "Bearer $token")
            .get()
    }

    private fun post(path: String, body: JsonObject): Request.Builder? {
        val token = hostBridge.getAuthToken() ?: return null
        val requestBody = body.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("${hostBridge.getApiBaseUrl()}$path")
            .header("Authorization", "Bearer $token")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post(requestBody)
    }

    private fun authorizedCall(
        requestBuilder: Request.Builder?,
        promise: Promise,
        parse: (String) -> WritableMap,
    ) {
        if (requestBuilder == null) {
            promise.reject("SARONITE_NOT_AUTHENTICATED", "No active itunda session")
            return
        }

        httpClient.newCall(requestBuilder.build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                promise.reject("SARONITE_NETWORK_ERROR", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val body = it.body?.string()
                    if (!it.isSuccessful || body == null) {
                        promise.reject("SARONITE_HTTP_ERROR", "itunda API returned ${it.code}: ${body ?: ""}")
                        return
                    }
                    try {
                        promise.resolve(parse(body))
                    } catch (e: Exception) {
                        promise.reject("SARONITE_PARSE_ERROR", e)
                    }
                }
            }
        })
    }

    private fun parseWalletBalance(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val result = Arguments.createMap()
        val wallets = Arguments.createArray()
        root.getAsJsonArray("wallets")?.forEach { element ->
            val w = element.asJsonObject
            val walletMap = Arguments.createMap()
            walletMap.putString("id", w.get("id").asString)
            walletMap.putDouble("balance", w.get("balance").asDouble)
            walletMap.putString("currency", w.get("currency").asString)
            wallets.pushMap(walletMap)
        }
        result.putArray("wallets", wallets)
        return result
    }

    private fun parsePendingBills(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val bills = Arguments.createArray()
        root.getAsJsonArray("bills")?.forEach { element ->
            val b = element.asJsonObject
            val billMap = Arguments.createMap()
            billMap.putString("id", b.get("id").asString)
            billMap.putString("provider", b.get("provider").asString)
            billMap.putDouble("amount", b.get("amount").asDouble)
            bills.pushMap(billMap)
        }
        val result = Arguments.createMap()
        result.putArray("bills", bills)
        return result
    }

    private fun parsePayBillResult(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val result = Arguments.createMap()
        result.putString("message", root.get("message")?.asString ?: "Bill payment successful")
        return result
    }

    private fun parseRewardTasks(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val tasks = Arguments.createArray()
        root.getAsJsonArray("tasks")?.forEach { element ->
            val t = element.asJsonObject
            val taskMap = Arguments.createMap()
            taskMap.putString("id", t.get("id").asString)
            taskMap.putString("title", t.get("title").asString)
            taskMap.putDouble("rewardAmount", t.get("rewardAmount").asDouble)
            tasks.pushMap(taskMap)
        }
        val result = Arguments.createMap()
        result.putArray("tasks", tasks)
        return result
    }

    private fun parseClaimRewardResult(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val result = Arguments.createMap()
        result.putString("message", root.get("message")?.asString ?: "Reward claimed")
        return result
    }

    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}
}

class SaronitePackage(private val hostBridge: SaroniteHostBridge) : ReactPackage {
    override fun createNativeModules(
        reactContext: ReactApplicationContext,
    ): List<NativeModule> = listOf(SaroniteBrownfieldModule(reactContext, hostBridge))

    override fun createViewManagers(
        reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = emptyList()
}
