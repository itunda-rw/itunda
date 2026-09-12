package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Contact
import rw.itunda.core.domain.CustomerPaymentCode
import rw.itunda.core.domain.DepositProtectionFund
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
import rw.itunda.core.domain.TermsAcceptance
import rw.itunda.core.domain.MerchantLoyaltyAccount
import rw.itunda.core.domain.GroupAccountMember
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.KeywordAlert
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.PhoneVerificationToken
import rw.itunda.core.domain.Emoticon
import rw.itunda.core.domain.EmoticonPack
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.StockTrade
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.domain.UserEmoticonPack
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountAutoTopUpSetting
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Grow31SavingsDeposit
import rw.itunda.core.domain.Grow31SavingsPlan
import rw.itunda.core.domain.WeeklySavingsInstallment
import rw.itunda.core.domain.WeeklySavingsPlan
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

interface LoanAccountRepository : JpaRepository<LoanAccount, String> {
    fun findByUserId(userId: String): List<LoanAccount>

    // Real lost-update fix (2026-09-03): LoanAccount carries no @Version, and
    // repayLoan/refinanceLoan mutate `outstanding`/`status` after posting real ledger
    // money with a plain unlocked findById -- two concurrent repayments could both cap
    // at the same stale `outstanding`, double-debiting the wallet while only reducing
    // the recorded debt once. Same findByIdForUpdate convention this codebase already
    // establishes elsewhere.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanAccount l where l.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<LoanAccount>
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

    // Real hardening (concurrency-audit thread) -- SavingsGoal already carries @Version,
    // so a losing concurrent writer's whole transaction rolls back atomically (not a
    // fund-leak), but depositToGoal/withdrawFromGoal/autoContribute all read this row
    // unlocked before mutating currentAmount and posting real ledger legs -- adding this
    // lock avoids the wasted ledger-posting work + a raw ObjectOptimisticLockingFailureException
    // in favor of a clean domain exception, matching the established convention
    // (WalletRepository/AccountRepository.findByIdForUpdate) elsewhere in this codebase.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SavingsGoal g where g.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<SavingsGoal>

    // Real KB국민은행-style 상품만기알림서비스 (product maturity alert) candidate query --
    // see SavingsMaturityReminderScheduler's own doc comment. `targetDate` is a real,
    // unvalidated free-text field set at goal-creation time (never parsed or format-
    // enforced there), so this only narrows to real candidates -- the actual
    // date-arrived comparison happens in Kotlin, which can defensively skip a
    // genuinely unparseable value instead of a JPQL date function silently erroring.
    fun findByStatusAndTargetDateIsNotNullAndMaturityNotifiedAtIsNull(status: rw.itunda.core.domain.SavingsGoalStatus): List<SavingsGoal>
}

interface InterestJarRepository : JpaRepository<InterestJar, String>

interface DepositProtectionFundRepository : JpaRepository<DepositProtectionFund, String>

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

    // Real N+1 fix (2026-09-12) -- GroupAccountService.remindUnpaidMembers used to call
    // existsByGroupAccountIdAndUserIdAndCycleMonth once per unpaid member (up to
    // MAX_MEMBERS=100), itself inside GroupAccountDuesReminderScheduler's own
    // fixedDelay=60000 poll across every real group account with dues configured --
    // an N-query cost, scaling with member count, repeated every minute for every such
    // account. Same real "call frequency, not table size" signal
    // feedback_n_plus_1_query_sweep.md already established, one batched fetch instead.
    fun findByGroupAccountIdAndCycleMonth(groupAccountId: String, cycleMonth: String): List<GroupAccountDuesReminder>
}

interface TermsAcceptanceRepository : JpaRepository<TermsAcceptance, String> {
    fun findByUserId(userId: String): List<TermsAcceptance>
}

interface MerchantLoyaltyAccountRepository : JpaRepository<MerchantLoyaltyAccount, String> {
    fun findByMerchantIdAndCustomerId(merchantId: String, customerId: String): MerchantLoyaltyAccount?

    // Real "Store points" Membership-screen row (itunda Pay redesign, 2026-08-28) --
    // every real per-merchant balance previously only ever read one merchant at a
    // time (see getBalance's own call sites); this is the first cross-merchant read,
    // backing MerchantLoyaltyPointsService.getMyBalances.
    fun findByCustomerId(customerId: String): List<MerchantLoyaltyAccount>

