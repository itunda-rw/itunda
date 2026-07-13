package rw.itunda.core.ledger

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.events.LedgerPostedEvent
import rw.itunda.core.events.LedgerPostedLeg
import rw.itunda.core.events.TOPIC_LEDGER_POSTED
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class LedgerLeg(
    val accountId: String,
    val accountType: LedgerAccountType,
    val direction: LedgerDirection,
    val amount: BigDecimal,
    val memo: String,
)

data class LedgerPostResult(val transactionId: String, val entries: List<LedgerEntry>)

/**
 * Direct Kotlin port of backend/src/services/ledger.ts's postLedgerTransaction, with one
 * upgrade the Express/in-memory version explicitly could not have: this runs inside a
 * real database transaction with row-level locks (see Repositories.kt's findByIdForUpdate),
 * so two concurrent transfers touching the same wallet or clearing account serialize
 * instead of racing. Same invariant as the original: legs must balance (debits == credits)
 * and a wallet debit that would overdraw is rejected — both checked before anything is
 * written, so a rejected transaction never partially applies.
 */
@Service
class LedgerService(
    private val walletRepository: WalletRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val eventPublisher: EventPublisher,
) {
    @Transactional
    fun postLedgerTransaction(currency: String, rawLegs: List<LedgerLeg>): LedgerPostResult {
        val legs = rawLegs.filter { it.amount > BigDecimal.ZERO }
        if (legs.isEmpty()) {
            throw LedgerImbalanceException("Ledger transaction must contain at least one non-zero leg")
        }

        val debits = legs.filter { it.direction == LedgerDirection.DEBIT }.sumOf { it.amount }
        val credits = legs.filter { it.direction == LedgerDirection.CREDIT }.sumOf { it.amount }
        if (debits.setScale(2) != credits.setScale(2)) {
            throw LedgerImbalanceException("Ledger legs do not balance: debits=$debits credits=$credits")
        }

        // Lock every account touched, in a stable order (by id) to avoid deadlocking
        // against another transfer that touches the same two accounts in reverse order.
        val walletIds = legs.filter { it.accountType == LedgerAccountType.WALLET }.map { it.accountId }.distinct().sorted()
        val clearingIds = legs.filter { it.accountType != LedgerAccountType.WALLET }.map { it.accountId }.distinct().sorted()

        val lockedWallets = walletIds.associateWith {
            walletRepository.findByIdForUpdate(it).orElseThrow { IllegalStateException("Unknown wallet account $it") }
        }
        val lockedAccounts = clearingIds.associateWith {
            ledgerAccountRepository.findByIdForUpdate(it).orElseThrow { IllegalStateException("Unknown ledger account $it") }
        }

        for (leg in legs) {
            if (leg.accountType == LedgerAccountType.WALLET && leg.direction == LedgerDirection.DEBIT) {
                val wallet = lockedWallets.getValue(leg.accountId)
                // Real enforcement of Wallet.isActive (2026-07-13) -- this field existed
                // on the entity already but was never read anywhere in the codebase
                // (confirmed by a repo-wide grep), making it purely cosmetic. Enforced
                // here, the single choke point every money-moving flow already passes
                // through, so freezing a wallet (SupportService's real account-takeover
                // response) actually blocks every outgoing debit system-wide rather than
                // only whichever specific endpoint happened to check it. Incoming
                // credits are still allowed -- a frozen account can still receive a
                // refund or an incoming transfer while under review, matching how real
                // account-freeze responses work.
                if (!wallet.isActive) {
                    throw WalletFrozenException("Wallet ${leg.accountId} is frozen pending review")
                }
                if (wallet.availableBalance < leg.amount) {
                    throw InsufficientFundsException("Insufficient available balance in ${leg.accountId}")
                }
            }
        }

        val transactionId = "ledgertxn_${UUID.randomUUID()}"
        val createdAt = Instant.now()
        val entries = legs.map { leg ->
            val signedAmount = if (leg.direction == LedgerDirection.CREDIT) leg.amount else leg.amount.negate()
            val balanceAfter: BigDecimal = if (leg.accountType == LedgerAccountType.WALLET) {
                val wallet = lockedWallets.getValue(leg.accountId)
                wallet.balance = wallet.balance.add(signedAmount)
                wallet.availableBalance = wallet.balance
                wallet.balance
            } else {
                val account = lockedAccounts.getValue(leg.accountId)
                account.balance = account.balance.add(signedAmount)
                account.balance
            }
            LedgerEntry(
                id = "entry_${UUID.randomUUID()}",
                transactionId = transactionId,
                accountId = leg.accountId,
                accountType = leg.accountType,
                direction = leg.direction,
                amount = leg.amount,
                currency = currency,
                balanceAfter = balanceAfter,
                memo = leg.memo,
                createdAt = createdAt,
            )
        }

        ledgerEntryRepository.saveAll(entries)

        // Real event backbone (2026-07-11 fix) -- this is the one choke-point every
        // money-moving flow already goes through (transfers, bills, loans, savings,
        // stocks, insurance, merchant collection), so publishing here covers all of
        // them without touching each individual service. Published after-commit, not
        // here directly -- see EventPublisher's own doc comment for why.
        eventPublisher.publishAfterCommit(
            TOPIC_LEDGER_POSTED,
            transactionId,
            LedgerPostedEvent(
                transactionId = transactionId,
                currency = currency,
                postedAt = createdAt,
                legs = entries.map {
                    LedgerPostedLeg(
                        accountId = it.accountId,
                        accountType = it.accountType.name,
                        direction = it.direction.name,
                        amount = it.amount,
                        memo = it.memo,
                    )
                },
            ),
        )

        return LedgerPostResult(transactionId, entries)
    }
}
