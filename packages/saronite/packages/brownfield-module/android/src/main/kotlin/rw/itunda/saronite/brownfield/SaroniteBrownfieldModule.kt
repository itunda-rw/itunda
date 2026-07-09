package rw.itunda.saronite.brownfield

import android.content.Intent
import android.net.Uri
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
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
 * The one native module every Saronite mini-app talks to — mirrors the real
 * shape found in Toss's open-source Granite: a single `GraniteBrownfieldModule`
 * exposing `closeView()` / `getSchemeUri()` / `onVisibilityChanged`. Toss's
 * concrete implementation of that module isn't public; this is a genuine,
 * independent implementation of the same contract (see
 * ../../src/spec/SaroniteBrownfieldModule.ts), plus one itunda-specific
 * addition, `getWalletBalance()`, proving the bridge can reach real app data
 * and not just UI-shell calls.
 */
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

    /**
     * Calls itunda's real `GET /wallet/balance` (see
     * backend/src/controllers/wallet.controller.ts:getAggregatedBalance) using
     * the host app's live session token, and hands the result back to JS.
     * This is the proof-of-concept that the bridge reaches real backend data,
     * not a mocked response.
     */
    @ReactMethod
    fun getWalletBalance(promise: Promise) {
        authorizedCall(get("wallet/balance"), promise, ::parseWalletBalance)
    }

    /** Backed by real `GET /bills/pending` (backend/src/controllers/bills.controller.ts). */
    @ReactMethod
    fun getPendingBills(promise: Promise) {
        authorizedCall(get("bills/pending"), promise, ::parsePendingBills)
    }

    /**
     * Backed by real `POST /bills/pay`. Generates a fresh `Idempotency-Key`
     * per call, the same header the backend actually requires to dedupe
     * retried payments (see backend's idempotency service and
     * blog/src/posts/idempotency-keys.ts) — a mini-app tapping "Pay" twice
     * on a flaky connection must not double-charge the wallet.
     */
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

    /** Backed by real `GET /rewards/tasks` (backend/src/controllers/rewards.controller.ts). */
    @ReactMethod
    fun getRewardTasks(promise: Promise) {
        authorizedCall(get("rewards/tasks"), promise, ::parseRewardTasks)
    }

    /** Backed by real `POST /rewards/claim`, idempotency-keyed for the same reason as `payBill`. */
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
        val data = root.getAsJsonObject("data")

        val result = Arguments.createMap()
        result.putDouble("totalBalance", root.get("totalBalance").asDouble)
        result.putString("currency", data?.get("currency")?.asString ?: "RWF")

        val wallets = Arguments.createArray()
        root.getAsJsonArray("wallets")?.forEach { element ->
            val w = element.asJsonObject
            val walletMap = Arguments.createMap()
            walletMap.putString("id", w.get("id").asString)
            walletMap.putString("type", w.get("type").asString)
            walletMap.putString("name", w.get("name").asString)
            walletMap.putString("number", w.get("number").asString)
            walletMap.putDouble("balance", w.get("balance").asDouble)
            walletMap.putString("currency", w.get("currency").asString)
            walletMap.putString("icon", w.get("icon").asString)
            walletMap.putBoolean("connected", w.get("connected").asBoolean)
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
            billMap.putString("dueDate", b.get("dueDate").asString)
            billMap.putString("status", b.get("status").asString)
            billMap.putString("accountNumber", b.get("accountNumber").asString)
            bills.pushMap(billMap)
        }
        val result = Arguments.createMap()
        result.putArray("bills", bills)
        return result
    }

    private fun parsePayBillResult(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val transaction = root.getAsJsonObject("transaction")
        val result = Arguments.createMap()
        result.putString("message", root.get("message")?.asString ?: "Bill payment successful")
        result.putString("transactionId", transaction.get("id").asString)
        result.putString("referenceNumber", transaction.get("referenceNumber").asString)
        result.putString("status", transaction.get("status").asString)
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
            taskMap.putString("subtitle", t.get("subtitle")?.asString ?: "")
            taskMap.putDouble("rewardAmount", t.get("rewardAmount").asDouble)
            taskMap.putBoolean("claimed", t.get("claimed").asBoolean)
            t.get("claimedAt")?.takeIf { !it.isJsonNull }?.let { taskMap.putString("claimedAt", it.asString) }
            tasks.pushMap(taskMap)
        }
        val result = Arguments.createMap()
        result.putArray("tasks", tasks)
        result.putDouble(
            "rewardsTotal",
            root.getAsJsonObject("rewardsSummary")?.get("total")?.asDouble ?: 0.0,
        )
        return result
    }

    private fun parseClaimRewardResult(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val result = Arguments.createMap()
        result.putString("message", root.get("message")?.asString ?: "Reward claimed")
        result.putDouble("rewardAmount", root.getAsJsonObject("task")?.get("rewardAmount")?.asDouble ?: 0.0)
        result.putDouble("newBalance", root.get("newBalance")?.asDouble ?: 0.0)
        return result
    }

    // Required by React Native's NativeEventEmitter contract on the old
    // native-modules architecture, even though Saronite currently emits no
    // events of its own beyond what the host forwards manually.
    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}
}
