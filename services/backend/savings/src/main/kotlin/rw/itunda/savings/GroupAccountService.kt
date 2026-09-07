package rw.itunda.savings

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupAccount
import rw.itunda.core.domain.GroupAccountContribution
import rw.itunda.core.domain.GroupAccountDuesReminder
import rw.itunda.core.domain.GroupAccountMember
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.GroupAccountContributionRepository
import rw.itunda.core.repository.GroupAccountDuesReminderRepository
import rw.itunda.core.repository.GroupAccountMemberRepository
import rw.itunda.core.repository.GroupAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

private const val MAX_MEMBERS = 100

class GroupAccountNotFoundException(message: String) : RuntimeException(message)
class GroupAccountRecipientNotFoundException(message: String) : RuntimeException(message)
class GroupAccountAlreadyMemberException(message: String) : RuntimeException(message)
class GroupAccountFullException(message: String) : RuntimeException(message)
class GroupAccountNoAccountException(message: String) : RuntimeException(message)
class InvalidGroupAccountNameException(message: String) : RuntimeException(message)

data class GroupAccountView(val account: GroupAccount, val balance: BigDecimal, val members: List<GroupAccountMemberView>)
data class GroupAccountMemberView(val userId: String, val firstName: String, val lastName: String, val isOwner: Boolean, val joinedAt: Instant)
data class GroupAccountDuesStatusView(val duesAmount: BigDecimal?, val cycleMonth: String, val members: List<GroupAccountDuesMemberView>)
data class GroupAccountDuesMemberView(val userId: String, val firstName: String, val lastName: String, val contributedAmount: BigDecimal, val paid: Boolean)

/**
 * Real Kakao Bank 모임통장 (group/shared account) -- see GroupAccount.kt's own doc
 * comment for the real, sourced mechanics this mirrors: the creator holds real
 * withdrawal authority, invited members can view and deposit but not withdraw, capped
 * at a real 100 members (사용자당 최대 100개 모임, 모임당 최대 100명, per Kakao Bank's
 * own published product page). Reuses the exact real recipient-resolution shape
 * P2pService.sendDirect established (findByPhoneNumber, real 404 on no match) and the
 * exact real ledger-movement shape every other money-moving feature in this backend
 * already uses -- no new balance concept, just a real Account(type=GROUP).
 */
