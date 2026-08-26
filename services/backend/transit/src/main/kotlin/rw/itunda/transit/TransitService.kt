package rw.itunda.transit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.TransitBalance
import rw.itunda.core.domain.TransitOperator
import rw.itunda.core.domain.TransitTrip
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.TransitBalanceRepository
import rw.itunda.core.repository.TransitTripRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class TransitNoAccountException(message: String) : RuntimeException(message)
class TransitInvalidAmountException(message: String) : RuntimeException(message)
class TransitInvalidOperatorException(message: String) : RuntimeException(message)
class TransitInvalidFareException(message: String) : RuntimeException(message)
class TransitInsufficientBalanceException(message: String) : RuntimeException(message)

data class TransitBalanceView(
    val balance: BigDecimal,
    val createdAt: Instant,
)

data class TransitTapResult(
    val trip: TransitTrip,
    val balance: TransitBalanceView,
)

/**
 * Real Kigali public-transit stored-value balance -- see
 * [rw.itunda.core.domain.TransitBalance]'s own doc comment for the full sourced account
 * of Kigali's real Tap&Go fare system and the honest boundary this simulates.
 */
@Service
class TransitService(
    private val transitBalanceRepository: TransitBalanceRepository,
    private val transitTripRepository: TransitTripRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    fun getMyBalance(userId: String): TransitBalanceView = toView(getBalanceOrThrow(userId))

    fun getMyTrips(userId: String, pageable: Pageable): Page<TransitTrip> =
        transitTripRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)

    /**
     * Real, ledger-backed top-up of the user's transit balance from their real MAIN
     * account -- get-or-create rather than a separate "activate" step, matching how the
     * real physical Tap&Go card itself needs no separate activation, just a purchase or
     * a top-up.
     */
    @Transactional
    fun topUp(userId: String, amount: BigDecimal): TransitBalanceView {
        rateLimiter.checkLimit("transit:topup:$userId", limit = 20, window = Duration.ofMinutes(1))
        if (amount <= BigDecimal.ZERO) throw TransitInvalidAmountException("Top-up amount must be greater than zero")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw TransitNoAccountException("No main account found for this account")

        val balance = transitBalanceRepository.findByUserId(userId)
            ?: transitBalanceRepository.save(TransitBalance(id = "transit_${UUID.randomUUID()}", userId = userId))
        // Lock the just-fetched-or-created row for the duration of this top-up, same
        // check-then-write discipline CardService.chargeWithCard's own doc comment
        // establishes for a card row -- a concurrent top-up and fare tap for this same
        // user must not race the balance mutation below.
        val locked = transitBalanceRepository.findByIdForUpdate(balance.id).orElseThrow { IllegalStateException("Unknown transit balance ${balance.id}") }

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transit balance top-up"),
                LedgerLeg("transit_balance_payable", LedgerAccountType.TRANSIT_BALANCE_PAYABLE, LedgerDirection.CREDIT, amount, "Transit balance top-up"),
            ),
        )

        locked.balance = locked.balance.add(amount)
        val saved = transitBalanceRepository.save(locked)
        return toView(saved)
    }

    /**
     * Real "tap to pay your fare" -- the honest simulation this entity's own doc
     * comment describes. `fare` is rider-chosen within Kigali's real sourced 200-500
     * RWF range (itunda has no real GPS-derived distance to compute an exact fare from,
     * same reasoning [rw.itunda.core.domain.TransitTrip]'s own doc comment gives).
     */
    @Transactional
    fun tapFare(userId: String, operator: String, fare: BigDecimal): TransitTapResult {
        rateLimiter.checkLimit("transit:tap:$userId", limit = 30, window = Duration.ofMinutes(1))
        if (operator !in TransitOperator.ALL) {
            throw TransitInvalidOperatorException("'$operator' is not a real Kigali transit operator itunda supports")
        }
        if (fare < TransitTrip.MIN_FARE || fare > TransitTrip.MAX_FARE) {
            throw TransitInvalidFareException("Fare must be between ${TransitTrip.MIN_FARE} and ${TransitTrip.MAX_FARE} RWF, Kigali's real sourced fare range")
        }

        val balance = getBalanceOrThrow(userId)
        val locked = transitBalanceRepository.findByIdForUpdate(balance.id).orElseThrow { IllegalStateException("Unknown transit balance ${balance.id}") }
        if (locked.balance < fare) {
            throw TransitInsufficientBalanceException("Your transit balance is too low for this fare. Top up and try again.")
        }

        val result = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg("transit_balance_payable", LedgerAccountType.TRANSIT_BALANCE_PAYABLE, LedgerDirection.DEBIT, fare, "Transit fare - $operator"),
                LedgerLeg("transit_fare_expense", LedgerAccountType.TRANSIT_FARE_EXPENSE, LedgerDirection.CREDIT, fare, "Transit fare - $operator"),
            ),
        )

        locked.balance = locked.balance.subtract(fare)
        val savedBalance = transitBalanceRepository.save(locked)

        val trip = transitTripRepository.save(
            TransitTrip(
                id = "transittrip_${UUID.randomUUID()}",
                userId = userId,
                operator = operator,
                fare = fare,
                ledgerTransactionId = result.transactionId,
            ),
        )

        return TransitTapResult(trip = trip, balance = toView(savedBalance))
    }

    private fun getBalanceOrThrow(userId: String): TransitBalance =
        transitBalanceRepository.findByUserId(userId) ?: throw TransitNoAccountException("No itunda transit balance found for this account -- top up to get started")

    private fun toView(balance: TransitBalance): TransitBalanceView =
        TransitBalanceView(balance = balance.balance, createdAt = balance.createdAt)
}
