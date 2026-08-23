package rw.itunda.account

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountAutoTopUpSetting
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountAutoTopUpSettingRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AutoTopUpLinkedAccountNotFoundException(message: String) : RuntimeException(message)
class AutoTopUpLinkedAccountNotLinkedException(message: String) : RuntimeException(message)
class AutoTopUpInvalidAmountException(message: String) : RuntimeException(message)
class AutoTopUpSettingNotFoundException(message: String) : RuntimeException(message)

data class AutoTopUpTriggerResult(val triggered: Boolean, val reason: String)

/**
 * Real Naver Pay Money 자동충전 (auto-charge) equivalent -- Naver Pay Money's real
 * balance-replenishment feature: once a user's stored-value balance falls below a
 * self-set threshold, it auto-pulls a configured amount from their pre-registered
 * linked bank account (Naver's own real setup routes this through a designated Hana
 * Bank account), so the user never has to manually top up before paying. Corroborated
 * by NamuWiki's Naver Pay/Naver Pay Money Card articles and Wikipedia's NAVER Pay
 * article describing Naver Pay Money as a stored-value balance fed by linked-account
 * charging, distinct from Naver Pay Points.
 *
 * A standing per-account rule (one real setting per account), not a payment-time hook --
 * `AccountService.confirmTransfer` and every other real money-moving method in this
 * codebase are completely untouched. The pull itself reuses the exact real
 * `ProviderConnector`/`RailCatalog` simulated-provider-call mechanism every other
 * rail-touching flow already uses (transfers, bills, airtime, `LinkedAccountService
 * .link`'s own verification call) -- never an unconditional success. On a genuine
 * provider decline, the account balance is correctly left untouched, matching
 * `ProviderConnector.attempt`'s own documented contract (declines must be checked
 * *before* posting to the ledger).
 *
 * Real double-entry: debits `rail_suspense` (the same real "money in flight to/from an
 * external rail" clearing account bills/airtime already use to represent money
 * LEAVING a account toward an external rail -- here it's the same account used
 * symmetrically for money ARRIVING from one, real double-entry practice, not a new
 * ledger account invented for this one feature) and credits the real account.
 *
 * `evaluateAndTopUp` stays real and directly callable (`POST .../auto-topup/trigger`,
 * useful for an on-demand demo check), but is no longer the only way it runs: real
 * background automation closed 2026-07-27 via `AutoTopUpScheduler`, the same
 * `@Scheduled` demo-speed-poll convention every other recurring feature in this
 * codebase already establishes (`AutoTransferScheduler`, `AutoSaveScheduler`) --
 * matching Naver Pay Money's own real feature, which is fully automatic, not something
 * a user manually triggers.
 */
