package rw.itunda.core.network

import com.google.gson.Gson
import com.google.gson.JsonParser

/** v1038 Eats demo fixture. Kept in network so the demo interceptor never depends on a feature module. */
internal object EatsDemoData {
    private const val MERCHANTS = """[
      {"merchantId":"m4","businessName":"Kimironko Coffee","category":"Coffee","cashbackRate":"2%","minOrderAmount":2000,"rating":4.9,"reviewCount":144,"distanceKm":0.8,"deliveryTimeMinutes":15,"phoneNumber":"0788 000 104","openingHours":"07:00 – 19:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Coffee","Bakery"],"searchTerms":["coffee","bakery","banana bread","croissant","cappuccino","chai"],"isOpenNow":true},
      {"merchantId":"m8","businessName":"Fresh Press Juice Bar","category":"Juice","cashbackRate":"2%","minOrderAmount":2000,"rating":4.8,"reviewCount":176,"distanceKm":0.9,"deliveryTimeMinutes":15,"phoneNumber":"0788 000 108","openingHours":"07:00 – 20:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Juice","Healthy"],"searchTerms":["smoothie","juice","fruit"],"isOpenNow":true},
      {"merchantId":"m1","businessName":"Kigali Bakery","category":"Bakery","cashbackRate":"2%","minOrderAmount":2000,"rating":4.8,"reviewCount":320,"distanceKm":1.2,"deliveryTimeMinutes":20,"phoneNumber":"0788 000 101","openingHours":"06:30 – 20:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Bakery","Coffee"],"searchTerms":["croissant","mandazi","pastries","cake","bread"],"isOpenNow":true},
      {"merchantId":"m9","businessName":"Remera Fries & Co.","category":"Chips","cashbackRate":"2%","minOrderAmount":2500,"rating":4.4,"reviewCount":412,"distanceKm":1.1,"deliveryTimeMinutes":20,"phoneNumber":"0788 000 109","openingHours":"09:00 – 22:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Chips","Chicken"],"searchTerms":["fries","chips","masala"],"isOpenNow":true},
      {"merchantId":"m5","businessName":"Inka Fried Chicken","category":"Chicken","cashbackRate":"2%","minOrderAmount":4000,"rating":4.6,"reviewCount":389,"distanceKm":1.9,"deliveryTimeMinutes":25,"phoneNumber":"0788 000 105","openingHours":"10:00 – 23:00","participatesInEatsMembership":false,"maxDiscountPercent":20,"categories":["Chicken","Chips"],"searchTerms":["wings","fried chicken","chips"],"isOpenNow":true},
      {"merchantId":"m7","businessName":"Burger Hill","category":"Burgers","cashbackRate":"2%","minOrderAmount":3500,"rating":4.7,"reviewCount":301,"distanceKm":1.5,"deliveryTimeMinutes":25,"phoneNumber":"0788 000 107","openingHours":"10:00 – 23:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Burgers","Chips"],"searchTerms":["burger","fries","sandwich","milkshake"],"isOpenNow":true},
      {"merchantId":"m11","businessName":"Green Bowl","category":"Healthy","cashbackRate":"2%","minOrderAmount":3000,"rating":4.6,"reviewCount":98,"distanceKm":2.2,"deliveryTimeMinutes":25,"phoneNumber":"0788 000 111","openingHours":"08:00 – 20:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Healthy"],"searchTerms":["salad","healthy","bowl","avocado"],"isOpenNow":true},
      {"merchantId":"m2","businessName":"Nyamirambo Grill","category":"Brochette","cashbackRate":"2%","minOrderAmount":5000,"rating":4.7,"reviewCount":512,"distanceKm":2.4,"deliveryTimeMinutes":30,"phoneNumber":"0788 000 102","openingHours":"10:00 – 22:30","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Brochette","Local"],"searchTerms":["goat","grill","skewer","grilled meat"],"isOpenNow":true},
      {"merchantId":"m13","businessName":"Sweet Tooth Cakes","category":"Dessert","cashbackRate":"2%","minOrderAmount":3000,"rating":4.9,"reviewCount":221,"distanceKm":2.8,"deliveryTimeMinutes":30,"phoneNumber":"0788 000 113","openingHours":"09:00 – 20:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Dessert","Bakery"],"searchTerms":["cake","cupcake","dessert","bakery"],"isOpenNow":true},
      {"merchantId":"m3","businessName":"Mama Rice House","category":"Local","cashbackRate":"2%","minOrderAmount":4000,"rating":4.6,"reviewCount":208,"distanceKm":3.1,"deliveryTimeMinutes":35,"phoneNumber":"0788 000 103","openingHours":"08:00 – 21:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Local"],"searchTerms":["rice","beans","isombe","matoke","rwandan"],"isOpenNow":true},
      {"merchantId":"m14","businessName":"Kicukiro Brochette Corner","category":"Brochette","cashbackRate":"2%","minOrderAmount":5000,"rating":4.5,"reviewCount":355,"distanceKm":4.1,"deliveryTimeMinutes":35,"phoneNumber":"0788 000 114","openingHours":"11:00 – 23:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Brochette","Local"],"searchTerms":["goat","brochette","grill"],"isOpenNow":true},
      {"merchantId":"m6","businessName":"Kacyiru Pizza Co.","category":"Pizza","cashbackRate":"2%","minOrderAmount":8000,"rating":4.5,"reviewCount":267,"distanceKm":3.4,"deliveryTimeMinutes":40,"phoneNumber":"0788 000 106","openingHours":"11:00 – 22:30","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Pizza"],"searchTerms":["margherita","pepperoni","italian"],"isOpenNow":true},
      {"merchantId":"m10","businessName":"Lake Kivu Fish House","category":"Local","cashbackRate":"2%","minOrderAmount":6000,"rating":4.7,"reviewCount":143,"distanceKm":3.8,"deliveryTimeMinutes":40,"phoneNumber":"0788 000 110","openingHours":"11:00 – 21:30","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Local"],"searchTerms":["fish","tilapia","sambaza"],"isOpenNow":true},
      {"merchantId":"m12","businessName":"Gisozi Street Kitchen","category":"Local","cashbackRate":"2%","minOrderAmount":5000,"rating":4.3,"reviewCount":86,"distanceKm":4.6,"deliveryTimeMinutes":45,"phoneNumber":"0788 000 112","openingHours":"17:00 – 23:00","participatesInEatsMembership":false,"maxDiscountPercent":null,"categories":["Local","Brochette"],"searchTerms":["street food","brochette","evening"],"isOpenNow":false}
    ]"""
    private val allMerchants = JsonParser.parseString(MERCHANTS).asJsonArray

