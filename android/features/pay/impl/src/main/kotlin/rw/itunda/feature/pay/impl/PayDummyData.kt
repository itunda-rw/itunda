package rw.itunda.feature.pay.impl

/** Prototype sample data for the Pay screens until a merchant/ledger summary API exists. */
internal class PayMerchant(val name: String, val till: String, val last: String)

internal val payMerchants = listOf(
    PayMerchant("Kigali Bakery", "123456", "3,500"),
    PayMerchant("Simba Supermarket", "731902", "18,200"),
    PayMerchant("Nyamirambo Coffee House", "204518", "2,800"),
)

internal class PayHistoryRow(
    val day: String, val name: String, val time: String, val amount: String, val till: String, val date: String, val ref: String,
)

internal val payHistory = listOf(
    PayHistoryRow("Mon 16 Nov", "Kigali Bakery", "09:12 AM", "3,500", "123456", "16 Nov 2026 · 09:12 AM", "TX40210000"),
    PayHistoryRow("Fri 13 Nov", "Simba Supermarket", "05:19 PM", "18,200", "731902", "13 Nov 2026 · 05:19 PM", "TX40217313"),
    PayHistoryRow("Thu 12 Nov", "Nyamirambo Coffee House", "08:26 AM", "2,800", "204518", "12 Nov 2026 · 08:26 AM", "TX40224626"),
    PayHistoryRow("Fri 6 Nov", "Kigali Bakery", "10:33 AM", "4,200", "123456", "6 Nov 2026 · 10:33 AM", "TX40231939"),
)

internal class PayPointsRow(val letter: String, val name: String, val value: String, val color: Long)

internal val payPoints = listOf(
    PayPointsRow("P", "itunda Points", "0", 0xFF5B54D6),
    PayPointsRow("W", "Pay Money", "120,500", 0xFF3B82F6),
    PayPointsRow("S", "Shop points", "0", 0xFF14B8A6),
    PayPointsRow("M", "MTN MoMo Rewards", "78", 0xFFF59E0B),
    PayPointsRow("B", "Kigali Bus Points", "28", 0xFFEC4899),
    PayPointsRow("Si", "Simba Points", "62", 0xFFEF4444),
    PayPointsRow("I", "Inzozi Market", "0", 0xFF22C55E),
)
