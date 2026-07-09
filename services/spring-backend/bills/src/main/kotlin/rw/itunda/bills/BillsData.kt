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
    )

    val pendingBills = listOf(
        PendingBill("bill_1", "REG - Electricity", 35000, "2024-12-15", "pending", "REG-12345"),
        PendingBill("bill_2", "WASAC - Water", 8500, "2024-12-20", "pending", "WASAC-67890"),
    )
}
