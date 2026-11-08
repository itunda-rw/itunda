package rw.itunda.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Demo mode: answers API calls with prototype-style dummy data so the UI/UX can be
 * tested on a device with no backend. Enabled only by `-PdemoMode=true` at build time
 * (BuildConfig.DEMO_MODE -> NetworkClient.demoMode); never on in a normal build.
 *
 * Numbers and names mirror itunda_clickable_prototype (Aline, 1,284,500 RWF, etc.).
 * Any path not listed returns `{"success":true}` so screens that don't know about
 * demo data degrade to their empty/error states instead of hanging on the network.
 * Writes (POST/PUT/DELETE) also succeed with `{"success":true}`; nothing persists.
 */
internal class DemoInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimStart('/')
        val json = route(request.method, path)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK (demo)")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun route(method: String, path: String): String {
        if (method == "POST") {
            return when (path) {
                "api/v1/auth/check-phone" -> """{"exists":true}"""
                "api/v1/auth/login", "api/v1/auth/register" ->
                    """{"message":"ok","user":$USER,"accessToken":"demo-access","refreshToken":"demo-refresh"}"""
                else -> OK
            }
        }
        if (method != "GET") return OK
        return when {
            path == "api/v1/auth/profile" -> """{"success":true,"user":$USER}"""
            path == "api/v1/auth/terms" -> """{"success":true,"terms":[]}"""
            path == "api/v1/account" -> """{"success":true,"accounts":$ACCOUNTS}"""
            path == "api/v1/account/spending" ->
                """{"success":true,"totalSpent":184300,"categories":[
                    {"name":"Food","amount":62400},{"name":"Transport","amount":38900},
                    {"name":"Shopping","amount":51200},{"name":"Bills","amount":31800}]}"""
            path == "api/v1/account/transactions" || path.endsWith("/transactions") ->
                """{"success":true,"transactions":$TRANSACTIONS}"""
            path == "api/v1/discover" ->
                """{"success":true,"items":[{"id":"d1","category":"Offers","title":"Get 5,000 RWF when you invite a friend",
                    "subtitle":"Invite","description":"Share your code and you both earn.","color":"#8B89FF","isNew":true,"badge":"New","priority":1}]}"""
            path == "api/v1/notifications" -> """{"success":true,"notifications":[],"unreadCount":2}"""
            path == "api/v1/rewards/tasks" ->
                """{"success":true,"rewardsTotal":2500,"tasks":[
                    {"id":"r1","title":"Pay a bill","subtitle":"Earn 1,000 RWF","rewardAmount":1000,"claimed":false,"claimedAt":null,"eligible":true},
                    {"id":"r2","title":"Invite a friend","subtitle":"Earn 1,500 RWF","rewardAmount":1500,"claimed":true,"claimedAt":"2026-11-01T09:00:00Z","eligible":true}]}"""
            path == "api/v1/card/my-card" ->
                """{"success":true,"card":{"id":"c1","last4":"4821","dailyLimit":500000,"monthlyLimit":2000000,"frozen":false,
                    "lost":false,"closedAt":null,"pinSet":true,"reissuedAt":null,"issuedAt":"2026-01-10T09:00:00Z","design":"violet",
                    "spentToday":12000,"spentThisMonth":184300,"remainingToday":488000,"remainingThisMonth":1815700}}"""
            path == "api/v1/stocks" || path == "api/v1/stocks/watchlist" ->
                """{"success":true,"stocks":$STOCKS,"watchlist":$STOCKS}"""
            path == "api/v1/stocks/portfolio" ->
                """{"success":true,"portfolio":{"totalValue":842000,"totalReturn":61200,"totalReturnPercent":7.84,"holdings":[
                    {"stockId":"s1","symbol":"BKG","name":"Bank of Kigali","shares":1200,"avgPrice":290,"currentPrice":312,"value":374400,"return":26400},
                    {"stockId":"s2","symbol":"MTNR","name":"MTN Rwandacell","shares":2200,"avgPrice":195,"currentPrice":212,"value":466400,"return":37400}]}}"""
            path == "api/v1/weekly-savings/plans" -> """{"success":true,"plans":[]}"""
            path == "api/v1/accounts/linked" -> """{"success":true,"accounts":[]}"""
            path == "api/v1/p2p/auto-transfers" -> """{"success":true,"transfers":[]}"""
            else -> OK
        }
    }

    private companion object {
        const val OK = """{"success":true}"""

        const val USER = """{"id":"demo-user","phoneNumber":"+250788123456","email":"aline@example.com","firstName":"Aline",
            "lastName":"Uwase","kycVerified":true,"creditScore":742,"createdAt":"2025-06-01T09:00:00Z","neighborhood":"Kimironko",
            "neighborhoodVerificationCount":3,"emailVerified":true,"phoneVerified":true,"profilePhotoUrl":null,"pinSet":true}"""

        const val ACCOUNTS = """[
            {"id":"a1","userId":"demo-user","accountNumber":"0010 2233 4455","accountName":"itunda Money","nickname":null,"type":"MAIN",
             "balance":1284500,"availableBalance":1284500,"currency":"RWF","isActive":true},
            {"id":"a2","userId":"demo-user","accountNumber":"0010 2233 9988","accountName":"Savings","nickname":"Rainy day","type":"SAVINGS",
             "balance":350000,"availableBalance":350000,"currency":"RWF","isActive":true}]"""

        const val TRANSACTIONS = """[
            {"id":"t1","senderId":"demo-user","recipientId":"m1","type":"PAYMENT","amount":4500,"fee":0,"currency":"RWF","status":"COMPLETED","description":"Java House","createdAt":"2026-11-08T08:12:00Z"},
            {"id":"t2","senderId":"demo-user","recipientId":"m2","type":"TRANSIT","amount":600,"fee":0,"currency":"RWF","status":"COMPLETED","description":"Bus fare","createdAt":"2026-11-07T17:40:00Z"},
            {"id":"t3","senderId":"u2","recipientId":"demo-user","type":"TRANSFER","amount":25000,"fee":0,"currency":"RWF","status":"COMPLETED","description":"From Eric","createdAt":"2026-11-07T10:05:00Z"},
            {"id":"t4","senderId":"demo-user","recipientId":"m3","type":"BILL","amount":12000,"fee":0,"currency":"RWF","status":"COMPLETED","description":"Electricity","createdAt":"2026-11-06T09:30:00Z"}]"""

        const val STOCKS = """[
            {"id":"s1","symbol":"BKG","name":"Bank of Kigali","price":312,"change":4,"changePercent":1.3,"marketCap":"280B","volume":120400,"market":"RSE"},
            {"id":"s2","symbol":"MTNR","name":"MTN Rwandacell","price":212,"change":-2,"changePercent":-0.9,"marketCap":"190B","volume":98200,"market":"RSE"}]"""
    }
}
