package com.itunda.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itunda.app.data.api.RetrofitClient
import com.itunda.app.data.models.Stock
import com.itunda.app.data.models.StockHolding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StocksUiState(
    val isLoading: Boolean = false,
    val stocks: List<Stock> = emptyList(),
    val holdings: List<StockHolding> = emptyList(),
    val totalValue: Double = 0.0,
    val totalReturn: Double = 0.0,
    val totalReturnPercent: Double = 0.0,
    val selectedStock: Stock? = null,
    val orderShares: String = "",
    val isOrdering: Boolean = false,
    val orderResult: String? = null,
    val error: String? = null
)

class StocksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StocksUiState())
    val uiState: StateFlow<StocksUiState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val stocksResp = RetrofitClient.getApi().getStocks()
                val portfolioResp = RetrofitClient.getApi().getPortfolio()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stocks = stocksResp.body()?.stocks ?: emptyList(),
                    holdings = portfolioResp.body()?.holdings ?: emptyList(),
                    totalValue = portfolioResp.body()?.totalValue ?: 0.0,
                    totalReturn = portfolioResp.body()?.totalReturn ?: 0.0,
                    totalReturnPercent = portfolioResp.body()?.totalReturnPercent ?: 0.0
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stocks = demoStocks,
                    holdings = demoHoldings,
                    totalValue = demoHoldings.sumOf { it.totalValue },
                    totalReturn = demoHoldings.sumOf { it.totalReturn },
                    totalReturnPercent = 4.6,
                    error = null
                )
            }
        }
    }

    fun selectStock(stock: Stock) {
        _uiState.value = _uiState.value.copy(selectedStock = stock, orderShares = "")
    }

    fun updateOrderShares(shares: String) {
        _uiState.value = _uiState.value.copy(orderShares = shares)
    }

    fun buyStock() {
        val stock = _uiState.value.selectedStock ?: return
        val shares = _uiState.value.orderShares.toIntOrNull() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOrdering = true, error = null)
            try {
                val response = RetrofitClient.getApi().buyStock(
                    com.itunda.app.data.models.StockOrderRequest(stock.id, shares, stock.price)
                )
                _uiState.value = _uiState.value.copy(
                    isOrdering = false,
                    orderResult = response.body()?.message ?: "Buy order placed",
                    selectedStock = null
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isOrdering = false,
                    orderResult = "Demo buy order queued",
                    selectedStock = null,
                    error = null
                )
            }
        }
    }

    fun sellStock(stockId: String) {
        val holding = _uiState.value.holdings.find { it.stockId == stockId } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOrdering = true, error = null)
            try {
                val response = RetrofitClient.getApi().sellStock(
                    com.itunda.app.data.models.StockOrderRequest(stockId, holding.shares, holding.currentPrice)
                )
                _uiState.value = _uiState.value.copy(
                    isOrdering = false,
                    orderResult = response.body()?.message ?: "Sell order placed"
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isOrdering = false,
                    orderResult = "Demo sell order queued",
                    error = null
                )
            }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(orderResult = null, error = null, selectedStock = null)
    }

    private val demoStocks = listOf(
        Stock(
            id = "bok",
            symbol = "BOK",
            name = "Bank of Kigali Group PLC",
            price = 390.0,
            change = 5.0,
            changePercent = 1.3,
            marketCap = "RWF 382B",
            volume = 4200
        ),
        Stock(
            id = "mtnr",
            symbol = "MTNR",
            name = "MTN Rwanda PLC",
            price = 178.0,
            change = 2.0,
            changePercent = 1.14,
            marketCap = "RWF 238B",
            volume = 9100
        ),
        Stock(
            id = "blr",
            symbol = "BLR",
            name = "BRALIRWA PLC",
            price = 165.0,
            change = -1.0,
            changePercent = -0.6,
            marketCap = "RWF 171B",
            volume = 3600
        )
    )

    private val demoHoldings = listOf(
        StockHolding(
            stockId = "bok",
            symbol = "BOK",
            name = "Bank of Kigali Group PLC",
            shares = 120,
            avgPrice = 370.0,
            currentPrice = 390.0,
            totalValue = 46800.0,
            totalReturn = 2400.0,
            returnPercent = 5.4
        ),
        StockHolding(
            stockId = "mtnr",
            symbol = "MTNR",
            name = "MTN Rwanda PLC",
            shares = 180,
            avgPrice = 172.0,
            currentPrice = 178.0,
            totalValue = 32040.0,
            totalReturn = 1080.0,
            returnPercent = 3.5
        )
    )
}
