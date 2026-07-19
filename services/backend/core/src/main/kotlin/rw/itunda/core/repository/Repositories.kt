package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Contact
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import java.time.Instant
import java.util.Optional

interface LoanAccountRepository : JpaRepository<LoanAccount, String> {
    fun findByUserId(userId: String): List<LoanAccount>
}

interface ContactRepository : JpaRepository<Contact, String> {
    fun findByUserId(userId: String): List<Contact>
}

interface HoldingRepository : JpaRepository<Holding, String> {
    fun findByUserId(userId: String): List<Holding>
    fun findByUserIdAndStockId(userId: String, stockId: String): Holding?
}

interface SavingsGoalRepository : JpaRepository<SavingsGoal, String> {
    fun findByUserId(userId: String): List<SavingsGoal>
    fun existsByUserId(userId: String): Boolean
}

interface InterestJarRepository : JpaRepository<InterestJar, String>

interface LedgerAccountRepository : JpaRepository<LedgerAccount, String> {
    // A real row lock (SELECT ... FOR UPDATE) — the concurrency control the Express/
    // JSON-file prototype explicitly could not provide (see TOSS_PARITY_MATRIX.md's
    // ledger "Non-Negotiable Gate": needs "a real durable store with real transactions/
    // concurrency control"). Two simultaneous postLedgerTransaction calls touching the
    // same clearing account now serialize instead of racing on a read-modify-write.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LedgerAccount a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<LedgerAccount>
}

interface UserRepository : JpaRepository<User, String> {
    fun findByPhoneNumber(phoneNumber: String): User?
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun findByReferralCode(referralCode: String): User?
    fun findAllByReferredByUserId(referredByUserId: String): List<User>

    // Batch form of findByPhoneNumber -- GroupMessagingService.createGroupByPhoneNumbers
    // uses this to resolve every invited member in one round trip instead of one
    // findByPhoneNumber call per invitee (a real N+1 found in a 2026-07-19 sweep, same
    // shape as PayrollService.runPayroll's own findByUserIdInAndType fix above).
    fun findAllByPhoneNumberIn(phoneNumbers: List<String>): List<User>
}

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, String> {
    fun findByToken(token: String): EmailVerificationToken?
}

interface WalletRepository : JpaRepository<Wallet, String> {
    fun findByUserId(userId: String): List<Wallet>
    fun findByUserIdAndType(userId: String, type: WalletType): Wallet?

    // Batch form of findByUserIdAndType -- PayrollService.runPayroll uses this to fetch
    // every employee's wallet in one round trip instead of one findByUserIdAndType call
    // per roster row (a real N+1 a large payroll roster would otherwise pay for on every
    // run).
    fun findByUserIdInAndType(userIds: List<String>, type: WalletType): List<Wallet>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<Wallet>
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, String> {
    fun findByTransactionId(transactionId: String): List<LedgerEntry>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: String): List<LedgerEntry>
}

interface TransactionRepository : JpaRepository<Transaction, String> {
    fun findBySenderIdOrRecipientIdOrderByCreatedAtDesc(senderId: String, recipientId: String): List<Transaction>
    fun existsBySenderIdAndTypeAndStatus(senderId: String, type: TransactionType, status: TransactionStatus): Boolean

    // Merchant reports (2026-07-16): a merchant collection's Transaction row has
    // recipientId = the merchant owner's userId (see MerchantService.collect), so this
    // is the real join key -- not a merchant id or wallet id.
    fun findByRecipientIdAndTypeAndCreatedAtBetween(
        recipientId: String,
        type: TransactionType,
        from: Instant,
        to: Instant,
    ): List<Transaction>
}
