package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Contact
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentCashIn
import rw.itunda.core.domain.AgentCashOut
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentTillReconciliation
import rw.itunda.core.domain.TillReconciliationStatus
import rw.itunda.core.domain.EmailVerificationToken
import rw.itunda.core.domain.GroupAccount
import rw.itunda.core.domain.GroupAccountContribution
import rw.itunda.core.domain.GroupAccountDuesReminder
import rw.itunda.core.domain.GroupAccountMember
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.PhoneVerificationToken
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.StockTrade
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.WeeklySavingsInstallment
import rw.itunda.core.domain.WeeklySavingsPlan
import java.time.Instant
import java.time.LocalDate
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

// Real immutable trade log (2026-07-20) -- see StockTrade.kt's own doc comment. Ordered
// ascending so StocksService.getPortfolioHistory can replay it chronologically.
interface StockTradeRepository : JpaRepository<StockTrade, String> {
    fun findByUserIdOrderByExecutedAtAsc(userId: String): List<StockTrade>
}

interface SavingsGoalRepository : JpaRepository<SavingsGoal, String> {
    fun findByUserId(userId: String): List<SavingsGoal>
    fun existsByUserId(userId: String): Boolean
}

interface InterestJarRepository : JpaRepository<InterestJar, String>

interface GroupAccountRepository : JpaRepository<GroupAccount, String> {
    fun findAllByIdIn(ids: List<String>): List<GroupAccount>
    fun findByMonthlyDuesAmountIsNotNull(): List<GroupAccount>
}

interface GroupAccountMemberRepository : JpaRepository<GroupAccountMember, String> {
    fun findByGroupAccountId(groupAccountId: String): List<GroupAccountMember>
    fun findByUserId(userId: String): List<GroupAccountMember>
    fun findByGroupAccountIdAndUserId(groupAccountId: String, userId: String): GroupAccountMember?
    fun countByGroupAccountId(groupAccountId: String): Long
}

interface GroupAccountContributionRepository : JpaRepository<GroupAccountContribution, String> {
    fun findByGroupAccountIdAndCycleMonth(groupAccountId: String, cycleMonth: String): List<GroupAccountContribution>
}

interface GroupAccountDuesReminderRepository : JpaRepository<GroupAccountDuesReminder, String> {
    fun existsByGroupAccountIdAndUserIdAndCycleMonth(groupAccountId: String, userId: String, cycleMonth: String): Boolean
}

interface WeeklySavingsPlanRepository : JpaRepository<WeeklySavingsPlan, String> {
    fun findByUserId(userId: String): List<WeeklySavingsPlan>
}

interface WeeklySavingsInstallmentRepository : JpaRepository<WeeklySavingsInstallment, String> {
    fun findByPlanIdOrderByWeekNumberAsc(planId: String): List<WeeklySavingsInstallment>
}

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

interface PhoneVerificationTokenRepository : JpaRepository<PhoneVerificationToken, String> {
    fun findByToken(token: String): PhoneVerificationToken?
}

interface WalletRepository : JpaRepository<Wallet, String> {
    fun findByUserId(userId: String): List<Wallet>
    fun findByUserIdAndType(userId: String, type: WalletType): Wallet?

    // Real foreign-currency accounts (2026-07-25) -- a user can hold several
    // FOREIGN_CURRENCY wallets at once (one per currency), unlike MAIN/SAVINGS'
    // single-row-per-type assumption findByUserIdAndType relies on, same reasoning
    // GROUP/WEEKLY_SAVINGS already established. See ForeignCurrencyWalletService's own
    // doc comment.
    fun findByUserIdAndTypeOrderByCreatedAtDesc(userId: String, type: WalletType): List<Wallet>
    fun findByUserIdAndTypeAndCurrency(userId: String, type: WalletType, currency: String): Wallet?

    // Batch form of findByUserIdAndType -- PayrollService.runPayroll uses this to fetch
    // every employee's wallet in one round trip instead of one findByUserIdAndType call
    // per roster row (a real N+1 a large payroll roster would otherwise pay for on every
    // run).
    fun findByUserIdInAndType(userIds: List<String>, type: WalletType): List<Wallet>