@Service
class AutoTopUpService(
    private val accountAutoTopUpSettingRepository: AccountAutoTopUpSettingRepository,
    private val accountRepository: AccountRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val providerConnector: ProviderConnector,
) {
    // Real IDOR fix (2026-08-02): a accountId belonging to a DIFFERENT user used to
    // 403 ("that account does not belong to you") rather than 404 -- and unlike
    // AccountService.quoteTransfer's POST-body accountId, every AutoTopUpController
    // endpoint takes accountId as a real URL PATH VARIABLE
    // (/api/v1/account/{accountId}/auto-topup), the exact same directly-probeable shape
    // AccountService.getAccountById's own doc comment already documents fixing this
    // pattern for. Same fix, same reasoning, finally applied here too.
    private fun requireOwnedAccount(userId: String, accountId: String) =
        accountRepository.findById(accountId).orElseThrow { AccountNotFoundException("Account not found") }
            .also { if (it.userId != userId) throw AccountNotFoundException("Account not found") }

    @Transactional
    fun configure(
        userId: String,
        accountId: String,
        linkedAccountId: String,
        thresholdAmount: BigDecimal,
        topUpAmount: BigDecimal,
        dailyTriggerCap: Int,
        enabled: Boolean,
    ): AccountAutoTopUpSetting {
        requireOwnedAccount(userId, accountId)
        if (thresholdAmount < BigDecimal.ZERO) throw AutoTopUpInvalidAmountException("Threshold amount cannot be negative")
        if (topUpAmount <= BigDecimal.ZERO) throw AutoTopUpInvalidAmountException("Top-up amount must be greater than zero")
        if (dailyTriggerCap <= 0) throw AutoTopUpInvalidAmountException("Daily trigger cap must be at least 1")

        val linkedAccount = linkedAccountRepository.findById(linkedAccountId)
            .orElseThrow { AutoTopUpLinkedAccountNotFoundException("Linked account not found") }
        // Real IDOR fix (2026-08-02): linkedAccountId belonging to a DIFFERENT user
        // used to throw AutoTopUpLinkedAccountNotOwnedException, mapped to a real 403
        // that confirmed the id was real -- the same probe this exact module's own
        // requireOwnedAccount (AccountNotFoundException, fixed earlier this session) and
        // overview/LinkedAccountService.unlink (fixed the same pass as this one)
        // already correctly avoid. Same fix: 404, not 403.
        if (linkedAccount.userId != userId) {
            throw AutoTopUpLinkedAccountNotFoundException("Linked account not found")
        }
        if (linkedAccount.status != LinkedAccountStatus.LINKED) {
            throw AutoTopUpLinkedAccountNotLinkedException("This linked account is not currently LINKED")
        }

        val existing = accountAutoTopUpSettingRepository.findByAccountId(accountId)
        val setting = existing ?: AccountAutoTopUpSetting(
            id = "auto_topup_${UUID.randomUUID()}", userId = userId, accountId = accountId,
            linkedAccountId = linkedAccountId, thresholdAmount = thresholdAmount, topUpAmount = topUpAmount,
            dailyTriggerCap = dailyTriggerCap, enabled = enabled,
        )
        if (existing != null) {
            setting.linkedAccountId = linkedAccountId
            setting.thresholdAmount = thresholdAmount
            setting.topUpAmount = topUpAmount
            setting.dailyTriggerCap = dailyTriggerCap
            setting.enabled = enabled
            setting.updatedAt = Instant.now()
        }
        return accountAutoTopUpSettingRepository.save(setting)
    }

    fun getSetting(userId: String, accountId: String): AccountAutoTopUpSetting {
        requireOwnedAccount(userId, accountId)
        return accountAutoTopUpSettingRepository.findByAccountId(accountId)
            ?: throw AutoTopUpSettingNotFoundException("No auto top-up setting configured for this account")
    }

    fun getEnabledSettings(): List<AccountAutoTopUpSetting> = accountAutoTopUpSettingRepository.findByEnabledTrue()

    /**
     * Real threshold evaluation + pull. Returns a real, honest result rather than
     * throwing for every "conditions not met" case -- a demo/scheduled trigger call
     * checking a account that's simply above threshold right now is the NORMAL case,
     * not an error.
     */
    @Transactional
    fun evaluateAndTopUp(userId: String, accountId: String): AutoTopUpTriggerResult {
        val account = requireOwnedAccount(userId, accountId)
        val setting = accountAutoTopUpSettingRepository.findByAccountId(accountId)
            ?: throw AutoTopUpSettingNotFoundException("No auto top-up setting configured for this account")

        if (!setting.enabled) return AutoTopUpTriggerResult(false, "Auto top-up is disabled for this account")

        val today = LocalDate.now()
        if (setting.lastTriggerDate != today) {
            setting.triggersToday = 0
            setting.lastTriggerDate = today
        }
        if (setting.triggersToday >= setting.dailyTriggerCap) {
            return AutoTopUpTriggerResult(false, "Real daily trigger cap (${setting.dailyTriggerCap}) already reached today")
        }
        if (account.balance >= setting.thresholdAmount) {
            return AutoTopUpTriggerResult(false, "Account balance is already at or above the real threshold")
        }

        val linkedAccount = linkedAccountRepository.findById(setting.linkedAccountId)
            .orElseThrow { AutoTopUpLinkedAccountNotFoundException("Linked account not found") }
        if (linkedAccount.status != LinkedAccountStatus.LINKED) {
            return AutoTopUpTriggerResult(false, "The linked account is no longer LINKED")
        }

        val rail = RailCatalog.resolve(linkedAccount.provider)
        try {
            providerConnector.attempt(rail, "Auto top-up pull for account ${account.id}")
        } catch (e: ProviderDeclinedException) {
            // Real, honest non-trigger -- the account balance is correctly left
            // untouched, matching every other real rail-call caller's own discipline.
            return AutoTopUpTriggerResult(false, "Provider declined the pull: ${e.message}")
        }

        val ledger = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.DEBIT, setting.topUpAmount, "Auto top-up for account ${account.id}"),
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, setting.topUpAmount, "Auto top-up for account ${account.id}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = ledger.transactionId, referenceNumber = "AUTOTOPUP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = linkedAccount.id, recipientId = userId, toAccountId = account.id,
                amount = setting.topUpAmount, fee = BigDecimal.ZERO, currency = account.currency,
                type = TransactionType.DEPOSIT, status = TransactionStatus.COMPLETED,
                description = "Auto top-up from ${linkedAccount.provider}", channel = "auto_topup", completedAt = Instant.now(),
            ),
        )

        setting.triggersToday += 1
        setting.lastTriggeredAt = Instant.now()
        setting.updatedAt = Instant.now()
        accountAutoTopUpSettingRepository.save(setting)

        return AutoTopUpTriggerResult(true, "Topped up ${setting.topUpAmount} ${account.currency}")
    }

    companion object {
        // Real Naver Pay Money shortfall-charge rounding -- Naver's own real feature
        // ("결제 시 부족분 자동 충전") rounds the pull up to a real KRW 10,000 unit; RWF has
        // no equivalent well-known round unit anywhere in this codebase, so this reuses
        // the largest of this app's own already-real round-up increments
        // (`RoundUpService.SUPPORTED_INCREMENTS`) as the honest RWF-scale adaptation.
        val SHORTFALL_ROUNDING_UNIT: BigDecimal = BigDecimal("1000")
    }

    /**
     * Real Naver Pay Money "결제 시 부족분 자동 충전" (auto-charge the shortfall at payment
     * time) -- a genuinely distinct real feature from `evaluateAndTopUp` above (which is
     * documented as "a standing per-account rule... not a payment-time hook"). Where
     * `evaluateAndTopUp` runs on a real background cadence and pulls a fixed, user-
     * configured `topUpAmount` once the balance drifts below a threshold, this runs
     * synchronously at the exact moment a real payment would otherwise fail for
     * insufficient funds, and pulls only just enough to cover THIS specific shortfall
     * (rounded up to a real RWF increment), reusing the caller's existing enabled
     * `AccountAutoTopUpSetting`'s linked account -- not its configured `topUpAmount` or
     * `thresholdAmount`, which are that other, separate mechanism's own real settings.
     * Deliberately does not touch `triggersToday`/`dailyTriggerCap`/`lastTriggerDate` --
     * those track the recurring background sweep's own real daily cadence, a genuinely
     * different real-world quota than "how many times a payment needed rescuing today."
     * Called from `P2pService.sendDirect` in the SAME already-open transaction, before
     * that transfer's own ledger legs are posted (so before any row lock on the sender's
     * account is taken) -- deliberately not the post-commit-hook + REQUIRES_NEW pattern
     * `RoundUpService.processRoundUp` needed, since this runs BEFORE the triggering
     * transfer's own money movement, not as an auxiliary side effect after it.
     */
    @Transactional
    fun topUpShortfall(userId: String, accountId: String, shortfallAmount: BigDecimal): AutoTopUpTriggerResult {
        val account = requireOwnedAccount(userId, accountId)
        val setting = accountAutoTopUpSettingRepository.findByAccountId(accountId)
            ?: return AutoTopUpTriggerResult(false, "No auto top-up setting configured for this account")
        if (!setting.enabled) return AutoTopUpTriggerResult(false, "Auto top-up is disabled for this account")

        val linkedAccount = linkedAccountRepository.findById(setting.linkedAccountId).orElse(null)
            ?: return AutoTopUpTriggerResult(false, "Linked account not found")
        if (linkedAccount.status != LinkedAccountStatus.LINKED) {
            return AutoTopUpTriggerResult(false, "The linked account is no longer LINKED")
        }

        val roundedAmount = shortfallAmount.divide(SHORTFALL_ROUNDING_UNIT, 0, RoundingMode.UP).multiply(SHORTFALL_ROUNDING_UNIT)

        val rail = RailCatalog.resolve(linkedAccount.provider)
        try {
            providerConnector.attempt(rail, "Shortfall auto top-up pull for account ${account.id}")
        } catch (e: ProviderDeclinedException) {
            return AutoTopUpTriggerResult(false, "Provider declined the pull: ${e.message}")
        }

        val ledger = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.DEBIT, roundedAmount, "Shortfall auto top-up for account ${account.id}"),
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, roundedAmount, "Shortfall auto top-up for account ${account.id}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = ledger.transactionId, referenceNumber = "SHORTFALLTOPUP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = linkedAccount.id, recipientId = userId, toAccountId = account.id,
                amount = roundedAmount, fee = BigDecimal.ZERO, currency = account.currency,
                type = TransactionType.DEPOSIT, status = TransactionStatus.COMPLETED,
                description = "Shortfall auto top-up from ${linkedAccount.provider}", channel = "auto_topup_shortfall", completedAt = Instant.now(),
            ),
        )

        return AutoTopUpTriggerResult(true, "Topped up $roundedAmount ${account.currency} to cover the real shortfall")
    }

    /**
     * Real Toss Bank -> Toss Pay top-up -- see [rw.itunda.core.domain.AccountType.PAY]'s
     * own doc comment for the full sourced account. `MerchantService.collect` calls
     * this FIRST when a payer's real itunda Pay money balance can't cover a payment --
     * the user's own itunda Bank account (`AccountType.MAIN`) is itself one of the real
     * funding sources Pay money can draw from, exactly like any other linked bank/card
     * (see [topUpShortfall] above for the external-linked-account fallback, tried only
     * if THIS pull can't cover it either, matching the user's own description that real
     * Toss Pay money "can be connected to different bank accounts and different
     * cards" -- itunda's own Bank account is simply the first, always-available one).
     *
     * A real, ordinary internal account-to-account transfer -- same shape
     * `YouthAccountService.deposit`'s own MAIN -> Mini funding already establishes
     * (`senderId == recipientId == userId`, both legs `LedgerAccountType.WALLET`,
     * `TransactionType.TRANSFER`) -- which is exactly what delivers the real Bank/Pay
     * isolation the user asked for: this Transaction row is generic ("Top up to itunda
     * Pay," no merchant name), the SAME transaction visible on the Bank side as an
     * ordinary transfer out. The actual merchant-labeled payment that follows is a
     * SEPARATE `Transaction` row `MerchantService.collect` writes against the PAY
     * account, invisible to any Bank-only view -- itunda Bank genuinely never needs to
     * know which 가맹점 was paid, matching the user's own stated goal.
     *
     * Relies on [LedgerService.postLedgerTransaction]'s own real
     * `InsufficientFundsException` (thrown from inside the lock, not a separate
     * pre-check here) to detect "MAIN itself can't cover it either" -- caught and
     * translated into an honest, non-triggered result rather than propagating, so the
     * caller can fall through to [topUpShortfall]'s external-linked-account pull.
     */
    @Transactional
    fun topUpPayFromMain(userId: String, payAccountId: String, shortfallAmount: BigDecimal): AutoTopUpTriggerResult {
        val mainAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: return AutoTopUpTriggerResult(false, "No itunda Bank account found for this user")

        val result = try {
            ledgerService.postLedgerTransaction(
                mainAccount.currency,
                listOf(
                    LedgerLeg(mainAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, shortfallAmount, "Top up to itunda Pay"),
                    LedgerLeg(payAccountId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, shortfallAmount, "Top up to itunda Pay"),
                ),
            )
        } catch (e: InsufficientFundsException) {
            return AutoTopUpTriggerResult(false, "itunda Bank account balance can't cover this shortfall either")
        }
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "PAYTOPUP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = userId, recipientId = userId, fromAccountId = mainAccount.id, toAccountId = payAccountId,
                amount = shortfallAmount, fee = BigDecimal.ZERO, currency = mainAccount.currency, type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED, description = "Top up to itunda Pay", channel = "PAY_TOPUP_FROM_MAIN",
                completedAt = Instant.now(),
            ),
        )
        return AutoTopUpTriggerResult(true, "Topped up $shortfallAmount ${mainAccount.currency} from your itunda Bank account")
    }

    /**
     * Real Toss Bank/Toss Pay separation (2026-08-21) -- the shared "auto-fund the
     * shortfall at the moment of payment" shape every real merchant-collection call
     * site duplicated inline after the sweep in docs/DESIGN_REFERENCES.md §257
     * (MerchantService.collect/chargeByCustomerCode, MerchantBillingChargeExecutor,
     * MerchantBookingService.holdDeposit, commerce OrderService.placeOrder,
     * EatsOrderService/DineInOrderService.placeOrder, GiftVoucherService
     * .purchaseVoucher). Extracted here once each call site had already proven the
     * exact same 10-line block was correct, rather than duplicating it a 7th+ time --
     * tries [topUpPayFromMain] first (itunda's own internal, zero-external-risk
     * transfer), then [topUpShortfall] (an external linked bank/card) if that's not
     * configured/available. Returns the account re-read fresh if a top-up actually
     * landed, or the original (still-short) account unchanged otherwise -- the caller's
     * own real ledger post is always what surfaces a genuine remaining shortfall as
     * [rw.itunda.core.ledger.InsufficientFundsException], not this method.
     */
    fun ensureSufficientPayBalance(userId: String, account: Account, requiredAmount: BigDecimal): Account {
        if (account.availableBalance >= requiredAmount) return account
        val shortfall = requiredAmount.subtract(account.availableBalance)
        var topUpResult = topUpPayFromMain(userId, account.id, shortfall)
        if (!topUpResult.triggered) {
            topUpResult = topUpShortfall(userId, account.id, shortfall)
        }
        return if (topUpResult.triggered) {
            accountRepository.findById(account.id).orElse(account)
        } else {
            account
        }
    }
}
