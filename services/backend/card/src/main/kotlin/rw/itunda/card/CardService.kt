package rw.itunda.card

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
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
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.UserRepository
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
class CardInvalidFundingAccountException(message: String) : RuntimeException(message)
class CardDailyLimitExceededException(message: String) : RuntimeException(message)
class CardMonthlyLimitExceededException(message: String) : RuntimeException(message)
class CardInvalidDesignException(message: String) : RuntimeException(message)
class CardLostException(message: String) : RuntimeException(message)
class CardClosedException(message: String) : RuntimeException(message)
class CardNotEligibleForReissueException(message: String) : RuntimeException(message)
class CardInvalidPinException(message: String) : RuntimeException(message)
class CardIncorrectCredentialException(message: String) : RuntimeException(message)

private val CARD_PIN_PATTERN = Regex("^\\d{4}$")

data class CardView(
    val id: String,
    val last4: String,
    val dailyLimit: BigDecimal,
    val monthlyLimit: BigDecimal,
    val frozen: Boolean,
    val lost: Boolean,
    val closedAt: Instant?,
    val pinSet: Boolean,
    val issuedAt: Instant,
    val reissuedAt: Instant?,
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
    private val userRepository: UserRepository,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    companion object {
        // itunda has no real production timezone service; Rwanda is a single-timezone
        // country (no DST), so a fixed zone is the correct real choice, same reasoning
        // YouthAccountService's own age-eligibility fix just established.
        private val RWANDA_ZONE: ZoneId = ZoneId.of("Africa/Kigali")

        // Real Toss "결제 계좌" (payment account) reference (2026-09-12) -- deliberately
        // MAIN/PAY only, both always RWF (Account.currency's own real default,
        // confirmed via AccountRepository.findByUserIdAndType never varying it for
        // these two types). FOREIGN_CURRENCY is excluded on purpose: crediting the
        // single global `card_spend_expense` clearing account (implicitly RWF) from a
        // foreign-currency debit would silently misstate itunda's own real expense
        // books by the raw foreign-currency number instead of its RWF value -- the
        // same cross-currency-clearing problem ForeignCurrencyAccountService.convert's
        // own doc comment already solves with a real per-currency `fx_clearing_*`
        // account, which this pass deliberately doesn't build for card spend too.
        val CARD_FUNDING_ACCOUNT_TYPES = setOf(AccountType.MAIN, AccountType.PAY)
    }

    private val secureRandom = SecureRandom()
    // Same convention AuthService.setPin already uses for the login PIN -- no shared
    // PasswordEncoder bean exists anywhere in this codebase (confirmed by grep), every
    // consumer that needs one builds its own BCryptPasswordEncoder() instance.
    private val passwordEncoder = BCryptPasswordEncoder()

    @Transactional
    fun issueCard(userId: String, design: String = DebitCardDesign.DEFAULT): DebitCard {
        // Real gap found (2026-09-07, Card product-completeness pass): every other
        // state-mutating card endpoint already has a rate limiter, this one never
        // did. A real card can only ever be issued once per user (the uniqueness
        // check right below), so this is a modest, consumer-style limit -- not sized
        // for a repeat-caller business actor the way Agents' cashIn/cashOut are.
        rateLimiter.checkLimit("card:issue:$userId", limit = 10, window = Duration.ofHours(1))
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
        // Same real gap, same reasoning as issueCard above -- a rare state toggle,
        // not a high-frequency action, so a modest consumer-style limit fits.
        rateLimiter.checkLimit("card:freeze:$userId", limit = 10, window = Duration.ofHours(1))
        val card = getCardOrThrow(userId)
        card.frozen = true
        val saved = debitCardRepository.save(card)
        notifyCardStateChanged(userId, saved, frozen = true)
        return toView(saved)
    }

    @Transactional
    fun unfreeze(userId: String): CardView {
        // Same real gap, same reasoning as freeze above.
        rateLimiter.checkLimit("card:unfreeze:$userId", limit = 10, window = Duration.ofHours(1))
        val card = getCardOrThrow(userId)
        // Real one-way-state fix: `lost`/`closedAt` must never be self-service-
        // reversible the way an ordinary freeze is -- see DebitCard.kt's own doc
        // comment on why these are separate fields. Reissuing is the only real way
        // back to an active card from either state.
        if (card.lost) throw CardLostException("This card was reported lost or stolen. Reissue a new card to keep using it.")
        if (card.closedAt != null) throw CardClosedException("This card is closed. Reissue a new card to keep using it.")
        card.frozen = false
        val saved = debitCardRepository.save(card)
        notifyCardStateChanged(userId, saved, frozen = false)
        return toView(saved)
    }

    /**
     * Real "분실신고" (report lost or stolen) -- see DebitCard.kt's own doc comment
     * for why this is a distinct, one-way state from the ordinary freeze/unfreeze
     * toggle. Closes a real gap bank-mfe's own CardView had already found and
     * flagged live (its "Report lost or stolen" button used to just relabel the
     * ordinary freeze() call because this distinct flow didn't exist yet).
     */
    @Transactional
    fun reportLost(userId: String): CardView {
        rateLimiter.checkLimit("card:report-lost:$userId", limit = 5, window = Duration.ofHours(1))
        val card = getCardOrThrow(userId)
        if (card.closedAt != null) throw CardClosedException("This card is already closed")
        card.lost = true
        card.frozen = true
        val saved = debitCardRepository.save(card)
        notifyCardLostOrClosed(userId, saved, "reported lost or stolen")
        return toView(saved)
    }

    /**
     * Real "카드 해지하기" (close/cancel card) -- a real, distinct terminal state
     * from `frozen`. Unlike a lost/stolen report, this is the cardholder's own
     * deliberate choice to stop using this card entirely; `reissue` below is the
     * real, honest recovery path from either state, matching how a real bank
     * treats "get a replacement" the same way whether the old card was lost or
     * simply retired.
     */
    @Transactional
    fun closeCard(userId: String): CardView {
        rateLimiter.checkLimit("card:close:$userId", limit = 5, window = Duration.ofHours(1))
        val card = getCardOrThrow(userId)
        if (card.closedAt != null) throw CardClosedException("This card is already closed")
        card.closedAt = Instant.now()
        card.frozen = true
        val saved = debitCardRepository.save(card)
        notifyCardLostOrClosed(userId, saved, "closed")
        return toView(saved)
    }

    /**
     * Real "카드 재발급" (reissue) -- closes the real gap DebitCard.kt's own doc
     * comment on `issueCard` flags: once a card exists, `issueCard` can never be
     * called again (CardAlreadyIssuedException), so a lost/stolen/closed card
     * previously had no real recovery path at all. Regenerates `last4` and clears
     * `lost`/`closedAt`/`pinHash` on the SAME row rather than creating a second
     * DebitCard, since `userId` is uniquely constrained (one real card per user at
     * a time) -- the same honest simplification `last4` itself already documents
     * (a real stored display suffix, not a routable PAN, so there's no physical
     * card-fulfilment step a client needs to wait on).
     */
    @Transactional
    fun reissue(userId: String): CardView {
        rateLimiter.checkLimit("card:reissue:$userId", limit = 5, window = Duration.ofHours(1))
        val card = getCardOrThrow(userId)
        if (!card.lost && card.closedAt == null) {
            throw CardNotEligibleForReissueException("Only a lost, stolen, or closed card can be reissued")
        }
        card.last4 = (secureRandom.nextInt(9000) + 1000).toString()
        card.lost = false
        card.closedAt = null
        card.frozen = false
        card.pinHash = null
        card.reissuedAt = Instant.now()
        val saved = debitCardRepository.save(card)
        val title = "New card issued"
        val body = "Your new itunda card ending in ${saved.last4} is ready. Set a card PIN before your first purchase."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "CARD_REISSUED",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"cardId\":\"${saved.id}\"}",
            ),
        )
        sendCardPushAfterCommit(userId, title, body, saved.id)
        return toView(saved)
    }

    /**
     * Real "카드 비밀번호 변경" (change card PIN) -- a real, itunda-issued 4-digit
     * debit-card PIN, deliberately separate from the 6-digit login PIN (see
     * DebitCard.kt's own doc comment). Step-up auth (verify the caller's current
     * login credential before accepting a new card PIN) reuses the exact same
     * passwordEncoder.matches proof-of-ownership pattern AuthService.setPin
     * already established for changing the login PIN itself.
     */
    @Transactional
    fun setPin(userId: String, newPin: String, currentCredential: String): CardView {
        rateLimiter.checkLimit("card:set-pin:$userId", limit = 5, window = Duration.ofHours(1))
        if (!CARD_PIN_PATTERN.matches(newPin)) {
            throw CardInvalidPinException("Your card PIN must be exactly 4 digits")
        }
        val card = getCardOrThrow(userId)
        val user = userRepository.findById(userId).orElseThrow { CardNotFoundException("User not found") }
        if (!passwordEncoder.matches(currentCredential, user.passwordHash)) {
            throw CardIncorrectCredentialException("Incorrect current password or PIN")
        }
        card.pinHash = passwordEncoder.encode(newPin)
        val saved = debitCardRepository.save(card)
        // Real sibling-asymmetry fix (2026-09-12) -- every other real card-state change
        // in this class (freeze/unfreeze via notifyCardStateChanged, reportLost/
        // closeCard via notifyCardLostOrClosed, reissue with its own notification)
        // sends a real security alert. Changing the card PIN is at least as security-
        // sensitive as any of those, but sent none -- if someone with temporary
        // device+password access changed the cardholder's card PIN, the real
        // cardholder previously had no way to find out.
        val pinChangedTitle = "Card PIN changed"
        val pinChangedBody = "The PIN for your itunda card ending in ${saved.last4} was just changed."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "CARD_PIN_CHANGED",
                title = pinChangedTitle, body = pinChangedBody,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"cardId\":\"${saved.id}\"}",
            ),
        )
        sendCardPushAfterCommit(userId, pinChangedTitle, pinChangedBody, saved.id)
        return toView(saved)
    }

    /**
     * Real, ledger-backed "pay with your itunda card" -- the honest simulation of a
     * card-present purchase this entity's own doc comment describes. Debits the
     * caller's own chosen account (MAIN by default, matching every pre-existing
     * caller's real behavior) the same way every other product in this codebase moves
     * money (LedgerService.postLedgerTransaction), so LedgerService's own
     * frozen-account/insufficient-funds checks apply here too, on top of this card's
     * own frozen/limit checks.
     */
    @Transactional
    fun chargeWithCard(userId: String, amount: BigDecimal, merchantName: String, fundingAccountType: AccountType = AccountType.MAIN): CardChargeResult {
        rateLimiter.checkLimit("card:charge:$userId", limit = 30, window = Duration.ofMinutes(1))
        if (amount <= BigDecimal.ZERO) throw CardInvalidAmountException("Amount must be greater than zero")
        val trimmedMerchant = merchantName.trim()
        if (trimmedMerchant.isEmpty() || trimmedMerchant.length > 200) {
            throw CardInvalidAmountException("Merchant name is required and must be 200 characters or fewer")
        }
        if (fundingAccountType !in CARD_FUNDING_ACCOUNT_TYPES) {
            throw CardInvalidFundingAccountException("Card purchases can only be funded from your Main or Pay account")
        }

        val card = getCardOrThrow(userId)
        if (card.lost) throw CardFrozenException("This card was reported lost or stolen. Reissue a new card to make a purchase.")
        if (card.closedAt != null) throw CardFrozenException("This card is closed. Reissue a new card to make a purchase.")
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

        val account = accountRepository.findByUserIdAndType(userId, fundingAccountType)
            ?: throw CardNoAccountException("No ${fundingAccountType.name.lowercase()} account found for this account")
        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Card purchase - $trimmedMerchant"),
                LedgerLeg("card_spend_expense", LedgerAccountType.CARD_SPEND_EXPENSE, LedgerDirection.CREDIT, amount, "Card purchase - $trimmedMerchant"),
            ),
        )

        // Real gap found (2026-09-07, Card product-completeness pass): real money
        // movement with zero FraudRuleEngine coverage -- AgentService.cashIn/cashOut,
        // BillsService.payBill/buyAirtime, FloatMarketplaceService.acceptRequest, and
        // P2pService.pay/send all already have this exact fix, this sibling service
        // never did. recipientUserId is null -- a free-text merchant name isn't a
        // recurring itunda counterparty the NEW_RECIPIENT rule's shape fits, so only
        // HIGH_VALUE/VELOCITY apply, same reasoning those other call sites use.
        fraudRuleEngine.evaluate(userId, null, amount, result.transactionId)

        val transaction = debitCardTransactionRepository.save(
            DebitCardTransaction(
                id = "cardtx_${UUID.randomUUID()}",
                cardId = card.id,
                userId = userId,
                amount = amount,
                merchantName = trimmedMerchant,
                ledgerTransactionId = result.transactionId,
                fundingAccountType = fundingAccountType,
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
                frozen = card.frozen, lost = card.lost, closedAt = card.closedAt, pinSet = card.pinHash != null,
                issuedAt = card.issuedAt, reissuedAt = card.reissuedAt, design = card.design,
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
            frozen = card.frozen, lost = card.lost, closedAt = card.closedAt, pinSet = card.pinHash != null,
            issuedAt = card.issuedAt, reissuedAt = card.reissuedAt, design = card.design,
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

    /** A lost/stolen report or a closure must always reach the cardholder immediately
     * -- the same real security-alert discipline notifyCardStateChanged already
     * established for freeze/unfreeze, reused here for these two one-way states. */
    private fun notifyCardLostOrClosed(userId: String, card: DebitCard, verb: String) {
        val title = if (verb.startsWith("reported")) "Card reported lost" else "Card closed"
        val body = "Your itunda card ending in ${card.last4} was $verb. No purchases can be made on it anymore."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "CARD_STATE_CHANGED",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"cardId\":\"${card.id}\"}",
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
