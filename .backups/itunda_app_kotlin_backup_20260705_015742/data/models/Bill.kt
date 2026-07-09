package com.itunda.app.data.models

data class BillProvider(
    val id: String,
    val name: String,
    val icon: String,
    val category: String,
    val isAirtime: Boolean
)

data class PendingBill(
    val id: String,
    val providerId: String,
    val providerName: String,
    val accountNumber: String,
    val amount: Double,
    val dueDate: String,
    val status: String
)

data class BillsResponse(
    val success: Boolean,
    val pendingBills: List<PendingBill>
)

data class BillProvidersResponse(
    val success: Boolean,
    val providers: List<BillProvider>
)

data class PayBillRequest(
    val billId: String,
    val amount: Double,
    val walletId: String
)

data class AirtimeRequest(
    val phone: String,
    val amount: Double,
    val network: String
)

data class PayBillResponse(
    val success: Boolean,
    val transactionId: String?,
    val message: String?
)
