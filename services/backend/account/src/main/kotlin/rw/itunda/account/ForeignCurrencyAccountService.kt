package rw.itunda.account

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.CurrencyConversion
import rw.itunda.core.domain.ExchangeRateAlert
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fx.ForeignCurrencyRateClient
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CurrencyConversionRepository
import rw.itunda.core.repository.ExchangeRateAlertRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class UnsupportedCurrencyException(message: String) : RuntimeException(message)
class ForeignCurrencyAccountAlreadyExistsException(message: String) : RuntimeException(message)
class ForeignCurrencyAccountNotFoundException(message: String) : RuntimeException(message)
class InvalidConversionException(message: String) : RuntimeException(message)
class ExchangeRateUnavailableException(message: String) : RuntimeException(message)
class InvalidRateAlertException(message: String) : RuntimeException(message)
class ExchangeRateAlertNotFoundException(message: String) : RuntimeException(message)

/**
 * A real 토스뱅크 외화통장 (foreign-currency account) equivalent -- closes the gap named
 * in this session's Toss ecosystem research (docs/DESIGN_REFERENCES.md), scoped to real
 * value for a Rwandan diaspora remittance context rather than Toss's real 17-currency
 * breadth: USD/EUR/GBP, the three currencies real Rwandan diaspora remittance corridors
 * (US, Eurozone/Belgium, UK) actually run through.
 *
 * Honest scope: this is a real conversion between a user's OWN RWF and foreign-currency
 * accounts, both fully inside itunda, at a real live mid-market rate
 * ([ForeignCurrencyRateClient]) plus a real, transparent itunda margin -- it is NOT a
 * cross-border receiving/SWIFT rail (itunda has no real correspondent-banking
 * relationship to build one on, same genuinely-blocked-external-access category as
 * NIDA/PSP integrations elsewhere in this codebase). What's real here -- the rate, the
 * conversion, the double-entry money movement -- stays real; what would require an
 * external commercial relationship this repo has no path to isn't faked.
 *
 * Each conversion is real double-entry money movement, but implemented as TWO separate,
 * each-individually-balanced single-currency ledger transactions rather than one
 * cross-currency one: `LedgerService.postLedgerTransaction` enforces raw debits==credits
 * for one call with one shared `currency` label, so a single call spanning two
 * currencies has no way to balance. Both calls run inside this method's own
 * `@Transactional` boundary (Spring's default REQUIRED propagation joins the caller's
 * transaction), so a failure in the second call still rolls back the first -- this never
 * partially applies.
 */
