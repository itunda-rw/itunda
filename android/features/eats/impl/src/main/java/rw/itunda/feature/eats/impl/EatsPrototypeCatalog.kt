package rw.itunda.feature.eats.impl

import androidx.compose.ui.graphics.Color
import rw.itunda.core.network.ShoppingMerchantDto

/** The fixed catalogue used by the v1038 prototype and the Android demo surface. */
data class EatsPrototypeRestaurant(
    val merchant: ShoppingMerchantDto,
    val categories: List<String>,
    val subtitle: String,
    val rating: Double,
    val reviews: Int,
    val distanceKm: Double,
    val deliveryMinutes: Int,
    val etaRange: String,
    val deliveryFeeRwf: Int,
    val minOrderRwf: Int,
    val tag: String?,
    val art: String,
    val background: Color,
    val isOpenNow: Boolean = true,
    val searchTerms: List<String> = emptyList(),
) {
    val name: String get() = merchant.businessName
}

data class EatsPrototypeDish(
    val id: String,
    val merchantId: String,
    val name: String,
    val description: String,
    val priceRwf: Int,
    val art: String,
    val background: Color,
)

private fun prototypeMerchant(
    id: String,
    name: String,
    category: String,
    rating: Double,
    reviews: Int,
    distance: Double,
    eta: Int,
    minOrder: Int,
    phone: String,
    hours: String,
    discount: Int? = null,
) = ShoppingMerchantDto(
    merchantId = id,
    businessName = name,
    category = category,
    cashbackRate = "2%",
    minOrderAmount = minOrder.toDouble(),
    rating = rating,
    reviewCount = reviews.toLong(),
    distanceKm = distance,
    deliveryTimeMinutes = eta,
    phoneNumber = phone,
    openingHours = hours,
    participatesInEatsMembership = false,
    maxDiscountPercent = discount,
)