@Service
class GroupAccountService(
    private val groupAccountRepository: GroupAccountRepository,
    private val groupAccountMemberRepository: GroupAccountMemberRepository,
    private val groupAccountContributionRepository: GroupAccountContributionRepository,
    private val groupAccountDuesReminderRepository: GroupAccountDuesReminderRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    private fun currentCycleMonth(): String = YearMonth.now().toString()

    @Transactional
    fun createGroupAccount(ownerId: String, name: String): GroupAccount {
        // Real gap found 2026-09-05: GroupAccount.name's own @Column already declares a
        // real length = 255, but this real registration entry point never enforced it
        // before insert -- and 255 alone isn't even the right bound here, since this
        // same name is also concatenated into the settlement Account's own accountName
        // below ("$name (Group Account)", 17 extra characters) -- a name right at 255
        // would overflow accountName's own real Hibernate-default-255 column even
        // though it would fit GroupAccount.name's own explicit one. 238 is the real
        // safe bound (255 - 17) for both columns, not just the naive one.
        if (name.isBlank() || name.length > 238) {
            throw InvalidGroupAccountNameException("Group account name must be between 1 and 238 characters")
        }
        // Real anti-spam limit, added from day one this time (not as a later fix) --
        // this is a real free-row-creation endpoint, the exact class of gap the
        // 2026-07-19 sweep found across P2P/Savings/Marketplace/etc.
        rateLimiter.checkLimit("group-account:create:$ownerId", limit = 10, window = Duration.ofHours(1))
        val owner = userRepository.findById(ownerId).orElseThrow { GroupAccountNotFoundException("Account not found") }

        val userAccount = accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = ownerId,
                accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "$name (Group Account)",
                type = AccountType.GROUP,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        val account = groupAccountRepository.save(
            GroupAccount(id = "grp_${UUID.randomUUID()}", name = name, ownerId = ownerId, accountId = userAccount.id),
        )
        groupAccountMemberRepository.save(
            GroupAccountMember(id = "grpmem_${UUID.randomUUID()}", groupAccountId = account.id, userId = ownerId),
        )
        return account
    }

    fun getMyGroupAccounts(userId: String): List<GroupAccount> {
        val groupAccountIds = groupAccountMemberRepository.findByUserId(userId).map { it.groupAccountId }
        return groupAccountRepository.findAllByIdIn(groupAccountIds)
    }

    fun getGroupAccount(userId: String, groupAccountId: String): GroupAccountView {
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        groupAccountMemberRepository.findByGroupAccountIdAndUserId(groupAccountId, userId)
            ?: throw GroupAccountNotFoundException("Group account not found")
        val userAccount = accountRepository.findById(account.accountId).orElseThrow { GroupAccountNoAccountException("Account not found") }

        val members = groupAccountMemberRepository.findByGroupAccountId(groupAccountId)
        // Batch-resolved, same no-N+1 discipline as ProductFavoriteService.getMyFavorites.
        val users = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        val memberViews = members.map { m ->
            val u = users[m.userId]
            GroupAccountMemberView(userId = m.userId, firstName = u?.firstName ?: "", lastName = u?.lastName ?: "", isOwner = m.userId == account.ownerId, joinedAt = m.joinedAt)
        }
        return GroupAccountView(account = account, balance = userAccount.balance, members = memberViews)
    }

    @Transactional
    fun inviteMember(ownerId: String, groupAccountId: String, phoneNumber: String): GroupAccountMemberView {
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        if (account.ownerId != ownerId) throw GroupAccountNotFoundException("Group account not found")

        val invitee = userRepository.findByPhoneNumber(phoneNumber.trim())
            ?: throw GroupAccountRecipientNotFoundException("No itunda account found for this phone number")
        if (groupAccountMemberRepository.findByGroupAccountIdAndUserId(groupAccountId, invitee.id) != null) {
            throw GroupAccountAlreadyMemberException("This person is already a member")
        }
        // Real cap, matching Kakao Bank's own published limit (100 members per group).
        if (groupAccountMemberRepository.countByGroupAccountId(groupAccountId) >= MAX_MEMBERS) {
            throw GroupAccountFullException("This group account has reached its real ${MAX_MEMBERS}-member limit")
        }

        val member = groupAccountMemberRepository.save(
            GroupAccountMember(id = "grpmem_${UUID.randomUUID()}", groupAccountId = groupAccountId, userId = invitee.id),
        )
        val inviteTitle = "Added to \"${account.name}\""
        val inviteBody = "You can now view and deposit to this group account."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = invitee.id, type = "GROUP_ACCOUNT_INVITE",
                title = inviteTitle, body = inviteBody,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"groupAccountId\":\"${account.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(invitee.id, inviteTitle, inviteBody, mapOf("groupAccountId" to account.id))
        return GroupAccountMemberView(userId = invitee.id, firstName = invitee.firstName, lastName = invitee.lastName, isOwner = false, joinedAt = member.joinedAt)
    }

    @Transactional
    fun deposit(userId: String, groupAccountId: String, amount: BigDecimal): GroupAccountView {
        // Real anti-spam/cost limit -- createGroupAccount already has one, deposit never
        // did, the same "row creation vs. repeatable action" gap class this pass's own
        // Loans research already named.
        rateLimiter.checkLimit("group-account:deposit:$userId", limit = 30, window = Duration.ofHours(1))
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        groupAccountMemberRepository.findByGroupAccountIdAndUserId(groupAccountId, userId)
            ?: throw GroupAccountNotFoundException("Group account not found")

        val sourceAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw GroupAccountNoAccountException("No account found for this account")
        val groupAccount = accountRepository.findById(account.accountId).orElseThrow { GroupAccountNoAccountException("Account not found") }

        ledgerService.postLedgerTransaction(
            sourceAccount.currency,
            listOf(
                LedgerLeg(sourceAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Deposit to \"${account.name}\""),
                LedgerLeg(groupAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Deposit to \"${account.name}\""),
            ),
        )
        // Real dues tracking (2026-07-26) -- see getDuesStatus's own doc comment. Every
        // deposit counts toward this cycle's dues; there's no separate "pay dues" action,
        // matching how a real 모임통장 works.
        groupAccountContributionRepository.save(
            GroupAccountContribution(id = "grpcontrib_${UUID.randomUUID()}", groupAccountId = account.id, userId = userId, cycleMonth = currentCycleMonth(), amount = amount),
        )
        notifyOtherMembers(account, actorId = userId, title = "New deposit to \"${account.name}\"", body = "${amount.toPlainString()} RWF was added by a member.")
        return getGroupAccount(userId, groupAccountId)
    }

    @Transactional
    fun withdraw(ownerId: String, groupAccountId: String, amount: BigDecimal): GroupAccountView {
        // Real anti-spam/cost limit -- same convention deposit above now establishes.
        rateLimiter.checkLimit("group-account:withdraw:$ownerId", limit = 30, window = Duration.ofHours(1))
        require(amount > BigDecimal.ZERO) { "Amount must be greater than zero" }
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        // Real Kakao Bank behavior: withdrawal/settlement authority belongs to the
        // organizer only, unlike deposit which any real member can do.
        if (account.ownerId != ownerId) throw GroupAccountNotFoundException("Group account not found")

        val groupAccount = accountRepository.findById(account.accountId).orElseThrow { GroupAccountNoAccountException("Account not found") }
        val ownerAccount = accountRepository.findByUserIdAndType(ownerId, AccountType.MAIN)
            ?: throw GroupAccountNoAccountException("No account found for this account")
        if (groupAccount.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance in this group account")
        }

        ledgerService.postLedgerTransaction(
            groupAccount.currency,
            listOf(
                LedgerLeg(groupAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Withdrawal from \"${account.name}\""),
                LedgerLeg(ownerAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Withdrawal from \"${account.name}\""),
            ),
        )
        notifyOtherMembers(account, actorId = ownerId, title = "Withdrawal from \"${account.name}\"", body = "${amount.toPlainString()} RWF was withdrawn by the organizer.")
        return getGroupAccount(ownerId, groupAccountId)
    }

    /**
     * Real KakaoBank 회비 (monthly dues) amount -- see kakaobank.com/products/moim's own
     * "자동 회비 관리" (automated dues management). Organizer-only, matching every other
     * real settlement/withdrawal-adjacent authority on this entity. `null` clears dues
     * tracking entirely (no reminders sent, `getDuesStatus` reports `duesAmount = null`).
     */
    @Transactional
    fun setDuesAmount(ownerId: String, groupAccountId: String, amount: BigDecimal?): GroupAccount {
        require(amount == null || amount >= BigDecimal.ZERO) { "Dues amount cannot be negative" }
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        if (account.ownerId != ownerId) throw GroupAccountNotFoundException("Group account not found")
        account.monthlyDuesAmount = amount
        return groupAccountRepository.save(account)
    }

    /**
     * Real per-member dues status for the current real calendar cycle -- any member can
     * see this (matches Kakao Bank's own real shared transparency), not just the
     * organizer. "Paid" is computed live from real deposits (`GroupAccountContribution`),
     * never a separately-tracked flag that could drift from what actually happened.
     */
    fun getDuesStatus(userId: String, groupAccountId: String): GroupAccountDuesStatusView {
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        groupAccountMemberRepository.findByGroupAccountIdAndUserId(groupAccountId, userId)
            ?: throw GroupAccountNotFoundException("Group account not found")

        val cycleMonth = currentCycleMonth()
        val members = groupAccountMemberRepository.findByGroupAccountId(groupAccountId)
        val users = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        val contributedByUser = groupAccountContributionRepository.findByGroupAccountIdAndCycleMonth(groupAccountId, cycleMonth)
            .groupBy { it.userId }.mapValues { (_, rows) -> rows.sumOf { it.amount } }

        val duesAmount = account.monthlyDuesAmount
        val memberViews = members.map { m ->
            val u = users[m.userId]
            val contributed = contributedByUser[m.userId] ?: BigDecimal.ZERO
            GroupAccountDuesMemberView(
                userId = m.userId, firstName = u?.firstName ?: "", lastName = u?.lastName ?: "",
                contributedAmount = contributed, paid = duesAmount != null && contributed >= duesAmount,
            )
        }
        return GroupAccountDuesStatusView(duesAmount = duesAmount, cycleMonth = cycleMonth, members = memberViews)
    }

    /**
     * Organizer's real one-tap "미납부 모임원에게 알림 발송" (send unpaid members a
     * notification) action -- see docs/DESIGN_REFERENCES.md's Group Account row. Shares
     * `remindUnpaidMembers`'s real per-cycle dedupe with the automatic scheduler so a
     * member already reminded automatically today doesn't get double-notified; returns
     * how many were actually reminded (can be 0 if everyone's already paid or already
     * reminded this cycle -- an honest count, not a fire-and-forget action).
     */
    @Transactional
    fun requestUnpaidDues(ownerId: String, groupAccountId: String): Int {
        val account = groupAccountRepository.findById(groupAccountId).orElseThrow { GroupAccountNotFoundException("Group account not found") }
        if (account.ownerId != ownerId) throw GroupAccountNotFoundException("Group account not found")
        require(account.monthlyDuesAmount != null) { "No monthly dues amount is set for this group account" }
        return remindUnpaidMembers(account, currentCycleMonth())
    }

    /**
     * Real read-only listing of every group account with dues configured -- used by
     * GroupAccountDuesReminderScheduler's own per-account loop, see that class's doc
     * comment for why the loop lives there and not in a batch `@Transactional` method
     * here.
     */
    fun getAccountsWithDuesConfigured(): List<GroupAccount> = groupAccountRepository.findByMonthlyDuesAmountIsNotNull()

    /**
     * Called by GroupAccountDuesReminderScheduler, once per due group account -- see
     * that class's own doc comment for the real batch-transaction-poisoning bug this
     * closes (docs/DESIGN_REFERENCES.md Section 180). This used to be
     * `sendAutomaticDuesReminders()`, a single `@Transactional` method that looped over
     * EVERY group account with dues configured network-wide (and, within each, every
     * unpaid member) inside one shared transaction with no try/catch anywhere in the
     * loop. A real DB issue on any one member's `Notification`/`GroupAccountDuesReminder`
     * insert -- anywhere in that whole network-wide batch -- would have rolled back
     * every OTHER already-reminded account's real dedupe row and Notification from that
     * same poll too, not just the bad one, the same "one bad row blocks the sweep for
     * every other real due row" bug class Section 179 already found and fixed for
     * `MerchantBookingService.processNoShows`. `remindUnpaidMembers`'s own re-fetch of
     * `account.monthlyDuesAmount` is itself the re-check-before-act guard (a race where
     * the amount was cleared between the scheduler's read and this call safely no-ops).
     */
    @Transactional
    fun sendAutomaticDuesRemindersFor(groupAccountId: String): Int {
        val account = groupAccountRepository.findById(groupAccountId).orElse(null) ?: return 0
        return remindUnpaidMembers(account, currentCycleMonth())
    }

    private fun remindUnpaidMembers(account: GroupAccount, cycleMonth: String): Int {
        val duesAmount = account.monthlyDuesAmount ?: return 0
        val members = groupAccountMemberRepository.findByGroupAccountId(account.id)
        val contributedByUser = groupAccountContributionRepository.findByGroupAccountIdAndCycleMonth(account.id, cycleMonth)
            .groupBy { it.userId }.mapValues { (_, rows) -> rows.sumOf { it.amount } }

        var remindedCount = 0
        for (m in members) {
            val contributed = contributedByUser[m.userId] ?: BigDecimal.ZERO
            if (contributed >= duesAmount) continue
            if (groupAccountDuesReminderRepository.existsByGroupAccountIdAndUserIdAndCycleMonth(account.id, m.userId, cycleMonth)) continue

            val reminderTitle = "Dues reminder for \"${account.name}\""
            val reminderBody = "You haven't paid this month's ${duesAmount.toPlainString()} RWF dues yet."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = m.userId, type = "GROUP_ACCOUNT_DUES_REMINDER",
                    title = reminderTitle, body = reminderBody,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"groupAccountId\":\"${account.id}\"}",
                ),
            )
            groupAccountDuesReminderRepository.save(
                GroupAccountDuesReminder(id = "grpdue_${UUID.randomUUID()}", groupAccountId = account.id, userId = m.userId, cycleMonth = cycleMonth),
            )
            pushNotificationService.sendToUser(m.userId, reminderTitle, reminderBody, mapOf("groupAccountId" to account.id))
            remindedCount++
        }
        return remindedCount
    }

    // Real transparency, matching Kakao Bank's own real-time shared-activity feed --
    // never notifies the actor about their own action, same rule established elsewhere
    // in this codebase (Eats/Commerce buyer notifications).
    private fun notifyOtherMembers(account: GroupAccount, actorId: String, title: String, body: String) {
        val otherMemberIds = groupAccountMemberRepository.findByGroupAccountId(account.id).map { it.userId }.filter { it != actorId }
        for (memberId in otherMemberIds) {
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = memberId, type = "GROUP_ACCOUNT_ACTIVITY",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"groupAccountId\":\"${account.id}\"}",
                ),
            )
        }
    }
}