@Service
class ForeignCurrencyAccountService(
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateClient: ForeignCurrencyRateClient,
    private val currencyConversionRepository: CurrencyConversionRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val exchangeRateAlertRepository: ExchangeRateAlertRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        val SUPPORTED_CURRENCIES = setOf("USD", "EUR", "GBP")

        // Same 1.5% fee-schedule reasoning OrderService.feeRate/EatsOrderService
        // .platformFeeRate already use, reused rather than inventing a different
        // number. A real forex spread (the customer's effective rate is this much
        // worse than the live mid-market rate), the same mechanic every real consumer
        // FX product uses -- not a flat fee.
        val MARGIN_RATE = BigDecimal("0.015")
    }

    private fun getMainAccount(userId: String) =
        accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw AccountNotFoundException("No main account found for this account")

    @Transactional
    fun openAccount(userId: String, currency: String): Account {
        val code = currency.trim().uppercase()
        if (code !in SUPPORTED_CURRENCIES) {
            throw UnsupportedCurrencyException("$code isn't a supported currency -- itunda currently supports ${SUPPORTED_CURRENCIES.sorted().joinToString()}")
        }

        // Real bug found live (2026-08-02): the plain `findByUserIdAndTypeAndCurrency`
        // check just below reads-then-CREATES a brand-new row -- there's no existing
        // FOREIGN_CURRENCY row to put an `@Version` guard on yet, and `accounts` has no
        // unique constraint on (user_id, type, currency) either, so two concurrent
        // openAccount("USD") calls for the same user could both pass that check before
        // either committed and both create a real USD account. Fixed the same way
        // YouthAccountService.openYouthAccount's own identical-shaped fix works: lock a
        // DIFFERENT already-existing row (the user's own real MAIN account) via
        // `findByIdForUpdate` to serialize the two concurrent creates.
        accountRepository.findByIdForUpdate(getMainAccount(userId).id)
        if (accountRepository.findByUserIdAndTypeAndCurrency(userId, AccountType.FOREIGN_CURRENCY, code) != null) {
            throw ForeignCurrencyAccountAlreadyExistsException("You already have a $code account")
        }
        return accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}", userId = userId, accountNumber = accountNumberGenerator.generate(2026400000L),
                accountName = "$code Account", type = AccountType.FOREIGN_CURRENCY,
                balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO, currency = code,
            ),
        )
    }

    fun getMyAccounts(userId: String): List<Account> =
        accountRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, AccountType.FOREIGN_CURRENCY)

    /** Real live mid-market rate, before itunda's own margin -- backs a client-side
     * quote preview before the user commits to `convert`. */
    fun getRate(from: String, to: String): Double =
        rateClient.getRate(from.trim().uppercase(), to.trim().uppercase())
            ?: throw ExchangeRateUnavailableException("Couldn't fetch a live exchange rate right now -- try again shortly")

    @Transactional
    fun convert(userId: String, fromCurrency: String, toCurrency: String, amount: BigDecimal): CurrencyConversion {
        val from = fromCurrency.trim().uppercase()
        val to = toCurrency.trim().uppercase()
        if (amount <= BigDecimal.ZERO) {
            throw InvalidConversionException("Amount must be greater than zero")
        }
        if (from == to) {
            throw InvalidConversionException("Cannot convert a currency to itself")
        }
        if (from != "RWF" && to != "RWF") {
            throw InvalidConversionException("Conversions must be between RWF and one foreign-currency account")
        }
        val foreignCode = if (from == "RWF") to else from
        if (foreignCode !in SUPPORTED_CURRENCIES) {
            throw UnsupportedCurrencyException("$foreignCode isn't a supported currency -- itunda currently supports ${SUPPORTED_CURRENCIES.sorted().joinToString()}")
        }

        val midRate = rateClient.getRate(from, to)
            ?: throw ExchangeRateUnavailableException("Couldn't fetch a live exchange rate right now -- try again shortly")

        val mainAccount = getMainAccount(userId)
        val foreignAccount = accountRepository.findByUserIdAndTypeAndCurrency(userId, AccountType.FOREIGN_CURRENCY, foreignCode)
            ?: throw ForeignCurrencyAccountNotFoundException("Open a $foreignCode account first")

        val grossConverted = amount.multiply(BigDecimal(midRate)).setScale(2, RoundingMode.HALF_UP)
        val marginAmount = grossConverted.multiply(MARGIN_RATE).setScale(2, RoundingMode.HALF_UP)
        val netConverted = grossConverted.subtract(marginAmount)
        if (netConverted <= BigDecimal.ZERO) {
            throw InvalidConversionException("Amount is too small to convert")
        }

        val sourceAccount = if (from == "RWF") mainAccount else foreignAccount
        val destAccount = if (to == "RWF") mainAccount else foreignAccount

        // Leg 1: source-currency transaction -- debit the user's own source account,
        // credit itunda's own source-currency clearing account. Single currency,
        // trivially balanced.
        ledgerService.postLedgerTransaction(
            from,
            listOf(
                LedgerLeg(sourceAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Currency conversion $from->$to"),
                LedgerLeg("fx_clearing_${from.lowercase()}", LedgerAccountType.FX_CLEARING, LedgerDirection.CREDIT, amount, "Currency conversion $from->$to"),
            ),
        )
        // Leg 2: destination-currency transaction -- debit itunda's own
        // destination-currency clearing account the full mid-market-converted amount,
        // credit the user's destination account the net amount after itunda's real
        // margin, and credit fee_revenue the margin itself. Single currency, balances:
        // gross == net + margin.
        val result = ledgerService.postLedgerTransaction(
            to,
            listOf(
                LedgerLeg("fx_clearing_${to.lowercase()}", LedgerAccountType.FX_CLEARING, LedgerDirection.DEBIT, grossConverted, "Currency conversion $from->$to"),
                LedgerLeg(destAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netConverted, "Currency conversion $from->$to"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, marginAmount, "FX margin $from->$to"),
            ),
        )

        return currencyConversionRepository.save(
            CurrencyConversion(
                id = "fx_conversion_${UUID.randomUUID()}", userId = userId, fromCurrency = from, toCurrency = to,
                fromAmount = amount, toAmount = netConverted, rate = BigDecimal(midRate).setScale(6, RoundingMode.HALF_UP),
                marginAmount = marginAmount, transactionId = result.transactionId,
            ),
        )
    }

    fun getMyConversions(userId: String, pageable: Pageable): Page<CurrencyConversion> =
        currencyConversionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)

    // Real Toss 외환 환율 알림 (exchange rate alert) (2026-08-17) -- see
    // ExchangeRateAlert's own doc comment. Setting a new target on an already-alerted
    // pair re-arms it (clears alertTriggeredAt), same "your new choice replaces the
    // old one" shape StocksService.setPriceAlert already establishes.
    @Transactional
    fun setRateAlert(userId: String, fromCurrency: String, toCurrency: String, targetRate: Double, direction: String): ExchangeRateAlert {
        val from = fromCurrency.trim().uppercase()
        val to = toCurrency.trim().uppercase()
        if (direction != "ABOVE" && direction != "BELOW") {
            throw InvalidRateAlertException("direction must be ABOVE or BELOW")
        }
        if (targetRate <= 0.0) {
            throw InvalidRateAlertException("Target rate must be greater than zero")
        }
        if (from == to) {
            throw InvalidConversionException("Cannot set an alert on a currency against itself")
        }
        val foreignCode = if (from == "RWF") to else if (to == "RWF") from else null
            ?: throw InvalidConversionException("Alerts must be between RWF and one foreign currency")
        if (foreignCode !in SUPPORTED_CURRENCIES) {
            throw UnsupportedCurrencyException("$foreignCode isn't a supported currency -- itunda currently supports ${SUPPORTED_CURRENCIES.sorted().joinToString()}")
        }

        val alert = exchangeRateAlertRepository.findByUserIdAndFromCurrencyAndToCurrency(userId, from, to)
            ?: ExchangeRateAlert(id = "fx_alert_${UUID.randomUUID()}", userId = userId, fromCurrency = from, toCurrency = to, targetRate = targetRate, direction = direction)
        alert.targetRate = targetRate
        alert.direction = direction
        alert.alertTriggeredAt = null
        return exchangeRateAlertRepository.save(alert)
    }

    @Transactional
    fun clearRateAlert(userId: String, fromCurrency: String, toCurrency: String) {
        val alert = exchangeRateAlertRepository.findByUserIdAndFromCurrencyAndToCurrency(userId, fromCurrency.trim().uppercase(), toCurrency.trim().uppercase())
            ?: throw ExchangeRateAlertNotFoundException("You don't have an alert set on this pair")
        exchangeRateAlertRepository.delete(alert)
    }

    fun getMyRateAlerts(userId: String): List<ExchangeRateAlert> =
        exchangeRateAlertRepository.findByUserIdOrderByCreatedAtDesc(userId)

    // Real due-alert query backing ExchangeRateAlertScheduler -- a real, not-yet-fired
    // alert whose real live rate (ForeignCurrencyRateClient) has actually crossed its
    // real target, in the real direction the user asked for. Unlike
    // StocksService.getDuePriceAlerts (a free in-memory simulated-price lookup),
    // rateClient.getRate is a real, cached, potentially-null external call -- a
    // currently-unreachable pair is honestly skipped, never treated as "not due."
    fun getDueRateAlerts(): List<ExchangeRateAlert> {
        val candidates = exchangeRateAlertRepository.findByAlertTriggeredAtIsNull()
        if (candidates.isEmpty()) return emptyList()
        return candidates.filter { alert ->
            val currentRate = rateClient.getRate(alert.fromCurrency, alert.toCurrency) ?: return@filter false
            when (alert.direction) {
                "ABOVE" -> currentRate >= alert.targetRate
                "BELOW" -> currentRate <= alert.targetRate
                else -> false
            }
        }
    }

    /** One real alert notification, called per-row by the scheduler -- same
     * re-check-right-before-firing resilience StocksService.triggerPriceAlert's own
     * doc comment already establishes, so a genuine race (or the rate moving back
     * between the batch snapshot and this call) can't double-fire or wrongly fire. */
    @Transactional
    fun triggerRateAlert(alertId: String) {
        val alert = exchangeRateAlertRepository.findById(alertId).orElse(null) ?: return
        if (alert.alertTriggeredAt != null) return
        val currentRate = rateClient.getRate(alert.fromCurrency, alert.toCurrency) ?: return
        val crossed = when (alert.direction) {
            "ABOVE" -> currentRate >= alert.targetRate
            "BELOW" -> currentRate <= alert.targetRate
            else -> false
        }
        if (!crossed) return

        val title = "${alert.fromCurrency}/${alert.toCurrency} hit your target rate"
        val body = "${alert.fromCurrency}/${alert.toCurrency} is now ${"%.6f".format(currentRate)} (target: ${alert.targetRate})"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = alert.userId, type = "EXCHANGE_RATE_ALERT",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"fromCurrency\":\"${alert.fromCurrency}\",\"toCurrency\":\"${alert.toCurrency}\"}",
            ),
        )
        pushNotificationService.sendToUser(alert.userId, title, body, mapOf("fromCurrency" to alert.fromCurrency, "toCurrency" to alert.toCurrency))
        alert.alertTriggeredAt = Instant.now()
        exchangeRateAlertRepository.save(alert)
    }
}