val EATS_PROTOTYPE_RESTAURANTS: List<EatsPrototypeRestaurant> = listOf(
    EatsPrototypeRestaurant(prototypeMerchant("m1", "Kigali Bakery", "Bakery", 4.8, 320, 1.2, 20, 2000, "0788 000 101", "06:30 – 20:00"), listOf("Bakery", "Coffee"), "Bread, pastries and cakes", 4.8, 320, 1.2, 20, "15-25", 0, 2000, "Free delivery", "bread", Color(0xFFFFE8C7), searchTerms = listOf("croissant", "mandazi", "pastries", "cake", "bread")),
    EatsPrototypeRestaurant(prototypeMerchant("m2", "Nyamirambo Grill", "Brochette", 4.7, 512, 2.4, 30, 5000, "0788 000 102", "10:00 – 22:30"), listOf("Brochette", "Local"), "Grilled brochettes and sides", 4.7, 512, 2.4, 30, "25-35", 1500, 5000, "Popular", "brochette", Color(0xFFFFE1D6), searchTerms = listOf("goat", "grill", "skewer", "grilled meat")),
    EatsPrototypeRestaurant(prototypeMerchant("m3", "Mama Rice House", "Local", 4.6, 208, 3.1, 35, 4000, "0788 000 103", "08:00 – 21:00"), listOf("Local"), "Home-style Rwandan meals", 4.6, 208, 3.1, 35, "30-40", 1500, 4000, null, "rice", Color(0xFFE6F4DC), searchTerms = listOf("rice", "beans", "isombe", "matoke", "rwandan")),
    EatsPrototypeRestaurant(prototypeMerchant("m4", "Kimironko Coffee", "Coffee", 4.9, 144, 0.8, 15, 2000, "0788 000 104", "07:00 – 19:00"), listOf("Coffee", "Bakery"), "Rwandan coffee and light bites", 4.9, 144, 0.8, 15, "10-20", 500, 2000, "New", "coffee", Color(0xFFE7DDF7), searchTerms = listOf("coffee", "bakery", "banana bread", "croissant", "cappuccino", "chai")),
    EatsPrototypeRestaurant(prototypeMerchant("m5", "Inka Fried Chicken", "Chicken", 4.6, 389, 1.9, 25, 4000, "0788 000 105", "10:00 – 23:00", 20), listOf("Chicken", "Chips"), "Crispy fried chicken and wings", 4.6, 389, 1.9, 25, "20-30", 1000, 4000, "20% off", "chicken", Color(0xFFFFE9B8), searchTerms = listOf("wings", "fried chicken", "chips")),
    EatsPrototypeRestaurant(prototypeMerchant("m6", "Kacyiru Pizza Co.", "Pizza", 4.5, 267, 3.4, 40, 8000, "0788 000 106", "11:00 – 22:30"), listOf("Pizza"), "Wood-fired pizza", 4.5, 267, 3.4, 40, "35-45", 2000, 8000, null, "pizza", Color(0xFFFFD9D2), searchTerms = listOf("margherita", "pepperoni", "italian")),
    EatsPrototypeRestaurant(prototypeMerchant("m7", "Burger Hill", "Burgers", 4.7, 301, 1.5, 25, 3500, "0788 000 107", "10:00 – 23:00"), listOf("Burgers", "Chips"), "Burgers, fries and shakes", 4.7, 301, 1.5, 25, "20-30", 1000, 3500, "Popular", "burger", Color(0xFFFFE3C2), searchTerms = listOf("burger", "fries", "sandwich", "milkshake")),
    EatsPrototypeRestaurant(prototypeMerchant("m8", "Fresh Press Juice Bar", "Juice", 4.8, 176, 0.9, 15, 2000, "0788 000 108", "07:00 – 20:00"), listOf("Juice", "Healthy"), "Cold-pressed juice and smoothies", 4.8, 176, 0.9, 15, "10-20", 500, 2000, "New", "juice", Color(0xFFDDF3E4), searchTerms = listOf("smoothie", "juice", "fruit")),
    EatsPrototypeRestaurant(prototypeMerchant("m9", "Remera Fries & Co.", "Chips", 4.4, 412, 1.1, 20, 2500, "0788 000 109", "09:00 – 22:00"), listOf("Chips", "Chicken"), "Chips with everything", 4.4, 412, 1.1, 20, "15-25", 0, 2500, "Free delivery", "chips", Color(0xFFFFF0B8), searchTerms = listOf("fries", "chips", "masala")),
    EatsPrototypeRestaurant(prototypeMerchant("m10", "Lake Kivu Fish House", "Local", 4.7, 143, 3.8, 40, 6000, "0788 000 110", "11:00 – 21:30"), listOf("Local"), "Tilapia and sambaza", 4.7, 143, 3.8, 40, "35-45", 2000, 6000, null, "fish", Color(0xFFD8ECFA), searchTerms = listOf("fish", "tilapia", "sambaza")),
    EatsPrototypeRestaurant(prototypeMerchant("m11", "Green Bowl", "Healthy", 4.6, 98, 2.2, 25, 3000, "0788 000 111", "08:00 – 20:00"), listOf("Healthy"), "Salads and healthy bowls", 4.6, 98, 2.2, 25, "20-30", 1000, 3000, "New", "salad", Color(0xFFE1F4D4), searchTerms = listOf("salad", "healthy", "bowl", "avocado")),
    EatsPrototypeRestaurant(prototypeMerchant("m12", "Gisozi Street Kitchen", "Local", 4.3, 86, 4.6, 45, 5000, "0788 000 112", "17:00 – 23:00"), listOf("Local", "Brochette"), "Evening street food", 4.3, 86, 4.6, 45, "40-50", 2000, 5000, "Back at 17:00", "rice", Color(0xFFEADCF5), isOpenNow = false, searchTerms = listOf("street food", "brochette", "evening")),
    EatsPrototypeRestaurant(prototypeMerchant("m13", "Sweet Tooth Cakes", "Dessert", 4.9, 221, 2.8, 30, 3000, "0788 000 113", "09:00 – 20:00"), listOf("Dessert", "Bakery"), "Cakes, cupcakes and treats", 4.9, 221, 2.8, 30, "25-35", 1500, 3000, "Popular", "cake", Color(0xFFFFDCE6), searchTerms = listOf("cake", "cupcake", "dessert", "bakery")),
    EatsPrototypeRestaurant(prototypeMerchant("m14", "Kicukiro Brochette Corner", "Brochette", 4.5, 355, 4.1, 35, 5000, "0788 000 114", "11:00 – 23:00"), listOf("Brochette", "Local"), "Goat brochettes done right", 4.5, 355, 4.1, 35, "30-40", 1500, 5000, null, "brochette", Color(0xFFFFD8C8), searchTerms = listOf("goat", "brochette", "grill")),
)

private val eatsPrototypeById = EATS_PROTOTYPE_RESTAURANTS.associateBy { it.merchant.merchantId }

fun ShoppingMerchantDto.toEatsPrototypeRestaurant(): EatsPrototypeRestaurant =
    eatsPrototypeById[merchantId] ?: EatsPrototypeRestaurant(
        merchant = this,
        categories = listOf(category),
        subtitle = category,
        rating = rating ?: 4.5,
        reviews = reviewCount.toInt(),
        distanceKm = distanceKm ?: 1.0,
        deliveryMinutes = deliveryTimeMinutes ?: 25,
        etaRange = "20-30",
        deliveryFeeRwf = if (participatesInEatsMembership) 0 else 500,
        minOrderRwf = minOrderAmount?.toInt() ?: 2000,
        tag = maxDiscountPercent?.takeIf { it > 0 }?.let { "$it% off" },
        art = eatsCategoryArt(category),
        background = Color(0xFFFFE8C7),
    )

val EATS_PROTOTYPE_DISHES: List<EatsPrototypeDish> = listOf(
    EatsPrototypeDish("m4-coffee-1", "m4", "Rwandan coffee", "Kimironko Coffee · 15 min", 1500, "coffee", Color(0xFFE7DDF7)),
    EatsPrototypeDish("m4-coffee-2", "m4", "Iced coffee", "Kimironko Coffee · 15 min", 2200, "coffee", Color(0xFFE7DDF7)),
)
