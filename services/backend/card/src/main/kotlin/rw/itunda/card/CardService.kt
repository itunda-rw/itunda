package rw.itunda.card

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.DebitCardDesign
import rw.itunda.core.domain.DebitCardTransaction
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class CardAlreadyIssuedException(message: String) : RuntimeException(message)
class CardNotFoundException(message: String) : RuntimeException(message)
class CardNoAccountException(message: String) : RuntimeException(message)
class CardFrozenException(message: String) : RuntimeException(message)
class CardInvalidLimitException(message: String) : RuntimeException(message)
class CardInvalidAmountException(message: String) : RuntimeException(message)
class CardDailyLimitExceededException(message: String) : RuntimeException(message)
class CardMonthlyLimitExceededException(message: String) : RuntimeException(message)
class CardInvalidDesignException(message: String) : RuntimeException(message)

data class CardView(
    val id: String,
    val last4: String,
    val dailyLimit: BigDecimal,
    val monthlyLimit: BigDecimal,
    val frozen: Boolean,
    val issuedAt: Instant,
    val design: String,
    val spentToday: BigDecimal,
    val spentThisMonth: BigDecimal,
    val remainingToday: BigDecimal,
    val remainingThisMonth: BigDecimal,
)

data class CardChargeResult(
    val transaction: DebitCardTransaction,
    val card: CardView,
)

/**
 * Real Toss Bank 체크카드 (check/debit card) -- see [rw.itunda.core.domain.DebitCard]'s
 * own doc comment for the full sourced account of the real feature this mirrors and the
 * honest boundary around "paying with your card" not riding a real card-network rail.
 */
