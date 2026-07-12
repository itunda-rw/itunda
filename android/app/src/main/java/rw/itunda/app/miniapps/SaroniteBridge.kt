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
import rw.itunda.app.BuildConfig
import rw.itunda.app.network.NetworkClient
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
 * getAuthToken() now reads the real session TokenStore (2026-07-11, alongside
 * network/SessionManager.kt) instead of honestly returning null -- a mini-app call
 * still hits the real SARONITE_NOT_AUTHENTICATED path if nobody has logged in, which
 * is still a correct, real failure mode, just no longer the *only* one.
 */
class ItundaSaroniteHostBridge : SaroniteHostBridge {
    override fun getApiBaseUrl(): String = BuildConfig.API_BASE_URL
    override fun getAuthToken(): String? = NetworkClient.currentTokenStore().getAccessToken()
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
            // ReactContextBaseJavaModule's inherited currentActivity shortcut is
            // deprecated as of RN 0.80.0 in favor of this explicit form (confirmed
            // directly against react-native's own source, 2026-07-12, granite-
            // adoption stage 3's RN 0.84.0 hop) -- the base-class shortcut stopped
            // resolving as a bare reference at this RN version.
            hostBridge.onCloseView(reactApplicationContext.currentActivity)
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
        // Real bug fixed (2026-07-13): was missing the /api/v1 prefix every other
        // endpoint on this same class already uses correctly (getWalletBalance) --
        // resolved to the wrong URL relative to BuildConfig.API_BASE_URL and 404'd
        // against the real backend (services/backend/bills, @RequestMapping("/api/v1/bills")).
        authorizedCall(get("api/v1/bills/pending"), promise, ::parsePendingBills)
    }

    @ReactMethod
    fun payBill(billId: String, amount: Double, accountNumber: String, provider: String, promise: Promise) {
        val body = JsonObject().apply {
            addProperty("billId", billId)
            addProperty("amount", amount)
            addProperty("accountNumber", accountNumber)
            addProperty("provider", provider)
        }
        // Same missing-prefix bug as getPendingBills above, fixed 2026-07-13.
        authorizedCall(post("api/v1/bills/pay", body), promise, ::parsePayBillResult)
    }

    @ReactMethod
    fun getRewardTasks(promise: Promise) {
        // Same missing-prefix bug as getPendingBills/payBill, fixed 2026-07-13 alongside the
        // real backend these calls hit for the first time (services/backend's new rewards
        // module -- see docs/API_SPECIFICATION.md's Rewards section).
        authorizedCall(get("api/v1/rewards/tasks"), promise, ::parseRewardTasks)
    }

    @ReactMethod
    fun claimRewardTask(taskId: String, promise: Promise) {
        val body = JsonObject().apply { addProperty("taskId", taskId) }
        authorizedCall(post("api/v1/rewards/claim", body), promise, ::parseClaimRewardResult)
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

    // Real bug fixed (2026-07-13): never set top-level `totalBalance`/`currency`,
    // which WalletBalanceResult (the TS spec this bridge implements --
    // packages/saronite/packages/brownfield-module/src/spec/SaroniteBrownfieldModule.ts)
    // requires and wallet-balance/pages/index.tsx actually reads
    // (`balance.totalBalance.toLocaleString()`) -- would throw on any successful
    // fetch. Also only populated 3 of WalletSummary's 8 fields; the real backend
    // (services/backend's Wallet entity) has real values for all of them except
    // `icon` (no such concept server-side -- left honestly empty rather than
    // invented) and `connected` (always true: these are itunda's own wallets,
    // not an externally-linked account with a real connection-status concept).
    private fun parseWalletBalance(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val result = Arguments.createMap()
        val wallets = Arguments.createArray()
        var totalBalance = 0.0
        var currency = "RWF"
        root.getAsJsonArray("wallets")?.forEach { element ->
            val w = element.asJsonObject
            val balance = w.get("balance").asDouble
            val walletCurrency = w.get("currency")?.asString ?: currency
            val walletMap = Arguments.createMap()
            walletMap.putString("id", w.get("id").asString)
            walletMap.putString("type", w.get("type")?.asString ?: "")
            walletMap.putString("name", w.get("accountName")?.asString ?: "")
            walletMap.putString("number", w.get("accountNumber")?.asString ?: "")
            walletMap.putDouble("balance", balance)
            walletMap.putString("currency", walletCurrency)
            walletMap.putString("icon", "")
            walletMap.putBoolean("connected", true)
            wallets.pushMap(walletMap)
            totalBalance += balance
            currency = walletCurrency
        }
        result.putDouble("totalBalance", totalBalance)
        result.putString("currency", currency)
        result.putArray("wallets", wallets)
        return result
    }

    // Real bug fixed (2026-07-13): only populated 3 of PendingBill's 6 fields --
    // `dueDate` and `status` are rendered directly by pay-bills/pages/index.tsx
    // (`item.dueDate`), and `accountNumber` is required in the payBill() request
    // body sent right back to this same bridge's payBill method, so leaving it
    // unset meant a real bill payment would submit `accountNumber: undefined`.
    private fun parsePendingBills(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val bills = Arguments.createArray()
        root.getAsJsonArray("bills")?.forEach { element ->
            val b = element.asJsonObject
            val billMap = Arguments.createMap()
            billMap.putString("id", b.get("id").asString)
            billMap.putString("provider", b.get("provider").asString)
            billMap.putDouble("amount", b.get("amount").asDouble)
            billMap.putString("dueDate", b.get("dueDate")?.asString ?: "")
            billMap.putString("status", b.get("status")?.asString ?: "")
            billMap.putString("accountNumber", b.get("accountNumber")?.asString ?: "")
            bills.pushMap(billMap)
        }
        val result = Arguments.createMap()
        result.putArray("bills", bills)
        return result
    }

    // Real bug fixed (2026-07-13): only populated `message`, leaving
    // `transactionId`/`referenceNumber`/`status` unset -- the current UI
    // (pay-bills/pages/index.tsx) only reads `message` today so this was benign
    // in practice, but any future screen showing a receipt/confirmation would
    // have silently gotten `undefined` for all three. Pulled from the real
    // nested `transaction` object services/backend's BillsController actually
    // returns (`{"success", "message", "transaction": {"id", "referenceNumber",
    // "status", ...}}`), not invented.
    private fun parsePayBillResult(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val transaction = root.getAsJsonObject("transaction")
        val result = Arguments.createMap()
        result.putString("message", root.get("message")?.asString ?: "Bill payment successful")
        result.putString("transactionId", transaction?.get("id")?.asString ?: "")
        result.putString("referenceNumber", transaction?.get("referenceNumber")?.asString ?: "")
        result.putString("status", transaction?.get("status")?.asString ?: "")
        return result
    }

    // Fixed 2026-07-13, live-verified against the real backend: this used to drop subtitle/
    // claimed per task and the top-level rewardsTotal entirely -- the mini-app's own type
    // (packages/saronite/packages/brownfield-module/src/spec/SaroniteBrownfieldModule.ts's
    // RewardTask/RewardTasksResult) requires all of them; result.rewardsTotal being undefined
    // would have thrown on the very first render (`total.toLocaleString()`).
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
            taskMap.putBoolean("claimed", t.get("claimed")?.asBoolean ?: false)
            t.get("claimedAt")?.takeIf { !it.isJsonNull }?.let { taskMap.putString("claimedAt", it.asString) }
            tasks.pushMap(taskMap)
        }
        val result = Arguments.createMap()
        result.putArray("tasks", tasks)
        result.putDouble("rewardsTotal", root.get("rewardsTotal")?.asDouble ?: 0.0)
        return result
    }

    // Fixed 2026-07-13 alongside parseRewardTasks: newBalance being dropped meant
    // setTotal(result.newBalance) in the mini-app would have set the displayed total to
    // undefined immediately after every successful claim.
    private fun parseClaimRewardResult(json: String): WritableMap {
        val root = JsonParser.parseString(json).asJsonObject
        val result = Arguments.createMap()
        result.putString("message", root.get("message")?.asString ?: "Reward claimed")
        result.putDouble("rewardAmount", root.get("rewardAmount")?.asDouble ?: 0.0)
        result.putDouble("newBalance", root.get("newBalance")?.asDouble ?: 0.0)
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
