package com.itunda.app.data.models

data class Stock(
    val id: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val marketCap: String,
    val volume: Int
)

data class StockHolding(
    val stockId: String,
    val symbol: String,
    val name: String,
    val shares: Int,
    val avgPrice: Double,
    val currentPrice: Double,
    val totalValue: Double,
    val totalReturn: Double,
    val returnPercent: Double
)

data class StockListResponse(
    val success: Boolean,
    val stocks: List<Stock>
)

data class PortfolioResponse(
    val success: Boolean,
    val holdings: List<StockHolding>,
    val totalValue: Double,
    val totalReturn: Double,
    val totalReturnPercent: Double
)

data class StockOrderRequest(
    val stockId: String,
    val shares: Int,
    val price: Double
)

data class StockOrderResponse(
    val success: Boolean,
    val orderId: String?,
    val message: String?
)