    fun merchants(category: String?, query: String?): String {
        val filtered = allMerchants.filter { element ->
            val merchant = element.asJsonObject
            val categories = merchant.getAsJsonArray("categories").map { it.asString }
            val terms = buildList {
                add(merchant.get("businessName").asString)
                add(merchant.get("category").asString)
                merchant.getAsJsonArray("searchTerms").forEach { add(it.asString) }
            }
            val categoryMatches = category.isNullOrBlank() || categories.any { it.equals(category, ignoreCase = true) }
            val queryMatches = query.isNullOrBlank() || terms.any { it.contains(query, ignoreCase = true) }
            categoryMatches && queryMatches
        }
        return """{"success":true,"merchants":${Gson().toJson(filtered)}}"""
    }

    val categories: String = """{"success":true,"categories":["Brochette","Chicken","Pizza","Burgers","Local","Chips","Bakery","Coffee","Juice","Healthy","Dessert"]}"""
    val favorites: String = """{"success":true,"favorites":[{"restaurantId":"m1","businessName":"Kigali Bakery","category":"Bakery","favoritedAt":"2026-11-01T09:00:00Z"},{"restaurantId":"m2","businessName":"Nyamirambo Grill","category":"Brochette","favoritedAt":"2026-11-02T09:00:00Z"}],"page":0,"totalPages":1,"totalElements":2}"""
    private var hasPlacedOrder = false
    private var orderStatus = "PLACED"

    private fun orderJson(): String {
        val hasRider = orderStatus in setOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED")
        val riderIdJson = if (hasRider) "\"rider-1\"" else "null"
        val riderNameJson = if (hasRider) "\"Jean Claude\"" else "null"
        val eta = if (hasRider) 8 else 20
        return """{"id":"EA-7207","buyerId":"demo-buyer","restaurantId":"m1","riderId":$riderIdJson,"deliveryAddress":"Kimironko, KG 11 Ave","itemsSubtotal":2400,"deliveryFee":0,"platformFee":0,"totalAmount":2400,"promotionDiscount":0,"transactionId":"demo-txn-7207","deliveryPayoutTransactionId":null,"status":"$orderStatus","createdAt":"2026-11-17T14:00:00Z","updatedAt":"2026-11-17T14:00:00Z","deliveryLatitude":-1.95,"deliveryLongitude":30.12,"distanceKm":1.2,"deliveryNotes":"Leave it at the door","riderName":$riderNameJson,"estimatedArrivalMinutes":$eta,"tipAmount":null}"""
    }

    private val orderItems = """[{"id":"item-1","orderId":"EA-7207","productId":"p1","productName":"Croissant","unitPrice":1200,"quantity":2}]"""
    fun orderDetail(): String = """{"success":true,"order":\${orderJson()},"items":$orderItems}"""
    fun placeOrder(): String {
        hasPlacedOrder = true
        orderStatus = "PLACED"
        return orderDetail()
    }
    fun myOrders(): String = if (hasPlacedOrder) {
        """{"success":true,"orders":[\${orderJson()}],"page":0,"totalPages":1,"totalElements":1}"""
    } else {
        """{"success":true,"orders":[],"page":0,"totalPages":0,"totalElements":0}"""
    }
    fun cancelOrder(): String {
        orderStatus = "CANCELLED"
        return orderDetail()
    }
    fun review(): String = """{"success":true,"review":{"id":"review-demo","orderId":"EA-7207","restaurantId":"m1","riderId":"rider-1","restaurantRating":5,"riderRating":5,"comment":null,"createdAt":"2026-11-17T14:30:00Z"}}"""

}
