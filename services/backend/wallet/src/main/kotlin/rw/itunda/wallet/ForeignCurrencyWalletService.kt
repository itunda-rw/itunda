package rw.itunda.wallet

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.CurrencyConversion
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fx.ForeignCurrencyRateClient
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.CurrencyConversionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class UnsupportedCurrencyException(message: String) : RuntimeException(message)
class ForeignCurrencyWalletAlreadyExistsException(message: String) : RuntimeException(message)
class ForeignCurrencyWalletNotFoundException(message: String) : RuntimeException(message)
class InvalidConversionException(message: String) : RuntimeException(message)
class ExchangeRateUnavailableException(message: String) : RuntimeException(message)

/**
 * A real 토스뱅크 외화통장 (foreign-currency account) equivalent -- closes the gap named
 * in this session's Toss ecosystem research (docs/DESIGN_REFERENCES.md), scoped to real
 * value for a Rwandan diaspora remittance context rather than Toss's real 17-currency
 * breadth: USD/EUR/GBP, the three currencies real Rwandan diaspora remittance corridors
 * (US, Eurozone/Belgium, UK) actually run through.
 *
 * Honest scope: this is a real conversion between a user's OWN RWF and foreign-currency
 * wallets, both fully inside itunda, at a real live mid-market rate
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
class ForeignCurrencyWalletService(
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateClient: ForeignCurrencyRateClient,
    private val currencyConversionRepository: CurrencyConversionRepository,
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

    private fun getMainWallet(userId: String) =
        walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw WalletNotFoundException("No main wallet found for this account")

    private fun generateAccountNumber(): String = (2026400000L + (Math.random() * 900000).toLong()).toString()

    @Transactional
    fun openWallet(userId: String, currency: String): Wallet {
        val code = currency.trim().uppercase()
        if (code !in SUPPORTED_CURRENCIES) {
            throw UnsupportedCurrencyException("$code isn't a supported currency -- itunda currently supports ${SUPPORTED_CURRENCIES.sorted().joinToString()}")
        }
        if (walletRepository.findByUserIdAndTypeAndCurrency(userId, WalletType.FOREIGN_CURRENCY, code) != null) {
            throw ForeignCurrencyWalletAlreadyExistsException("You already have a $code account")
        }
        return walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}", userId = userId, accountNumber = generateAccountNumber(),
                accountName = "$code Account", type = WalletType.FOREIGN_CURRENCY,
                balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO, currency = code,
            ),
        )
    }

    fun getMyWallets(userId: String): List<Wallet> =
        walletRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, WalletType.FOREIGN_CURRENCY)

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

        val mainWallet = getMainWallet(userId)
        val foreignWallet = walletRepository.findByUserIdAndTypeAndCurrency(userId, WalletType.FOREIGN_CURRENCY, foreignCode)
            ?: throw ForeignCurrencyWalletNotFoundException("Open a $foreignCode account first")

        val grossConverted = amount.multiply(BigDecimal(midRate)).setScale(2, RoundingMode.HALF_UP)
        val marginAmount = grossConverted.multiply(MARGIN_RATE).setScale(2, RoundingMode.HALF_UP)
        val netConverted = grossConverted.subtract(marginAmount)
        if (netConverted <= BigDecimal.ZERO) {
            throw InvalidConversionException("Amount is too small to convert")
        }

        val sourceWallet = if (from == "RWF") mainWallet else foreignWallet
        val destWallet = if (to == "RWF") mainWallet else foreignWallet

        // Leg 1: source-currency transaction -- debit the user's own source wallet,
        // credit itunda's own source-currency clearing account. Single currency,
        // trivially balanced.
        ledgerService.postLedgerTransaction(
            from,
            listOf(
                LedgerLeg(sourceWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Currency conversion $from->$to"),
                LedgerLeg("fx_clearing_${from.lowercase()}", LedgerAccountType.FX_CLEARING, LedgerDirection.CREDIT, amount, "Currency conversion $from->$to"),
            ),
        )
        // Leg 2: destination-currency transaction -- debit itunda's own
        // destination-currency clearing account the full mid-market-converted amount,
        // credit the user's destination wallet the net amount after itunda's real
        // margin, and credit fee_revenue the margin itself. Single currency, balances:
        // gross == net + margin.
        val result = ledgerService.postLedgerTransaction(
            to,
            listOf(
                LedgerLeg("fx_clearing_${to.lowercase()}", LedgerAccountType.FX_CLEARING, LedgerDirection.DEBIT, grossConverted, "Currency conversion $from->$to"),
                LedgerLeg(destWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netConverted, "Currency conversion $from->$to"),
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
}
