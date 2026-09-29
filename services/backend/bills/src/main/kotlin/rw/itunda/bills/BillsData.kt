package rw.itunda.bills

data class BillProvider(val id: String, val name: String, val category: String, val logo: String, val isActive: Boolean = true)
data class PendingBill(val id: String, val provider: String, val amount: Long, val dueDate: String, val status: String, val accountNumber: String)

/** Same static catalog as backend/src/services/database.ts's billProviders/pending bills. */
object BillsCatalog {
    val providers = listOf(
        BillProvider("b1", "REG - Electricity", "utility", "⚡"),
        BillProvider("b2", "WASAC - Water", "utility", "🚧"),
        BillProvider("b3", "MTN Airtime", "airtime", "📱"),
        BillProvider("b4", "Airtel Airtime", "airtime", "📱"),
        BillProvider("b5", "Irembo Services", "government", "🏛️"),
        BillProvider("b6", "DSTV", "entertainment", "📺"),
        BillProvider("b7", "Startimes", "entertainment", "📡"),
        // Real RRA tax payment (2026-08-27, direct user follow-up: "now we can make
        // pay for tax and moto as well") -- same real model Toss itself uses for
        // 국세/지방세 (national/local tax): the taxpayer looks up what they owe on the
        // real government system first (Rwanda's own etax.rra.gov.rw, or the real
        // MTN/Airtel Mobile Money *182# "Rwanda Revenue" pay-bill menu, which already
        // requires entering a real RRA billing number the exact same way this
        // payBill(accountNumber) flow already does), then pays it through whichever
        // rail they prefer -- itunda is just a new rail, same as REG/WASAC above, not
        // a tax authority itself and not auto-looking-up what's owed (itunda has no
        // real RRA API partnership). No backend logic changes needed beyond this
        // catalog entry: payBill/BillsController are already fully generic over
        // (billId, accountNumber, amount), and web/Android/iOS all fetch this list
        // dynamically from GET /api/v1/bills/providers.
        BillProvider("b8", "RRA - Income Tax", "tax", "🧾"),
        BillProvider("b9", "RRA - VAT", "tax", "🧾"),
        BillProvider("b10", "RRA - Trading License", "tax", "🧾"),
    )

    val pendingBills = listOf(
        PendingBill("bill_1", "REG - Electricity", 35000, "2024-12-15", "pending", "REG-12345"),
        PendingBill("bill_2", "WASAC - Water", 8500, "2024-12-20", "pending", "WASAC-67890"),
    )
}
