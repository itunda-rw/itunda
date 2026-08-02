package rw.itunda.wallet

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletAutoTopUpSetting
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.provider.RailCatalog
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletAutoTopUpSettingRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AutoTopUpLinkedAccountNotFoundException(message: String) : RuntimeException(message)
class AutoTopUpLinkedAccountNotOwnedException(message: String) : RuntimeException(message)
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
 * A standing per-wallet rule (one real setting per wallet), not a payment-time hook --
 * `WalletService.confirmTransfer` and every other real money-moving method in this
 * codebase are completely untouched. The pull itself reuses the exact real
 * `ProviderConnector`/`RailCatalog` simulated-provider-call mechanism every other
 * rail-touching flow already uses (transfers, bills, airtime, `LinkedAccountService
 * .link`'s own verification call) -- never an unconditional success. On a genuine
 * provider decline, the wallet balance is correctly left untouched, matching
 * `ProviderConnector.attempt`'s own documented contract (declines must be checked
 * *before* posting to the ledger).
 *
 * Real double-entry: debits `rail_suspense` (the same real "money in flight to/from an
 * external rail" clearing account bills/airtime already use to represent money
 * LEAVING a wallet toward an external rail -- here it's the same account used
 * symmetrically for money ARRIVING from one, real double-entry practice, not a new
 * ledger account invented for this one feature) and credits the real wallet.
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
    private val walletAutoTopUpSettingRepository: WalletAutoTopUpSettingRepository,
    private val walletRepository: WalletRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val providerConnector: ProviderConnector,
) {
    // Real IDOR fix (2026-08-02): a walletId belonging to a DIFFERENT user used to
    // 403 ("that wallet does not belong to you") rather than 404 -- and unlike
    // WalletService.quoteTransfer's POST-body walletId, every AutoTopUpController
    // endpoint takes walletId as a real URL PATH VARIABLE
    // (/api/v1/wallet/{walletId}/auto-topup), the exact same directly-probeable shape
    // WalletService.getWalletById's own doc comment already documents fixing this
    // pattern for. Same fix, same reasoning, finally applied here too.
    private fun requireOwnedWallet(userId: String, walletId: String) =
        walletRepository.findById(walletId).orElseThrow { WalletNotFoundException("Wallet not found") }
            .also { if (it.userId != userId) throw WalletNotFoundException("Wallet not found") }

    @Transactional
    fun configure(
        userId: String,
        walletId: String,
        linkedAccountId: String,
        thresholdAmount: BigDecimal,
        topUpAmount: BigDecimal,
        dailyTriggerCap: Int,
        enabled: Boolean,
    ): WalletAutoTopUpSetting {
        requireOwnedWallet(userId, walletId)
        if (thresholdAmount < BigDecimal.ZERO) throw AutoTopUpInvalidAmountException("Threshold amount cannot be negative")
        if (topUpAmount <= BigDecimal.ZERO) throw AutoTopUpInvalidAmountException("Top-up amount must be greater than zero")
        if (dailyTriggerCap <= 0) throw AutoTopUpInvalidAmountException("Daily trigger cap must be at least 1")

        val linkedAccount = linkedAccountRepository.findById(linkedAccountId)
            .orElseThrow { AutoTopUpLinkedAccountNotFoundException("Linked account not found") }
        if (linkedAccount.userId != userId) {
            throw AutoTopUpLinkedAccountNotOwnedException("That linked account does not belong to you")
        }
        if (linkedAccount.status != LinkedAccountStatus.LINKED) {
            throw AutoTopUpLinkedAccountNotLinkedException("This linked account is not currently LINKED")
        }

        val existing = walletAutoTopUpSettingRepository.findByWalletId(walletId)
        val setting = existing ?: WalletAutoTopUpSetting(
            id = "auto_topup_${UUID.randomUUID()}", userId = userId, walletId = walletId,
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
        return walletAutoTopUpSettingRepository.save(setting)
    }

    fun getSetting(userId: String, walletId: String): WalletAutoTopUpSetting {
        requireOwnedWallet(userId, walletId)
        return walletAutoTopUpSettingRepository.findByWalletId(walletId)
            ?: throw AutoTopUpSettingNotFoundException("No auto top-up setting configured for this wallet")
    }

    fun getEnabledSettings(): List<WalletAutoTopUpSetting> = walletAutoTopUpSettingRepository.findByEnabledTrue()

    /**
     * Real threshold evaluation + pull. Returns a real, honest result rather than
     * throwing for every "conditions not met" case -- a demo/scheduled trigger call
     * checking a wallet that's simply above threshold right now is the NORMAL case,
     * not an error.
     */
    @Transactional
    fun evaluateAndTopUp(userId: String, walletId: String): AutoTopUpTriggerResult {
        val wallet = requireOwnedWallet(userId, walletId)
        val setting = walletAutoTopUpSettingRepository.findByWalletId(walletId)
            ?: throw AutoTopUpSettingNotFoundException("No auto top-up setting configured for this wallet")

        if (!setting.enabled) return AutoTopUpTriggerResult(false, "Auto top-up is disabled for this wallet")

        val today = LocalDate.now()
        if (setting.lastTriggerDate != today) {
            setting.triggersToday = 0
            setting.lastTriggerDate = today
        }
        if (setting.triggersToday >= setting.dailyTriggerCap) {
            return AutoTopUpTriggerResult(false, "Real daily trigger cap (${setting.dailyTriggerCap}) already reached today")
        }
        if (wallet.balance >= setting.thresholdAmount) {
            return AutoTopUpTriggerResult(false, "Wallet balance is already at or above the real threshold")
        }

        val linkedAccount = linkedAccountRepository.findById(setting.linkedAccountId)
            .orElseThrow { AutoTopUpLinkedAccountNotFoundException("Linked account not found") }
        if (linkedAccount.status != LinkedAccountStatus.LINKED) {
            return AutoTopUpTriggerResult(false, "The linked account is no longer LINKED")
        }

        val rail = RailCatalog.resolve(linkedAccount.provider)
        try {
            providerConnector.attempt(rail, "Auto top-up pull for wallet ${wallet.id}")
        } catch (e: ProviderDeclinedException) {
            // Real, honest non-trigger -- the wallet balance is correctly left
            // untouched, matching every other real rail-call caller's own discipline.
            return AutoTopUpTriggerResult(false, "Provider declined the pull: ${e.message}")
        }

        val ledger = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.DEBIT, setting.topUpAmount, "Auto top-up for wallet ${wallet.id}"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, setting.topUpAmount, "Auto top-up for wallet ${wallet.id}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = ledger.transactionId, referenceNumber = "AUTOTOPUP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = linkedAccount.id, recipientId = userId, toWalletId = wallet.id,
                amount = setting.topUpAmount, fee = BigDecimal.ZERO, currency = wallet.currency,
                type = TransactionType.DEPOSIT, status = TransactionStatus.COMPLETED,
                description = "Auto top-up from ${linkedAccount.provider}", channel = "auto_topup", completedAt = Instant.now(),
            ),
        )

        setting.triggersToday += 1
        setting.lastTriggeredAt = Instant.now()
        setting.updatedAt = Instant.now()
        walletAutoTopUpSettingRepository.save(setting)

        return AutoTopUpTriggerResult(true, "Topped up ${setting.topUpAmount} ${wallet.currency}")
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
     * documented as "a standing per-wallet rule... not a payment-time hook"). Where
     * `evaluateAndTopUp` runs on a real background cadence and pulls a fixed, user-
     * configured `topUpAmount` once the balance drifts below a threshold, this runs
     * synchronously at the exact moment a real payment would otherwise fail for
     * insufficient funds, and pulls only just enough to cover THIS specific shortfall
     * (rounded up to a real RWF increment), reusing the caller's existing enabled
     * `WalletAutoTopUpSetting`'s linked account -- not its configured `topUpAmount` or
     * `thresholdAmount`, which are that other, separate mechanism's own real settings.
     * Deliberately does not touch `triggersToday`/`dailyTriggerCap`/`lastTriggerDate` --
     * those track the recurring background sweep's own real daily cadence, a genuinely
     * different real-world quota than "how many times a payment needed rescuing today."
     * Called from `P2pService.sendDirect` in the SAME already-open transaction, before
     * that transfer's own ledger legs are posted (so before any row lock on the sender's
     * wallet is taken) -- deliberately not the post-commit-hook + REQUIRES_NEW pattern
     * `RoundUpService.processRoundUp` needed, since this runs BEFORE the triggering
     * transfer's own money movement, not as an auxiliary side effect after it.
     */
    @Transactional
    fun topUpShortfall(userId: String, walletId: String, shortfallAmount: BigDecimal): AutoTopUpTriggerResult {
        val wallet = requireOwnedWallet(userId, walletId)
        val setting = walletAutoTopUpSettingRepository.findByWalletId(walletId)
            ?: return AutoTopUpTriggerResult(false, "No auto top-up setting configured for this wallet")
        if (!setting.enabled) return AutoTopUpTriggerResult(false, "Auto top-up is disabled for this wallet")

        val linkedAccount = linkedAccountRepository.findById(setting.linkedAccountId).orElse(null)
            ?: return AutoTopUpTriggerResult(false, "Linked account not found")
        if (linkedAccount.status != LinkedAccountStatus.LINKED) {
            return AutoTopUpTriggerResult(false, "The linked account is no longer LINKED")
        }

        val roundedAmount = shortfallAmount.divide(SHORTFALL_ROUNDING_UNIT, 0, RoundingMode.UP).multiply(SHORTFALL_ROUNDING_UNIT)

        val rail = RailCatalog.resolve(linkedAccount.provider)
        try {
            providerConnector.attempt(rail, "Shortfall auto top-up pull for wallet ${wallet.id}")
        } catch (e: ProviderDeclinedException) {
            return AutoTopUpTriggerResult(false, "Provider declined the pull: ${e.message}")
        }

        val ledger = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.DEBIT, roundedAmount, "Shortfall auto top-up for wallet ${wallet.id}"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, roundedAmount, "Shortfall auto top-up for wallet ${wallet.id}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = ledger.transactionId, referenceNumber = "SHORTFALLTOPUP${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = linkedAccount.id, recipientId = userId, toWalletId = wallet.id,
                amount = roundedAmount, fee = BigDecimal.ZERO, currency = wallet.currency,
                type = TransactionType.DEPOSIT, status = TransactionStatus.COMPLETED,
                description = "Shortfall auto top-up from ${linkedAccount.provider}", channel = "auto_topup_shortfall", completedAt = Instant.now(),
            ),
        )

        return AutoTopUpTriggerResult(true, "Topped up $roundedAmount ${wallet.currency} to cover the real shortfall")
    }
}