@Service
class CardService(
    private val debitCardRepository: DebitCardRepository,
    private val debitCardTransactionRepository: DebitCardTransactionRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // itunda has no real production timezone service; Rwanda is a single-timezone
        // country (no DST), so a fixed zone is the correct real choice, same reasoning
        // YouthAccountService's own age-eligibility fix just established.
        private val RWANDA_ZONE: ZoneId = ZoneId.of("Africa/Kigali")
    }

    private val secureRandom = SecureRandom()

    @Transactional
    fun issueCard(userId: String, design: String = DebitCardDesign.DEFAULT): DebitCard {
        if (debitCardRepository.findByUserId(userId) != null) {
            throw CardAlreadyIssuedException("You already have an itunda debit card")
        }
        if (design !in DebitCardDesign.ALL) {
            throw CardInvalidDesignException("'$design' is not a real itunda card design")
        }
        // A real routable PAN needs a real card-network partnership itunda doesn't
        // have (see this entity's own doc comment) -- last4 is real stored display
        // data, generated with SecureRandom (a card-adjacent secret-shaped value,
        // same reasoning UserVerificationService.sendPhoneVerificationCode's OTP fix just
        // established), not a value anyone could derive from the account.
        val last4 = (secureRandom.nextInt(9000) + 1000).toString()
        val card = debitCardRepository.save(
            DebitCard(
                id = "card_${UUID.randomUUID()}",
                userId = userId,
                last4 = last4,
                dailyLimit = DebitCard.DEFAULT_DAILY_LIMIT,
                monthlyLimit = DebitCard.DEFAULT_MONTHLY_LIMIT,
                design = design,
            ),
        )
        return card
    }

    fun getMyCard(userId: String): CardView = toView(getCardOrThrow(userId))

    fun getMyTransactions(userId: String, pageable: Pageable): Page<DebitCardTransaction> {
        val card = getCardOrThrow(userId)
        return debitCardTransactionRepository.findByCardIdOrderByCreatedAtDesc(card.id, pageable)
    }

    @Transactional
    fun setLimits(userId: String, dailyLimit: BigDecimal, monthlyLimit: BigDecimal): CardView {
        rateLimiter.checkLimit("card:set-limits:$userId", limit = 10, window = Duration.ofMinutes(1))
        if (dailyLimit <= BigDecimal.ZERO || monthlyLimit <= BigDecimal.ZERO) {
            throw CardInvalidLimitException("Limits must be greater than zero")
        }
        if (dailyLimit > DebitCard.MAX_LIMIT || monthlyLimit > DebitCard.MAX_LIMIT) {
            throw CardInvalidLimitException("Limits may not exceed ${DebitCard.MAX_LIMIT} RWF")
        }
        val card = getCardOrThrow(userId)
        card.dailyLimit = dailyLimit
        card.monthlyLimit = monthlyLimit
        return toView(debitCardRepository.save(card))
    }

    @Transactional
    fun freeze(userId: String): CardView {
        val card = getCardOrThrow(userId)
        card.frozen = true
        val saved = debitCardRepository.save(card)
        notifyCardStateChanged(userId, saved, frozen = true)
        return toView(saved)
    }

    @Transactional
    fun unfreeze(userId: String): CardView {
        val card = getCardOrThrow(userId)
        card.frozen = false
        val saved = debitCardRepository.save(card)
        notifyCardStateChanged(userId, saved, frozen = false)
        return toView(saved)
    }

    /**
     * Real, ledger-backed "pay with your itunda card" -- the honest simulation of a
     * card-present purchase this entity's own doc comment describes. Debits the
     * caller's real MAIN account the same way every other product in this codebase
     * moves money (LedgerService.postLedgerTransaction), so LedgerService's own
     * frozen-account/insufficient-funds checks apply here too, on top of this card's
     * own frozen/limit checks.
     */
    @Transactional
    fun chargeWithCard(userId: String, amount: BigDecimal, merchantName: String): CardChargeResult {
        rateLimiter.checkLimit("card:charge:$userId", limit = 30, window = Duration.ofMinutes(1))
        if (amount <= BigDecimal.ZERO) throw CardInvalidAmountException("Amount must be greater than zero")
        val trimmedMerchant = merchantName.trim()
        if (trimmedMerchant.isEmpty() || trimmedMerchant.length > 200) {
            throw CardInvalidAmountException("Merchant name is required and must be 200 characters or fewer")
        }

        val card = getCardOrThrow(userId)
        if (card.frozen) throw CardFrozenException("This card is frozen. Unfreeze it to make a purchase.")

        // Real bug found live (2026-08-02): the daily/monthly limit check below reads a
        // live SUM over DebitCardTransaction rows, not a mutation of `card` itself, so
        // `@Version` on DebitCard never guards it -- two concurrent charges for this
        // same card could both read the same pre-charge sum and both pass the limit
        // check before either commits. Locking the card row here serializes concurrent
        // charges on THIS card so the sum-check-then-insert below is actually atomic.
        debitCardRepository.findByIdForUpdate(card.id)

        val now = Instant.now()
        val startOfToday = LocalDate.now(RWANDA_ZONE).atStartOfDay(RWANDA_ZONE).toInstant()
        val startOfMonth = LocalDate.now(RWANDA_ZONE).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(RWANDA_ZONE).toInstant()

        val spentToday = debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(card.id, startOfToday)
        if (spentToday.add(amount) > card.dailyLimit) {
            val remaining = card.dailyLimit.subtract(spentToday).max(BigDecimal.ZERO)
            throw CardDailyLimitExceededException("This purchase would exceed your daily card limit. $remaining RWF remaining today.")
        }
        val spentThisMonth = debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(card.id, startOfMonth)
        if (spentThisMonth.add(amount) > card.monthlyLimit) {
            val remaining = card.monthlyLimit.subtract(spentThisMonth).max(BigDecimal.ZERO)
            throw CardMonthlyLimitExceededException("This purchase would exceed your monthly card limit. $remaining RWF remaining this month.")
        }

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw CardNoAccountException("No main account found for this account")
        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Card purchase - $trimmedMerchant"),
                LedgerLeg("card_spend_expense", LedgerAccountType.CARD_SPEND_EXPENSE, LedgerDirection.CREDIT, amount, "Card purchase - $trimmedMerchant"),
            ),
        )

        val transaction = debitCardTransactionRepository.save(
            DebitCardTransaction(
                id = "cardtx_${UUID.randomUUID()}",
                cardId = card.id,
                userId = userId,
                amount = amount,
                merchantName = trimmedMerchant,
                ledgerTransactionId = result.transactionId,
                createdAt = now,
            ),
        )

        val title = "Card used"
        val body = "$amount RWF at $trimmedMerchant"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "CARD_CHARGE",
                title = title, body = body,
                isRead = false, createdAt = now, dataJson = "{\"cardId\":\"${card.id}\",\"amount\":\"$amount\"}",
            ),
        )
        sendCardPushAfterCommit(userId, title, body, card.id)

        val spentTodayAfter = spentToday.add(amount)
        val spentThisMonthAfter = spentThisMonth.add(amount)
        return CardChargeResult(
            transaction = transaction,
            card = CardView(
                id = card.id, last4 = card.last4, dailyLimit = card.dailyLimit, monthlyLimit = card.monthlyLimit,
                frozen = card.frozen, issuedAt = card.issuedAt, design = card.design,
                spentToday = spentTodayAfter, spentThisMonth = spentThisMonthAfter,
                remainingToday = card.dailyLimit.subtract(spentTodayAfter).max(BigDecimal.ZERO),
                remainingThisMonth = card.monthlyLimit.subtract(spentThisMonthAfter).max(BigDecimal.ZERO),
            ),
        )
    }

    private fun getCardOrThrow(userId: String): DebitCard =
        debitCardRepository.findByUserId(userId) ?: throw CardNotFoundException("No itunda debit card found for this account")

    private fun toView(card: DebitCard): CardView {
        val startOfToday = LocalDate.now(RWANDA_ZONE).atStartOfDay(RWANDA_ZONE).toInstant()
        val startOfMonth = LocalDate.now(RWANDA_ZONE).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(RWANDA_ZONE).toInstant()
        val spentToday = debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(card.id, startOfToday)
        val spentThisMonth = debitCardTransactionRepository.sumAmountByCardIdAndCreatedAtSince(card.id, startOfMonth)
        return CardView(
            id = card.id, last4 = card.last4, dailyLimit = card.dailyLimit, monthlyLimit = card.monthlyLimit,
            frozen = card.frozen, issuedAt = card.issuedAt, design = card.design,
            spentToday = spentToday, spentThisMonth = spentThisMonth,
            remainingToday = card.dailyLimit.subtract(spentToday).max(BigDecimal.ZERO),
            remainingThisMonth = card.monthlyLimit.subtract(spentThisMonth).max(BigDecimal.ZERO),
        )
    }

    /** A freeze/unfreeze security alert must not describe a state that rolled back. */
    private fun notifyCardStateChanged(userId: String, card: DebitCard, frozen: Boolean) {
        val title = if (frozen) "Card frozen" else "Card unfrozen"
        val body = if (frozen) {
            "Your itunda card ending in ${card.last4} is frozen. No purchases can be made until you unfreeze it."
        } else {
            "Your itunda card ending in ${card.last4} is active again."
        }
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "CARD_STATE_CHANGED",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"cardId\":\"${card.id}\",\"frozen\":\"$frozen\"}",
            ),
        )
        sendCardPushAfterCommit(userId, title, body, card.id)
    }

    private fun sendCardPushAfterCommit(userId: String, title: String, body: String, cardId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("cardId" to cardId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