    // Real scheduler feed for MerchantLoyaltyPointsExpiryScheduler -- coarse repo
    // filter (a nonzero balance untouched since before the real cutoff), exact
    // re-check-before-act done in MerchantLoyaltyPointsService.expireIfDue itself,
    // same "coarse repo filter, exact logic in the service" shape
    // P2pDelayedTransferService.getDueForRelease already establishes.
    fun findByPointBalanceGreaterThanAndUpdatedAtBefore(pointBalance: java.math.BigDecimal, cutoff: java.time.Instant): List<MerchantLoyaltyAccount>
}

interface WeeklySavingsPlanRepository : JpaRepository<WeeklySavingsPlan, String> {
    fun findByUserId(userId: String): List<WeeklySavingsPlan>
}

interface WeeklySavingsInstallmentRepository : JpaRepository<WeeklySavingsInstallment, String> {
    fun findByPlanIdOrderByWeekNumberAsc(planId: String): List<WeeklySavingsInstallment>
}

interface Grow31SavingsPlanRepository : JpaRepository<Grow31SavingsPlan, String> {
    fun findByUserId(userId: String): List<Grow31SavingsPlan>
}

interface Grow31SavingsDepositRepository : JpaRepository<Grow31SavingsDeposit, String> {
    fun findByPlanIdOrderByDayNumberAsc(planId: String): List<Grow31SavingsDeposit>
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

    // Real admin broadcast-announcement feature (2026-09-07) -- a lightweight ID
    // projection so a system-wide broadcast doesn't have to load every real User
    // entity into memory just to know who to notify.
    @Query("select u.id from User u")
    fun findAllUserIds(): List<String>

    // Batch form of findByPhoneNumber -- GroupMessagingService.createGroupByPhoneNumbers
    // uses this to resolve every invited member in one round trip instead of one
    // findByPhoneNumber call per invitee (a real N+1 found in a 2026-07-19 sweep, same
    // shape as PayrollService.runPayroll's own findByUserIdInAndType fix above).
    fun findAllByPhoneNumberIn(phoneNumbers: List<String>): List<User>

    // Real bug found live (2026-08-02): IdentityService.submit's own "reject if a
    // PENDING submission already exists" check reads-then-CREATEs a brand-new
    // KycSubmission row -- two concurrent submit() calls from the same user could both
    // pass that check before either committed and both create a real duplicate PENDING
    // KYC submission. Fixed the same way this codebase's own "reject if already exists"
    // race precedent works (e.g. AccountRepository.findByIdForUpdate): lock a DIFFERENT
    // already-existing row -- the caller's own real User row -- to serialize concurrent
    // submissions, then re-check under that lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<User>
}

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, String> {
    /** A verification challenge may be consumed by at most one confirmation transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId: String): EmailVerificationToken?

    @Modifying
    @Query("UPDATE EmailVerificationToken e SET e.usedAt = CURRENT_TIMESTAMP WHERE e.userId = :userId AND e.usedAt IS NULL")
    fun invalidateUnusedByUserId(@Param("userId") userId: String): Int

    /** Removes challenges that can no longer be presented successfully. */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.expiresAt < :expiredBefore")
    fun deleteExpiredBefore(@Param("expiredBefore") expiredBefore: Instant): Int
}

interface PhoneVerificationTokenRepository : JpaRepository<PhoneVerificationToken, String> {
    /** A verification challenge may be consumed by at most one confirmation transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId: String): PhoneVerificationToken?

    @Modifying
    @Query("UPDATE PhoneVerificationToken p SET p.usedAt = CURRENT_TIMESTAMP WHERE p.userId = :userId AND p.usedAt IS NULL")
    fun invalidateUnusedByUserId(@Param("userId") userId: String): Int

    /** Removes challenges that can no longer be presented successfully. */
    @Modifying
    @Query("DELETE FROM PhoneVerificationToken p WHERE p.expiresAt < :expiredBefore")
    fun deleteExpiredBefore(@Param("expiredBefore") expiredBefore: Instant): Int
}

