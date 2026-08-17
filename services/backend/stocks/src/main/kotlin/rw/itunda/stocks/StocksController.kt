package rw.itunda.stocks

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class TradeStockRequest(val stockId: String, val shares: BigDecimal)
data class FundInvestmentRequest(val amount: BigDecimal)
// Real Toss Securities 목표가 알림 (target price alert) -- see
// StocksService.setPriceAlert's own doc comment.
data class SetPriceAlertRequest(val targetPrice: BigDecimal, val direction: String)

@RestController
@RequestMapping("/api/v1/stocks")
class StocksController(private val stocksService: StocksService, private val idempotencyService: IdempotencyService) {

    @GetMapping
    fun getStocks() = ResponseEntity.ok(mapOf("success" to true, "stocks" to stocksService.getStocks()))

    // Real price history (2026-07-19) -- see StocksService.getPriceHistory's own doc
    // comment. Defaults to 30 days, matching a real securities app's default chart range.
    @GetMapping("/{stockId}/history")
    fun getPriceHistory(
        @PathVariable stockId: String,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "history" to stocksService.getPriceHistory(stockId, days)))

    @GetMapping("/portfolio")
    fun getPortfolio(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "portfolio" to stocksService.getPortfolio(currentUser.userId)))

    // Real portfolio value chart (2026-07-19) -- see StocksService.getPortfolioHistory's
    // own doc comment for the honest scoping (current holdings applied to real
    // historical prices, not a true historical share-count reconstruction).
    @GetMapping("/portfolio/history")
    fun getPortfolioHistory(
        @RequestParam(defaultValue = "30") days: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "history" to stocksService.getPortfolioHistory(currentUser.userId, days)))

    // Real bug fix (2026-07-27) -- see StocksService.fundInvestmentWallet's own doc
    // comment. Real money-moving internal transfer, so Idempotency-Key required, same
    // convention as every other money-moving POST in this codebase.
    @PostMapping("/fund")
    fun fund(
        @RequestBody request: FundInvestmentRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/stocks/fund", idempotencyKey, request) {
            val transaction = stocksService.fundInvestmentWallet(currentUser.userId, request.amount)
            200 to mapOf("success" to true, "transaction" to transaction)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/buy")
    fun buy(
        @RequestBody request: TradeStockRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/stocks/buy", idempotencyKey, request) {
            val transaction = stocksService.buyStock(currentUser.userId, request.stockId, request.shares)
            200 to mapOf("success" to true, "message" to "Bought ${request.shares} shares successfully", "transaction" to transaction)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/sell")
    fun sell(
        @RequestBody request: TradeStockRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/stocks/sell", idempotencyKey, request) {
            val transaction = stocksService.sellStock(currentUser.userId, request.stockId, request.shares)
            200 to mapOf("success" to true, "message" to "Sold ${request.shares} shares successfully", "transaction" to transaction)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real stock watchlist (2026-07-19) -- see StocksService.watchStock's own doc
    // comment. Not money-moving, no Idempotency-Key requirement, same convention
    // EatsFavoriteController's own toggle endpoints already use.
    @PostMapping("/{stockId}/watch")
    fun watch(
        @PathVariable stockId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val watch = stocksService.watchStock(currentUser.userId, stockId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "watch" to watch))
    }

    @DeleteMapping("/{stockId}/watch")
    fun unwatch(
        @PathVariable stockId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        stocksService.unwatchStock(currentUser.userId, stockId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/watchlist")
    fun getWatchlist(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "watchlist" to stocksService.getWatchlist(currentUser.userId)))

    // Real gap fix (2026-08-18) -- see StocksService.getPriceAlert's own doc comment.
    // Lets a client show current alert state (or its absence) when a stock detail
    // screen re-opens, instead of only ever being able to write-and-forget.
    @GetMapping("/{stockId}/price-alert")
    fun getPriceAlert(
        @PathVariable stockId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val watch = stocksService.getPriceAlert(currentUser.userId, stockId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "targetPrice" to watch?.targetPrice,
                "targetDirection" to watch?.targetDirection,
                "alertTriggeredAt" to watch?.alertTriggeredAt,
            ),
        )
    }

    // Real Toss Securities 목표가 알림 (target price alert) -- see
    // StocksService.setPriceAlert's own doc comment.
    @PostMapping("/{stockId}/price-alert")
    fun setPriceAlert(
        @PathVariable stockId: String,
        @RequestBody request: SetPriceAlertRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val watch = stocksService.setPriceAlert(currentUser.userId, stockId, request.targetPrice, request.direction)
        return ResponseEntity.ok(mapOf("success" to true, "watch" to watch))
    }

    @DeleteMapping("/{stockId}/price-alert")
    fun clearPriceAlert(
        @PathVariable stockId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val watch = stocksService.clearPriceAlert(currentUser.userId, stockId)
        return ResponseEntity.ok(mapOf("success" to true, "watch" to watch))
    }

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(StockNotFoundException::class)
    fun handleNotFound(ex: StockNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("STOCK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidPriceHistoryRangeException::class)
    fun handleInvalidRange(ex: InvalidPriceHistoryRangeException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRICE_HISTORY_RANGE", ex.message ?: "Bad request"))

    @ExceptionHandler(NoWalletException::class)
    fun handleNoWallet(ex: NoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidFundingAmountException::class)
    fun handleInvalidFunding(ex: InvalidFundingAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(NotEnoughSharesException::class)
    fun handleNotEnough(ex: NotEnoughSharesException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_SHARES", ex.message ?: "Insufficient shares"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(InvalidPriceAlertException::class)
    fun handleInvalidPriceAlert(ex: InvalidPriceAlertException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRICE_ALERT", ex.message ?: "Bad request"))
}
