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
                "api/v1/merchant/pay/customer-code" ->
                    """{"success":true,"code":"ITUNDA-DEMO-${'$'}{System.currentTimeMillis() / 5000}","expiresAt":"${'$'}{java.time.Instant.now().plusSeconds(60)}"}"""
                else -> OK
            }
        }
        if (method != "GET") return OK
        return when {
            path == "api/v1/auth/profile" -> """{"success":true,"user":$USER}"""
            path == "api/v1/affiliate/links/my-links" -> """{"success":true,"links":[]}"""
            path == "api/v1/affiliate/commissions/my-commissions" -> """{"success":true,"commissions":[]}"""
            path == "api/v1/p2p/scam-reports/mine" -> """{"success":true,"reports":[]}"""
            path == "api/v1/merchant/reviews/my-reviews" -> """{"success":true,"reviews":[]}"""
            path == "api/v1/messages/presence" -> """{"success":true,"presence":{"u-c1":true,"u-c2":true}}"""
            path == "api/v1/orders/my-orders" || path == "api/v1/eats/orders/my-orders" -> """{"success":true,"orders":[]}"""
            path == "api/v1/messages/conversations" -> """{"success":true,"conversations":${conversations()}}"""
            path == "api/v1/auth/terms" -> """{"success":true,"terms":[]}"""
            path == "api/v1/account" -> """{"success":true,"accounts":$ACCOUNTS}"""
            path == "api/v1/account/spending" ->
                """{"success":true,"totalSpent":184300,"categories":[
                    {"name":"Food","amount":62400},{"name":"Transport","amount":38900},
                    {"name":"Shopping","amount":51200},{"name":"Bills","amount":31800}]}"""
            path == "api/v1/account/transactions" || path.endsWith("/transactions") ->
                """{"success":true,"transactions":${transactions()}}"""
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
            path == "api/v1/credit-score" ->
                """{"success":true,"score":692,"factors":[],"computedAt":"2026-11-13T09:00:00Z"}"""
            path == "api/v1/credit-score/suggestions" ->
                """{"success":true,"suggestions":[]}"""
            path == "api/v1/marketplace/listings" || path == "api/v1/marketplace/listings/nearby" ||
                path == "api/v1/marketplace/listings/my-neighborhood" -> """{"success":true,"listings":[{"id":"l1","sellerId":"s1","title":"Samsung phone, barely used","description":"Demo listing","price":140000,"category":"ELECTRONICS","status":"ACTIVE","createdAt":"2026-11-14T07:00:00Z","latitude":-1.9441,"longitude":30.126,"neighborhood":"Kimironko","likeCount":10},{"id":"l2","sellerId":"s2","title":"Baby clothes, 0–12 months","description":"Demo listing","price":0,"category":"KIDS","status":"ACTIVE","createdAt":"2026-11-14T07:00:00Z","latitude":-1.945,"longitude":30.127,"neighborhood":"Kimironko","likeCount":11},{"id":"l3","sellerId":"s3","title":"Samsung phone, 64 GB","description":"Demo listing","price":60000,"category":"ELECTRONICS","status":"ACTIVE","createdAt":"2026-11-14T07:00:00Z","latitude":-1.956,"longitude":30.11,"neighborhood":"Remera","likeCount":4},{"id":"l4","sellerId":"s4","title":"Wooden dining table, 6 seats","description":"Demo listing","price":90000,"category":"HOME","status":"ACTIVE","createdAt":"2026-11-14T07:00:00Z","latitude":-1.957,"longitude":30.111,"neighborhood":"Remera","likeCount":11},{"id":"l5","sellerId":"s5","title":"Wooden bookshelf","description":"Demo listing","price":0,"category":"HOME","status":"ACTIVE","createdAt":"2026-11-14T07:00:00Z","latitude":-1.958,"longitude":30.112,"neighborhood":"Remera","likeCount":4}],"page":0,"totalPages":1,"totalElements":5}"""
            path.startsWith("api/v1/shopping/merchants/") && path.endsWith("/products") ->
                """{"success":true,"merchant":{"id":"m1","businessName":"Kigali Bakery"},"products":[{"id":"p1","merchantId":"m1","name":"Croissant","price":1200,"active":true,"createdAt":"2026-11-01T08:00:00Z"},{"id":"p2","merchantId":"m1","name":"Mandazi, 6 pieces","price":1000,"active":true,"createdAt":"2026-11-01T08:00:00Z"},{"id":"p3","merchantId":"m1","name":"Fresh bread loaf","price":1500,"active":true,"createdAt":"2026-11-01T08:00:00Z"},{"id":"p4","merchantId":"m1","name":"Fruit juice","price":1800,"active":true,"createdAt":"2026-11-01T08:00:00Z"}]}"""
            path == "api/v1/shopping/merchants" -> """{"success":true,"merchants":[{"merchantId":"m1","businessName":"Kigali Bakery","category":"Bakery","cashbackRate":"3","latitude":-1.941,"longitude":30.121,"rating":4.8,"reviewCount":320,"distanceKm":1.2,"deliveryTimeMinutes":20,"minOrderAmount":2000},{"merchantId":"m2","businessName":"Mama Rice House","category":"Local","cashbackRate":"3","latitude":-1.942,"longitude":30.122,"rating":4.6,"reviewCount":208,"distanceKm":2.6,"deliveryTimeMinutes":30,"minOrderAmount":2500},{"merchantId":"m3","businessName":"Kigali Roast","category":"Cafe","cashbackRate":"2","latitude":-1.943,"longitude":30.123,"rating":4.4,"reviewCount":212,"distanceKm":0.2,"deliveryTimeMinutes":15,"minOrderAmount":1500},{"merchantId":"m4","businessName":"Sweet Tooth Cakes","category":"Dessert, Bakery","cashbackRate":"2","latitude":-1.944,"longitude":30.124,"rating":4.5,"reviewCount":96,"distanceKm":4.0,"deliveryTimeMinutes":35,"minOrderAmount":3000}]}"""
            path == "api/v1/shopping/merchants/categories" -> """{"success":true,"categories":["Bakery","Local","Cafe","Dessert"]}"""
            path == "api/v1/eats/favorites" -> """{"success":true,"favorites":[],"page":0,"totalPages":1,"totalElements":0}"""
            path == "api/v1/eats/dishes" -> """{"success":true,"dishes":[{"id":"d1","merchantId":"m1","merchantName":"Kigali Bakery","name":"Croissant","price":1200,"recommended":true},{"id":"d2","merchantId":"m2","merchantName":"Mama Rice House","name":"Rice and beans","price":2500,"recommended":true},{"id":"d3","merchantId":"m3","merchantName":"Kigali Roast","name":"Latte","price":1800,"recommended":false}]}"""
            path == "api/v1/community/posts" || path == "api/v1/community/posts/nearby" ||
                path == "api/v1/community/meetups/upcoming" -> """{"success":true,"posts":[{"id":"c1","authorId":"u1","category":"EVENT","title":"Umuganda this Saturday","body":"Umuganda this Saturday at 8. Bring your own gloves if you have them.","status":"ACTIVE","likeCount":22,"commentCount":1,"createdAt":"2026-11-12T07:00:00Z","latitude":-1.956,"longitude":30.11,"neighborhood":"Remera","topic":"TOGETHER","eventDate":"2026-11-14T06:00:00Z","capacity":60},{"id":"c2","authorId":"u2","category":"QUESTION","title":"Tailor near Kimironko market","body":"Where can I find a reliable tailor near Kimironko market? I need a school uniform fixed by Monday.","status":"ACTIVE","likeCount":4,"commentCount":2,"createdAt":"2026-11-14T09:55:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Kimironko","topic":"ASK"},{"id":"c3","authorId":"u3","category":"TIP","title":"Best brochettes","body":"The brochette stall behind Kimironko market is the best this month. 1,500 RWF for three sticks with chips. Go before 19:00.","status":"ACTIVE","likeCount":9,"commentCount":3,"createdAt":"2026-11-14T09:40:00Z","latitude":-1.945,"longitude":30.127,"neighborhood":"Kimironko","topic":"LOCAL_FOOD"}],"page":0,"totalPages":1}"""
            path == "api/v1/community/categories" -> """{"success":true,"categories":[{"id":"QUESTION","label":"Ask"},{"id":"TIP","label":"Tips"},{"id":"EVENT","label":"Events"}]}"""
            path == "api/v1/community/topics" -> """{"success":true,"topics":[{"id":"ASK","label":"Ask"},{"id":"LOCAL_FOOD","label":"Local food"},{"id":"TOGETHER","label":"Together"}]}"""
            path == "api/v1/jobs/posts" || path == "api/v1/jobs/posts/nearby" -> """{"success":true,"posts":[{"id":"j1","posterId":"u1","category":"CLEANING","title":"House cleaning, 3 hours","description":"Two-bedroom house in Kimironko. Tools provided.","payType":"FIXED","payAmount":8000,"status":"OPEN","createdAt":"2026-11-13T08:00:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Kimironko"},{"id":"j2","posterId":"u2","category":"DELIVERY","title":"Weekend delivery rider","description":"Deliver parcels around Remera on Saturday.","payType":"FIXED","payAmount":12000,"status":"OPEN","createdAt":"2026-11-13T08:00:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Remera"},{"id":"j3","posterId":"u3","category":"TUTORING","title":"Math tutor, secondary","description":"Two evenings a week, Kacyiru.","payType":"HOURLY","payAmount":3000,"status":"OPEN","createdAt":"2026-11-13T08:00:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Kacyiru"}],"page":0,"totalPages":1,"totalElements":3}"""
            path == "api/v1/realestate/listings" || path == "api/v1/realestate/listings/nearby" -> """{"success":true,"listings":[{"id":"r1","listerId":"u1","listingType":"RENT","propertyType":"APARTMENT","title":"2-bedroom apartment, Kimironko","description":"Demo listing","price":350000,"bedrooms":2,"sizeSqm":85,"status":"ACTIVE","createdAt":"2026-11-12T08:00:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Kimironko"},{"id":"r2","listerId":"u2","listingType":"RENT","propertyType":"HOUSE","title":"3-bedroom house with garden, Remera","description":"Demo listing","price":600000,"bedrooms":3,"sizeSqm":140,"status":"ACTIVE","createdAt":"2026-11-12T08:00:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Remera"},{"id":"r3","listerId":"u3","listingType":"SALE","propertyType":"LAND","title":"Plot of land, Kanombe","description":"Demo listing","price":18000000,"bedrooms":0,"sizeSqm":400,"status":"ACTIVE","createdAt":"2026-11-12T08:00:00Z","latitude":-1.944,"longitude":30.126,"neighborhood":"Kanombe"}],"page":0,"totalPages":1,"totalElements":3}"""
            path == "api/v1/bus/trips/search" ->
                """{"success":true,"trips":[{"id":"bus-1","operatorUserId":"op-1","accountId":"acc-1","origin":"Nyabugogo","destination":"Musanze","departureTime":"2026-11-15T07:30:00Z","totalSeats":30,"availableSeats":12,"farePerSeat":3000,"createdAt":"2026-11-10T08:00:00Z"},{"id":"bus-2","operatorUserId":"op-1","accountId":"acc-1","origin":"Nyabugogo","destination":"Huye","departureTime":"2026-11-15T09:00:00Z","totalSeats":30,"availableSeats":8,"farePerSeat":2800,"createdAt":"2026-11-10T08:00:00Z"}]}"""
            path == "api/v1/bus/trips/mine" -> """{"success":true,"trips":[]}"""
            path == "api/v1/bus/bookings/my-history" -> """{"success":true,"bookings":[],"page":0,"totalPages":1}"""
            path == "api/v1/savings/goals" ->
                """{"success":true,"goals":[
                    {"id":"g1","userId":"demo-user","accountId":"a2","name":"Rainy day","targetAmount":500000,"currentAmount":350000,
                     "monthlyContribution":25000,"interestRate":5.0,"targetDate":"2027-03-01","category":"SAVINGS","status":"ACTIVE","color":"#34B68D"},
                    {"id":"g2","userId":"demo-user","accountId":"a2","name":"New phone","targetAmount":300000,"currentAmount":90000,
                     "monthlyContribution":15000,"interestRate":5.0,"targetDate":null,"category":"SAVINGS","status":"ACTIVE","color":"#8F89FF"}]}"""
            path == "api/v1/savings/interest-jar" ->
                """{"success":true,"jar":{"userId":"demo-user","accountId":"a2","balance":1462,"rate":2.8,"earnedThisMonth":212,"earnedTotal":1462}}"""
            path == "api/v1/savings/round-up" -> """{"success":true,"settings":null}"""
            path == "api/v1/mini-apps/catalog" -> """{"success":true,"miniApps":[],"page":0,"totalPages":1}"""
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

        // The prototype's Messages rows (names, previews, unread badges), timestamps relative to now.
        private fun conversations(): String {
            fun ago(min: Long) = java.time.Instant.now().minus(min, java.time.temporal.ChronoUnit.MINUTES).toString()
            fun c(id: String, name: String, preview: String, unread: Int, min: Long) =
                """{"conversationId":"$id","otherUserId":"u-$id","otherUserName":"$name","lastMessageAt":"${ago(min)}","lastMessagePreview":"$preview","unreadCount":$unread}"""
            return "[" + listOf(
                c("c1", "Patrick H.", "Yes, still available. Can you come tomorrow?", 1, 8),
                c("c2", "Amina K.", "See you on Friday", 1, 2),
                c("c3", "Umurenge Savers", "Contributions are due on Friday", 0, 60),
                c("c4", "Kigali Bakery", "Your order is on the way", 0, 300),
                c("c5", "itunda support", "Your report was reviewed. Thank you.", 0, 1500),
            ).joinToString(",") + "]"
        }

        // Relative to "now" so the Home 7-day spending chart always has data to show.
        private fun transactions(): String {
            fun at(daysAgo: Long, hour: Long) = java.time.Instant.now().minus(daysAgo, java.time.temporal.ChronoUnit.DAYS)
                .minus(hour, java.time.temporal.ChronoUnit.HOURS).toString()
            fun out(id: String, to: String, type: String, amount: Int, desc: String, daysAgo: Long, hour: Long) =
                """{"id":"$id","senderId":"demo-user","recipientId":"$to","type":"$type","amount":$amount,"fee":0,"currency":"RWF","status":"COMPLETED","description":"$desc","createdAt":"${at(daysAgo, hour)}"}"""
            return "[" + listOf(
                out("t1", "m1", "PAYMENT", 4500, "Java House", 0, 1),
                out("t2", "m2", "TRANSIT", 600, "Bus fare", 0, 5),
                """{"id":"t3","senderId":"u2","recipientId":"demo-user","type":"TRANSFER","amount":25000,"fee":0,"currency":"RWF","status":"COMPLETED","description":"From Eric","createdAt":"${at(1, 3)}"}""",
                out("t4", "m3", "BILL", 12000, "Electricity", 1, 6),
                out("t5", "m4", "PAYMENT", 18500, "Simba Supermarket", 2, 2),
                out("t6", "m2", "TRANSIT", 1200, "Moto fare", 3, 4),
                out("t7", "m5", "PAYMENT", 9800, "Inzozi Cafe", 4, 3),
                out("t8", "m6", "PAYMENT", 31000, "Kigali Mall", 5, 5),
                out("t9", "m7", "PAYMENT", 6400, "Pharmacy", 6, 2),
            ).joinToString(",") + "]"
        }

        const val STOCKS = """[
            {"id":"s1","symbol":"BKG","name":"Bank of Kigali","price":312,"change":4,"changePercent":1.3,"marketCap":"280B","volume":120400,"market":"RSE"},
            {"id":"s2","symbol":"MTNR","name":"MTN Rwandacell","price":212,"change":-2,"changePercent":-0.9,"marketCap":"190B","volume":98200,"market":"RSE"}]"""
    }
}