interface CustomerPaymentCodeRepository : JpaRepository<CustomerPaymentCode, String> {
    /** A merchant scan may consume at most one charge -- same locking discipline as
     * PhoneVerificationTokenRepository's own findFirst...ForUpdate-shaped lookup above. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByCode(code: String): CustomerPaymentCode?

    // Real cleanup of a code the customer never showed to anyone -- same real
    // "invalidate the last one, don't let it linger" discipline
    // PhoneVerificationTokenRepository.invalidateUnusedByUserId already establishes,
    // so a fresh code generated by re-opening Pay can't leave a still-valid stale one
    // sitting around too.
    @Modifying
    @Query("UPDATE CustomerPaymentCode c SET c.usedAt = CURRENT_TIMESTAMP WHERE c.userId = :userId AND c.usedAt IS NULL")
    fun invalidateUnusedByUserId(@Param("userId") userId: String): Int
}

interface AccountRepository : JpaRepository<Account, String> {
    fun findByUserId(userId: String): List<Account>
    fun findByUserIdAndType(userId: String, type: AccountType): Account?

    // Real foreign-currency accounts (2026-07-25) -- a user can hold several
    // FOREIGN_CURRENCY accounts at once (one per currency), unlike MAIN/SAVINGS'
    // single-row-per-type assumption findByUserIdAndType relies on, same reasoning
    // GROUP/WEEKLY_SAVINGS already established. See ForeignCurrencyAccountService's own
    // doc comment.
    fun findByUserIdAndTypeOrderByCreatedAtDesc(userId: String, type: AccountType): List<Account>
    fun findByUserIdAndTypeAndCurrency(userId: String, type: AccountType, currency: String): Account?

    // Batch form of findByUserIdAndType -- PayrollService.runPayroll uses this to fetch
    // every employee's account in one round trip instead of one findByUserIdAndType call
    // per roster row (a real N+1 a large payroll roster would otherwise pay for on every
    // run).
    fun findByUserIdInAndType(userIds: List<String>, type: AccountType): List<Account>

    // Real direct P2P push-transfer recipient resolution (2026-07-20) -- see
    // P2pService.sendDirect's own doc comment. accountNumber is globally unique (see
    // AuthService.generateAccountNumber), not scoped per account type.
    fun findByAccountNumber(accountNumber: String): Account?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Account w where w.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<Account>

    // Real Deposit Protection Fund coverage math (2026-08-11) -- see
    // DepositProtectionFund.kt's own doc comment. Scoped to the three real
    // Account-backed products itunda Bank's own "Save & grow" hub actually shows
    // (SAVINGS/WEEKLY_SAVINGS/UPFRONT_DEPOSIT), not every AccountType -- MAIN is
    // itunda's separate account/Pay product (see AccountSwitcherSheet's own naming
    // research), and INVESTMENT/GROUP/BUSINESS/MINI/FOREIGN_CURRENCY are each their
    // own distinct product with different real risk, not itunda Bank deposits.
    @Query("select coalesce(sum(w.balance), 0) from Account w where w.userId = :userId and w.type in :types")
    fun sumBalanceByUserIdAndTypeIn(@Param("userId") userId: String, @Param("types") types: List<AccountType>): BigDecimal

    @Query("select coalesce(sum(w.balance), 0) from Account w where w.type in :types")
    fun sumBalanceByTypeIn(@Param("types") types: List<AccountType>): BigDecimal
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, String> {
    fun findByTransactionId(transactionId: String): List<LedgerEntry>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: String): List<LedgerEntry>

    // Real Isoko Vendor Cash Advance underwriting/collection window (2026-08-02) --
    // see VendorCashAdvanceService's own doc comment. Both getOffer's trailing-30-day
    // inflow scan and runDailyCollection's since-lastCollectionAt scan need every
    // ledger entry posted to a given account account after a cutoff instant; direction
    // (CREDIT) and the "collection -" narration-text filter are applied in-memory by
    // the caller, the same honest "no structured settlement-category field exists yet"
    // shortcut VendorCashAdvanceService names explicitly.
    fun findByAccountIdAndCreatedAtAfter(accountId: String, createdAt: Instant): List<LedgerEntry>

    // Batch form of findByTransactionId -- AccountService.getSpendingInsight uses this to
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

    // Real operator-management admin UI (item 132) -- ops-mfe's Agents tab (item 129)
    // could assign/activate an operator by userId, but never had a way to actually
    // list who's currently assigned to a given agent till first.
    fun findByAgentId(agentId: String): List<AgentOperator>
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
    // Real Toss Bank/Toss Pay separation follow-up (2026-08-21, user-provided real
    // screenshots): the real Toss Bank account detail screen and the real "Toss Pay
    // Money" detail screen each show their OWN separate transaction ledger, not one
    // shared user-wide list -- findBySenderIdOrRecipientIdOrderByCreatedAtDesc above
    // mixes every account's transactions together, which is honest for a general
    // history view but wrong for either detail screen specifically. accountId is
    // matched against the real fromAccountId/toAccountId columns every Transaction
    // row already carries (see MerchantService.collect/AccountService.confirmTransfer
    // etc. -- every money-moving write already sets one or both).
    fun findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(fromAccountId: String, toAccountId: String): List<Transaction>
    fun existsBySenderIdAndTypeAndStatus(senderId: String, type: TransactionType, status: TransactionStatus): Boolean

    /** Operations dashboard: settled volume belongs to the day a transaction completed. */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.status = :status and t.completedAt >= :from and t.completedAt < :to")
    fun sumAmountByStatusAndCompletedAtBetween(
        @Param("status") status: TransactionStatus,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): java.math.BigDecimal

