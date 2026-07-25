package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.UUID

class BusinessAccountAlreadyExistsException(message: String) : RuntimeException(message)
class BusinessAccountNotFoundException(message: String) : RuntimeException(message)
class InvalidMoveAmountException(message: String) : RuntimeException(message)

/**
 * A real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent --
 * closes a real, structural gap named in `Merchant.kt`'s own doc comment: every
 * merchant's real card/QR collection settles straight into their PERSONAL MAIN wallet
 * ("reuses the owner's existing MAIN wallet as the settlement wallet rather than
 * introducing a new WalletType"), so business income and personal spending have always
 * been genuinely inseparable in this backend.
 *
 * Honestly scoped, not the full real Toss product: this ships the real, buildable core
 * (a dedicated `WalletType.BUSINESS` wallet + real money movement into/out of it + its
 * own real transaction history) without touching `MerchantService.collect`/`chargeCard`
 * at all -- those already-tested settlement flows keep paying into `Merchant.walletId`
 * exactly as before, favoring an additive new wallet over modifying already-tested
 * money-movement code, this codebase's own established discipline. A merchant
 * deliberately "sweeps" money they've earmarked as business income into this wallet
 * (`moveToBusiness`) or draws from it (`moveToPersonal`) -- both real, same-user,
 * fee-free wallet-to-wallet ledger transfers, same shape `SavingsService.depositToGoal`
 * already established for "move your own money into a dedicated bucket," just against a
 * real second `Wallet` row instead of a shared clearing account, since a business wallet
 * needs its own real, visible balance and transaction history.
 *
 * Deliberately NOT shipped this pass, named follow-ups: a real business debit card
 * (itunda has no physical card issuance anywhere in this backend to extend), automatic
 * expense auto-categorization for tax filing (`WalletService.getSpendingInsight`'s
 * existing categorization logic is the real reusable foundation, once there's a concrete
 * tax-authority integration to feed), and 사장님 대출 (Boss Loans) as a business-specific
 * lending product (itunda's existing generic Loan product is the real foundation, but a
 * business-specific underwriting/eligibility model is a genuinely separate build).
 */
@Service
class MerchantBusinessAccountService(
    private val merchantRepository: MerchantRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {
    companion object {
        private const val TRANSACTION_HISTORY_LIMIT = 50
    }

    private fun requireMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    private fun generateAccountNumber(): String = (2026500000L + (Math.random() * 900000).toLong()).toString()

    @Transactional
    fun openBusinessAccount(ownerUserId: String): Wallet {
        requireMerchant(ownerUserId)
        if (walletRepository.findByUserIdAndType(ownerUserId, WalletType.BUSINESS) != null) {
            throw BusinessAccountAlreadyExistsException("You already have a business account")
        }
        return walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}", userId = ownerUserId, accountNumber = generateAccountNumber(),
                accountName = "Business Account", type = WalletType.BUSINESS,
                balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
            ),
        )
    }

    fun getBusinessAccount(ownerUserId: String): Wallet =
        walletRepository.findByUserIdAndType(ownerUserId, WalletType.BUSINESS)
            ?: throw BusinessAccountNotFoundException("No business account found -- open one first")

    fun getBusinessTransactions(ownerUserId: String): List<LedgerEntry> {
        val businessWallet = getBusinessAccount(ownerUserId)
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(businessWallet.id).take(TRANSACTION_HISTORY_LIMIT)
    }

    @Transactional
    fun moveToBusiness(ownerUserId: String, amount: BigDecimal): Wallet {
        if (amount <= BigDecimal.ZERO) throw InvalidMoveAmountException("Amount must be greater than zero")
        val businessWallet = getBusinessAccount(ownerUserId)
        val personalWallet = walletRepository.findByUserIdAndType(ownerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            personalWallet.currency,
            listOf(
                LedgerLeg(personalWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Move to business account"),
                LedgerLeg(businessWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Move from personal account"),
            ),
        )
        return walletRepository.findById(businessWallet.id).orElseThrow()
    }

    @Transactional
    fun moveToPersonal(ownerUserId: String, amount: BigDecimal): Wallet {
        if (amount <= BigDecimal.ZERO) throw InvalidMoveAmountException("Amount must be greater than zero")
        val businessWallet = getBusinessAccount(ownerUserId)
        val personalWallet = walletRepository.findByUserIdAndType(ownerUserId, WalletType.MAIN)
            ?: throw MerchantNoWalletException("No wallet found for this account")

        ledgerService.postLedgerTransaction(
            businessWallet.currency,
            listOf(
                LedgerLeg(businessWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Move to personal account"),
                LedgerLeg(personalWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Move from business account"),
            ),
        )
        return walletRepository.findById(personalWallet.id).orElseThrow()
    }
}
