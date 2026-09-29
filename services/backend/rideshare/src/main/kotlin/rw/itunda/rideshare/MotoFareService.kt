package rw.itunda.rideshare

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.account.AutoTopUpService
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.MotoFareTrip
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.CustomerPaymentCodeRepository
import rw.itunda.core.repository.MotoFareTripRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MotoFareCodeNotFoundException(message: String) : RuntimeException(message)
class MotoFareCodeNotPayableException(message: String) : RuntimeException(message)
class MotoFareSelfCollectionException(message: String) : RuntimeException(message)
class MotoFareInvalidFareException(message: String) : RuntimeException(message)
class MotoFareNoAccountException(message: String) : RuntimeException(message)

data class MotoFareCollectResult(val fare: BigDecimal, val collectedAt: Instant)

/**
 * Real "tap to pay your moto-taxi fare" -- see [rw.itunda.core.domain.MotoFareTrip]'s
 * own doc comment for the full sourced account of Kigali's real smart-metered fares
 * and why this is a real, direct WALLET-to-WALLET payment (the driver IS a real itunda
 * user, unlike Transit's external, unmodeled bus operator) rather than a simulated
 * expense against an itunda-owned clearing account. Reuses
 * [rw.itunda.core.domain.CustomerPaymentCode] exactly like
 * [rw.itunda.transit.TransitService.tapFareByCode] does -- the same rider-presented
 * code already shown as a QR via "My payment code."
 */
@Service
class MotoFareService(
    private val motoFareTripRepository: MotoFareTripRepository,
    private val accountRepository: AccountRepository,
    private val customerPaymentCodeRepository: CustomerPaymentCodeRepository,
    private val autoTopUpService: AutoTopUpService,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    private val paymentEligibleAccountTypes = setOf(AccountType.PAY, AccountType.MAIN, AccountType.FOREIGN_CURRENCY)

    fun getMyTripsAsRider(userId: String, pageable: Pageable): Page<MotoFareTrip> =
        motoFareTripRepository.findByRiderUserIdOrderByCreatedAtDesc(userId, pageable)

    fun getMyTripsAsDriver(userId: String, pageable: Pageable): Page<MotoFareTrip> =
        motoFareTripRepository.findByDriverUserIdOrderByCreatedAtDesc(userId, pageable)

    @Transactional
    fun collectFare(driverUserId: String, code: String, fare: BigDecimal): MotoFareCollectResult {
        rateLimiter.checkLimit("moto-fare:collect:$driverUserId", limit = 60, window = Duration.ofMinutes(1))
        if (fare < MotoFareTrip.MIN_FARE || fare > MotoFareTrip.MAX_FARE) {
            throw MotoFareInvalidFareException("Fare must be between ${MotoFareTrip.MIN_FARE} and ${MotoFareTrip.MAX_FARE} RWF, Kigali's real sourced moto-taxi fare range")
        }
        val paymentCode = customerPaymentCodeRepository.findByCode(code)
            ?: throw MotoFareCodeNotFoundException("This code was not found")
        if (paymentCode.usedAt != null) {
            throw MotoFareCodeNotPayableException("This code has already been used")
        }
        if (paymentCode.expiresAt.isBefore(Instant.now())) {
            throw MotoFareCodeNotPayableException("This code has expired -- ask the rider to refresh their Pay screen")
        }
        val riderUserId = paymentCode.userId
        if (riderUserId == driverUserId) {
            throw MotoFareSelfCollectionException("That's your own code -- a rider taps their own code against the driver's device, not their own")
        }

        // Same real default MerchantService.chargeByCustomerCode already establishes
        // for a customer-presented-code charge: itunda Pay money, auto-funded from
        // Bank (then an external linked account) if short.
        var riderAccount = paymentCode.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: accountRepository.findByUserIdAndType(riderUserId, AccountType.PAY)
            ?: throw MotoFareNoAccountException("No itunda Pay money found for this rider")
        if (riderAccount.type !in paymentEligibleAccountTypes) {
            throw MotoFareNoAccountException("This account can't be used to pay a moto-taxi fare")
        }
        if (paymentCode.accountId == null) {
            riderAccount = autoTopUpService.ensureSufficientPayBalance(riderUserId, riderAccount, fare)
        }
        val driverAccount = accountRepository.findByUserIdAndType(driverUserId, AccountType.PAY)
            ?: accountRepository.findByUserIdAndType(driverUserId, AccountType.MAIN)
            ?: throw MotoFareNoAccountException("No itunda account found to receive this fare")

        // Real, direct peer-to-peer payment -- no fee, no itunda-owned clearing
        // account: unlike a registered merchant collection, this is honestly just two
        // real itunda users' own accounts moving money between each other.
        val result = ledgerService.postLedgerTransaction(
            riderAccount.currency,
            listOf(
                LedgerLeg(riderAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, fare, "Moto-taxi fare"),
                LedgerLeg(driverAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, fare, "Moto-taxi fare collected"),
            ),
        )
        // Real gap found (2026-09-13, repo-wide FraudRuleEngine re-sweep): a real,
        // direct WALLET-to-WALLET payment between two known itunda users with zero
        // fraud coverage -- P2pService/MerchantService.chargeByCustomerCode (the exact
        // same customer-presented-code shape) already have it, this sibling never did.
        fraudRuleEngine.evaluate(riderUserId, driverUserId, fare, result.transactionId)

        motoFareTripRepository.save(
            MotoFareTrip(
                id = "motofaretrip_${UUID.randomUUID()}",
                riderUserId = riderUserId,
                driverUserId = driverUserId,
                fare = fare,
                ledgerTransactionId = result.transactionId,
            ),
        )

        paymentCode.usedAt = Instant.now()
        customerPaymentCodeRepository.save(paymentCode)

        return MotoFareCollectResult(fare = fare, collectedAt = Instant.now())
    }
}
