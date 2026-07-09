package com.itunda.app.data.models

data class Wallet(
    val id: String,
    val type: String,
    val name: String,
    val number: String,
    val balance: Double,
    val currency: String,
    val icon: String,
    val connected: Boolean
)

data class BalanceResponse(
    val success: Boolean,
    val wallets: List<Wallet>,
    val totalBalance: Double
)

data class Transaction(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val amount: Double,
    val date: String,
    val status: String,
    val sender: String?,
    val recipient: String?
)

data class TransactionsResponse(
    val success: Boolean,
    val transactions: List<Transaction>
)

data class TransferRequest(
    val fromWalletId: String,
    val toContactId: String,
    val amount: Double,
    val memo: String?
)

data class TransferResponse(
    val success: Boolean,
    val transactionId: String?,
    val message: String?
)