    fun countByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
        status: TransactionStatus,
        from: Instant,
        to: Instant,
    ): Long

    // Merchant reports (2026-07-16): a merchant collection's Transaction row has
    // recipientId = the merchant owner's userId (see MerchantService.collect), so this
    // is the real join key -- not a merchant id or account id.
    fun findByRecipientIdAndTypeAndCreatedAtBetween(
        recipientId: String,
        type: TransactionType,
        from: Instant,
        to: Instant,
    ): List<Transaction>

    // Real FamilyLink daily spend-limit enforcement (2026-07-27) -- see FamilyLink.kt's
    // own doc comment. The child's own real sends since a real UTC day boundary,
    // summed in the service (same "coarse repo filter, exact logic in the service"
    // discipline the recurring-payment-detection query above already uses), not a new
    // running-total counter column that could drift from the real ledger.
    fun findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
        senderId: String,
        type: TransactionType,
        status: TransactionStatus,
        from: Instant,
    ): List<Transaction>

    // Real YouthAccountService daily/monthly deposit-cap enforcement (2026-07-28) -- see
    // that class's own doc comment. Coarse repo filter (this account's own real deposits
    // since a real window start), exact cap comparison in the service, same discipline
    // the recurring-payment-detection/FamilyLink spend-limit queries above establish.
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.toAccountId = :accountId and t.type = :type and t.status = :status and t.createdAt >= :from")
    fun sumAmountByToAccountIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
        @Param("accountId") accountId: String,
        @Param("type") type: TransactionType,
        @Param("status") status: TransactionStatus,
        @Param("from") from: Instant,
    ): java.math.BigDecimal

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

interface EmoticonPackRepository : JpaRepository<EmoticonPack, String> {
    fun findByActiveTrue(): List<EmoticonPack>
}

interface EmoticonRepository : JpaRepository<Emoticon, String> {
    fun findByPackIdOrderBySortOrderAsc(packId: String): List<Emoticon>
}

interface UserEmoticonPackRepository : JpaRepository<UserEmoticonPack, String> {
    fun findByUserId(userId: String): List<UserEmoticonPack>
    fun findByUserIdAndPackId(userId: String, packId: String): UserEmoticonPack?
}

interface AccountAutoTopUpSettingRepository : JpaRepository<AccountAutoTopUpSetting, String> {
    fun findByAccountId(accountId: String): AccountAutoTopUpSetting?
    fun findByEnabledTrue(): List<AccountAutoTopUpSetting>
}

interface KeywordAlertRepository : JpaRepository<KeywordAlert, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<KeywordAlert>
    fun countByUserId(userId: String): Long
    fun findByUserIdAndKeyword(userId: String, keyword: String): KeywordAlert?
    fun deleteByUserIdAndId(userId: String, id: String): Long

    // Real match query backing KeywordAlertService.notifyMatchingAlerts -- run once per
    // new listing, not once per registered alert. `keyword` is always stored trimmed +
    // lowercased at registration time (see KeywordAlert's own doc comment), so this is
    // a plain case-insensitive substring check against an already-lowercased title, not
    // a second normalization step here.
    @Query("SELECT k FROM KeywordAlert k WHERE :lowercasedTitle LIKE CONCAT('%', k.keyword, '%')")
    fun findMatchingAlerts(@Param("lowercasedTitle") lowercasedTitle: String): List<KeywordAlert>
}