    // Real direct P2P push-transfer recipient resolution (2026-07-20) -- see
    // P2pService.sendDirect's own doc comment. accountNumber is globally unique (see
    // AuthService.generateAccountNumber), not scoped per wallet type.
    fun findByAccountNumber(accountNumber: String): Wallet?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<Wallet>
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, String> {
    fun findByTransactionId(transactionId: String): List<LedgerEntry>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: String): List<LedgerEntry>

    // Batch form of findByTransactionId -- WalletService.getSpendingInsight uses this to
    // fetch every debit's sibling legs in one round trip instead of one findByTransactionId
    // call per debit (a real N+1 found in a 2026-07-19 performance sweep, same shape as
    // PayrollService.runPayroll's/GroupMessagingService's own already-fixed N+1s).
    fun findByTransactionIdIn(transactionIds: List<String>): List<LedgerEntry>
}

interface AgentRepository : JpaRepository<Agent, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agent a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<Agent>
}

interface AgentCashInRepository : JpaRepository<AgentCashIn, String> {
    fun existsByReceiptNumber(receiptNumber: String): Boolean

    @Query("select coalesce(sum(c.amount), 0) from AgentCashIn c where c.agentId = :agentId and c.createdAt >= :from and c.createdAt < :to")
    fun sumAmountByAgentIdBetween(@Param("agentId") agentId: String, @Param("from") from: Instant, @Param("to") to: Instant): java.math.BigDecimal
    fun findByAgentIdOrderByCreatedAtDesc(agentId: String): List<AgentCashIn>
}

interface AgentCashOutRepository : JpaRepository<AgentCashOut, String> {
    fun existsByReceiptNumber(receiptNumber: String): Boolean

    @Query("select coalesce(sum(c.amount), 0) from AgentCashOut c where c.agentId = :agentId and c.createdAt >= :from and c.createdAt < :to")
    fun sumAmountByAgentIdBetween(@Param("agentId") agentId: String, @Param("from") from: Instant, @Param("to") to: Instant): java.math.BigDecimal
    fun findByAgentIdOrderByCreatedAtDesc(agentId: String): List<AgentCashOut>
}

interface AgentOperatorRepository : JpaRepository<AgentOperator, String> {
    fun findByUserId(userId: String): AgentOperator?
}

interface AgentTillReconciliationRepository : JpaRepository<AgentTillReconciliation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AgentTillReconciliation r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<AgentTillReconciliation>

    fun findByAgentIdAndBusinessDate(agentId: String, businessDate: LocalDate): AgentTillReconciliation?
    fun findByStatusOrderByCreatedAtDesc(status: TillReconciliationStatus): List<AgentTillReconciliation>
    fun findByBusinessDateBetweenOrderByCreatedAtDesc(from: LocalDate, to: LocalDate): List<AgentTillReconciliation>
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

    // Real 단골 (regular customer) detection for MerchantCouponService -- a merchant
    // collection's Transaction row has recipientId = the merchant owner's userId (same
    // real join key as the report query above), senderId = the paying customer.
    fun countBySenderIdAndRecipientIdAndTypeAndStatus(
        senderId: String,
        recipientId: String,
        type: TransactionType,
        status: TransactionStatus,
    ): Long

    // Real recurring-payment detection for SubscriptionDetectionService -- a coarse
    // fetch of everything real money this user has ever sent out via a recurring-
    // capable channel (TRANSFER/PAYMENT/BILL -- DEPOSIT/WITHDRAWAL/AIRTIME/LOAN aren't
    // subscription-like), ascending so consecutive-occurrence intervals compute forward
    // in time. Real pattern detection itself happens in application code, same
    // "coarse repo filter, exact logic in the service" discipline this codebase already
    // uses for nearby()/spending-insight categorization.
    fun findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(
        senderId: String,
        types: List<TransactionType>,
        status: TransactionStatus,
    ): List<Transaction>
}
