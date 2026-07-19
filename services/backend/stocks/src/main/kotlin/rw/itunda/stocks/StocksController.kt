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

@RestController
@RequestMapping("/api/v1/stocks")
class StocksController(private val stocksService: StocksService, private val idempotencyService: IdempotencyService) {

    @GetMapping
    fun getStocks() = ResponseEntity.ok(mapOf("success" to true, "stocks" to stocksService.getStocks()))

    @GetMapping("/portfolio")
    fun getPortfolio(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "portfolio" to stocksService.getPortfolio(currentUser.userId)))

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

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(StockNotFoundException::class)
    fun handleNotFound(ex: StockNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("STOCK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoWalletException::class)
    fun handleNoWallet(ex: NoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NotEnoughSharesException::class)
    fun handleNotEnough(ex: NotEnoughSharesException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_SHARES", ex.message ?: "Insufficient shares"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))
}
