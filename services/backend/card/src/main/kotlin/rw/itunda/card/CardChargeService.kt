package rw.itunda.card

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.DebitCardTransaction
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.DebitCardTransactionRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private val CARD_CHARGE_RWANDA_ZONE: ZoneId = ZoneId.of("Africa/Kigali")

// Real Toss "결제 계좌" (payment account) reference (2026-09-12) -- deliberately
// MAIN/PAY only, both always RWF (Account.currency's own real default, confirmed via
// AccountRepository.findByUserIdAndType never varying it for these two types).
// FOREIGN_CURRENCY is excluded on purpose: crediting the single global
// `card_spend_expense` clearing account (implicitly RWF) from a foreign-currency debit
// would silently misstate itunda's own real expense books by the raw foreign-currency
// number instead of its RWF value -- the same cross-currency-clearing problem
// ForeignCurrencyAccountService.convert's own doc comment already solves with a real
// per-currency `fx_clearing_*` account, which this pass deliberately doesn't build for
// card spend too.
private val CARD_CHARGE_FUNDING_ACCOUNT_TYPES = setOf(AccountType.MAIN, AccountType.PAY)

/**
 * Real Toss Bank check-card CHARGE flow -- extracted out of CardService.kt (2026-09-13)
 * once that file crossed the 500-line file-size-lint guideline for the first time;
 * charging money is a genuinely distinct, self-contained concern from card
 * issuance/freeze/PIN-management (still CardService), matching the rationale
 * CardChargeServiceTest.kt's own doc comment already established for the TEST-side
 * split on 2026-09-12 -- this extends the same split to the real implementation.
 * `getCardOrThrow`/`sendCardPushAfterCommit` are deliberately duplicated from
 * CardService rather than shared, matching this codebase's own established
 * small-duplicate-helper-across-split-files convention (e.g. iOS's
 * TalkScreen.errorMessage/ShopBestSellerBadge) -- both are pure infrastructure with
 * no business logic to drift.
 */
@Service
class CardChargeService(
    private val debitCardRepository: DebitCardRepository,
    private val debitCardTransactionRepository: DebitCardTransactionRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    @Transactional
    fun chargeWithCard(userId: String, amount: BigDecimal, merchantName: String, fundingAccountType: AccountType = AccountType.MAIN): CardChargeResult {
        rateLimiter.checkLimit("card:charge:$userId", limit = 30, window = Duration.ofMinutes(1))
        if (amount <= BigDecimal.ZERO) throw CardInvalidAmountException("Amount must be greater than zero")
        val trimmedMerchant = merchantName.trim()
        if (trimmedMerchant.isEmpty() || trimmedMerchant.length > 200) {
            throw CardInvalidAmountException("Merchant name is required and must be 200 characters or fewer")
        }
        if (fundingAccountType !in CARD_CHARGE_FUNDING_ACCOUNT_TYPES) {
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
        val startOfToday = LocalDate.now(CARD_CHARGE_RWANDA_ZONE).atStartOfDay(CARD_CHARGE_RWANDA_ZONE).toInstant()
        val startOfMonth = LocalDate.now(CARD_CHARGE_RWANDA_ZONE).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(CARD_CHARGE_RWANDA_ZONE).toInstant()

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
