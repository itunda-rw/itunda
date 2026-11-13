package rw.itunda.feature.front.impl

/** Demo menu for the Front table flow until the backend exists. */
internal data class FrontDish(val id: String, val name: String, val note: String, val price: Int, val art: String, val group: String, val badge: String? = null)

internal val frontDishes = listOf(
    FrontDish("chicken", "Grilled chicken", "Half, with chips", 6500, "chicken", "Mains", "#1 seller"),
    FrontDish("brochette", "Goat brochette", "Five skewers", 2500, "burger", "Mains", "Owner's pick"),
    FrontDish("chips", "Chips", "Fresh, salted", 1500, "chips", "Sides and sweets", "First time?"),
    FrontDish("cake", "Passion cake", "Slice", 2000, "cake", "Sides and sweets"),
    FrontDish("juice", "Fresh juice", "Passion or mango", 1500, "juice", "Drinks"),
    FrontDish("coffee", "Rwandan coffee", "Black or with milk", 1200, "coffee", "Drinks"),
)

internal fun rwf(v: Int): String = "%,d RWF".format(v)
