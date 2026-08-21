package rw.itunda.core.ledger

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
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
import rw.itunda.core.repository.AccountRepository
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
 * so two concurrent transfers touching the same account or clearing account serialize
 * instead of racing. Same invariant as the original: legs must balance (debits == credits)
 * and a account debit that would overdraw is rejected — both checked before anything is
 * written, so a rejected transaction never partially applies.
 */
@Service
class LedgerService(
    private val accountRepository: AccountRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val eventPublisher: EventPublisher,
    private val entityManager: EntityManager,
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
        val accountIds = legs.filter { it.accountType == LedgerAccountType.WALLET }.map { it.accountId }.distinct().sorted()
        val clearingIds = legs.filter { it.accountType != LedgerAccountType.WALLET }.map { it.accountId }.distinct().sorted()

        // Real lost-update bug found live (2026-08-09): a caller that reads a account
        // WITHOUT a lock earlier in the SAME transaction (e.g. P2pService.sendDirect's
        // own pre-check `accountRepository.findByUserIdAndType(...)` before ever calling
        // here) leaves that entity managed in the shared persistence context. Hibernate
        // genuinely acquires the real row lock below (confirmed live via
        // information_schema.innodb_trx showing real LOCK WAIT states between
        // concurrent transfers) -- but since the entity is ALREADY loaded by identity,
        // it returns the SAME cached instance with its OLD field values instead of
        // refreshing them from the just-locked row, a well-documented JPA/Hibernate
        // pitfall (a lock mode escalates the lock, it does not by itself refresh
        // already-managed state).
        //
        // A first attempt fixed this with a bare `entityManager.refresh(account)` --
        // still wrong, confirmed live via SQL trace: a plain (unlocked) refresh() issues
        // a plain SELECT with no `for update`, and under MySQL's default REPEATABLE READ
        // isolation, a PLAIN read anywhere in an already-open transaction is still bound
        // to that transaction's original consistent-read snapshot (taken at its very
        // first read -- here, the same early unlocked pre-check), no matter how late in
        // the transaction it runs. Only a LOCKING read bypasses the snapshot and reads
        // the true latest committed row -- which is exactly why the `for update` select
        // just above this already works correctly on its own; refresh() must carry the
        // same lock mode to get the same guarantee. Reproduced and confirmed the actual
        // fix live both times: concurrent transfers all acquired the row lock in
        // sequence (real LOCK WAIT entries), but without a LOCKING refresh every one of
        // them still computed its new balance from the same stale pre-transaction
        // snapshot value, so only one of several concurrent debits actually persisted --
        // a real, silent lost transfer with no error surfaced anywhere, on both the
        // first (wrong) fix attempt and the original bug.
        val lockedUserAccounts = accountIds.associateWith {
            accountRepository.findByIdForUpdate(it).orElseThrow { IllegalStateException("Unknown account $it") }
                .also { account -> entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE) }
        }
        val lockedClearingAccounts = clearingIds.associateWith {
            ledgerAccountRepository.findByIdForUpdate(it).orElseThrow { IllegalStateException("Unknown ledger account $it") }
                .also { account -> entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE) }
        }

        for (leg in legs) {
            if (leg.accountType == LedgerAccountType.WALLET && leg.direction == LedgerDirection.DEBIT) {
                val account = lockedUserAccounts.getValue(leg.accountId)
                // Real enforcement of Account.isActive (2026-07-13) -- this field existed
                // on the entity already but was never read anywhere in the codebase
                // (confirmed by a repo-wide grep), making it purely cosmetic. Enforced
                // here, the single choke point every money-moving flow already passes
                // through, so freezing a account (SupportService's real account-takeover
                // response) actually blocks every outgoing debit system-wide rather than
                // only whichever specific endpoint happened to check it. Incoming
                // credits are still allowed -- a frozen account can still receive a
                // refund or an incoming transfer while under review, matching how real
                // account-freeze responses work.
                if (!account.isActive) {
                    throw AccountFrozenException("Account ${leg.accountId} is frozen pending review")
                }
                if (account.availableBalance < leg.amount) {
                    throw InsufficientFundsException("Insufficient available balance in ${leg.accountId}")
                }
            }
        }

        val transactionId = "ledgertxn_${UUID.randomUUID()}"
        val createdAt = Instant.now()
        val entries = legs.map { leg ->
            val signedAmount = if (leg.direction == LedgerDirection.CREDIT) leg.amount else leg.amount.negate()
            val balanceAfter: BigDecimal = if (leg.accountType == LedgerAccountType.WALLET) {
                val account = lockedUserAccounts.getValue(leg.accountId)
                account.balance = account.balance.add(signedAmount)
                account.availableBalance = account.balance
                account.balance
            } else {
                val account = lockedClearingAccounts.getValue(leg.accountId)
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
