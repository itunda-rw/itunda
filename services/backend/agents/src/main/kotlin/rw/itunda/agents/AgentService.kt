package rw.itunda.agents

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.Agent
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.domain.AgentCashIn
import rw.itunda.core.domain.AgentCashOut
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentTillReconciliation
import rw.itunda.core.domain.TillReconciliationStatus
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.auth.RateLimiter
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.geo.GeoUtils
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class AgentNotFoundException(message: String) : RuntimeException(message)
class AgentSuspendedException(message: String) : RuntimeException(message)
class AgentReceiptAlreadyUsedException(message: String) : RuntimeException(message)
class AgentDailyLimitExceededException(message: String) : RuntimeException(message)
class AgentInsufficientCashException(message: String) : RuntimeException(message)
class AgentOperatorNotAuthorizedException(message: String) : RuntimeException(message)
class AgentOperatorAlreadyAssignedException(message: String) : RuntimeException(message)
class TillReconciliationAlreadySubmittedException(message: String) : RuntimeException(message)
class TillReconciliationNotFoundException(message: String) : RuntimeException(message)

data class AgentTillSnapshot(
    val agentId: String,
    val agentName: String,
    val expectedCash: BigDecimal,
    val todayCashIn: BigDecimal,
    val todayCashOut: BigDecimal,
    val reconciliation: AgentTillReconciliation?,
)

/** A bounded, auditable view for the operations team to work daily till variances. */
data class AgentReconciliationReport(
    val from: LocalDate,
    val to: LocalDate,
    val reconciliations: List<AgentTillReconciliation>,
    val pendingReviewCount: Int,
    val totalVariance: BigDecimal,
)

data class AgentActivity(
    val id: String,
    val type: String,
    val amount: BigDecimal,
    val receiptNumber: String,
    val ledgerTransactionId: String,
    val createdAt: java.time.Instant,
)
data class NearbyAgent(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
)

@Service
class AgentService(
    private val agentRepository: AgentRepository,
    private val agentOperatorRepository: AgentOperatorRepository,
    private val tillReconciliationRepository: AgentTillReconciliationRepository,
    private val agentCashInRepository: AgentCashInRepository,
    private val agentCashOutRepository: AgentCashOutRepository,
    private val accountRepository: AccountRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val withdrawalAuthorizationService: AgentWithdrawalAuthorizationService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val fraudRuleEngine: FraudRuleEngine,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun register(displayName: String, dailyCashInLimit: BigDecimal, dailyCashOutLimit: BigDecimal): Agent {
        require(displayName.isNotBlank()) { "Agent display name is required" }
        require(displayName.trim().length <= 160) { "Agent display name must be 160 characters or fewer" }
        require(dailyCashInLimit > BigDecimal.ZERO) { "Daily cash-in limit must be greater than zero" }
        require(dailyCashOutLimit > BigDecimal.ZERO) { "Daily cash-out limit must be greater than zero" }

        val id = "agent_${UUID.randomUUID()}"
        val cashAccountId = "agent_cash_${UUID.randomUUID()}"
        ledgerAccountRepository.save(LedgerAccount(cashAccountId, "Agent cash on hand: ${displayName.trim()}"))
        return agentRepository.save(Agent(id, displayName.trim(), cashAccountId, dailyCashInLimit = dailyCashInLimit, dailyCashOutLimit = dailyCashOutLimit))
    }

    fun list(): List<Agent> = agentRepository.findAll()

    @Transactional
    fun setLocation(agentId: String, latitude: Double, longitude: Double): Agent {
        require(GeoUtils.isValidCoordinate(latitude, longitude) && GeoUtils.isWithinRwanda(latitude, longitude)) { "Agent location must be within Rwanda" }
        val agent = get(agentId)
        agent.latitude = latitude
        agent.longitude = longitude
        return agentRepository.save(agent)
    }

    /** Real gap found live (2026-08-16, uncalled-endpoint sweep): the only endpoint that
     * could ever call [setLocation] lived on the deprecated admin controller
     * (`AgentAdminController`, superseded everywhere else by this real operator-facing
     * flow -- see this class's own `cashInForOperator`/`cashOut` precedent), with zero
     * caller anywhere. `AgentDiscoveryController.getNearbyAgents` is a real, already-live
     * customer-facing feature that depends entirely on `Agent.latitude`/`longitude`
     * being current -- but no real agent in the field had any way to actually report
     * where they are. Same "resolve the caller's own agent from auth" pattern
     * [activeOperator] already establishes for every other operator-facing action. */
    @Transactional
    fun setLocationForOperator(userId: String, latitude: Double, longitude: Double): Agent {
        val operator = activeOperator(userId)
        return setLocation(operator.agentId, latitude, longitude)
    }

    @Transactional
    fun fundTill(agentId: String, amount: BigDecimal, reference: String): Map<String, Any?> {
        require(amount > BigDecimal.ZERO) { "Till funding amount must be greater than zero" }
        val trimmedReference = reference.trim()
        require(trimmedReference.length in 3..80) { "Funding reference must be between 3 and 80 characters" }
        val agent = getForUpdate(agentId)
        if (agent.status != AgentStatus.ACTIVE) throw AgentSuspendedException("This agent is suspended")
        val result = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg(agent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.DEBIT, amount, "Till float received: $trimmedReference"),
                LedgerLeg("cash_vault", LedgerAccountType.CASH_VAULT, LedgerDirection.CREDIT, amount, "Till float sent to ${agent.displayName}: $trimmedReference"),
            ),
        )
        return mapOf("agent" to agent, "ledgerTransactionId" to result.transactionId, "amount" to amount, "reference" to trimmedReference)
    }

    fun nearby(latitude: Double, longitude: Double, radiusKm: Double): List<NearbyAgent> {
        require(GeoUtils.isValidCoordinate(latitude, longitude) && GeoUtils.isWithinRwanda(latitude, longitude)) { "Search location must be within Rwanda" }
        require(radiusKm in 0.1..100.0) { "radiusKm must be between 0.1 and 100" }
        return agentRepository.findAll().asSequence()
            .filter { it.status == AgentStatus.ACTIVE && it.latitude != null && it.longitude != null }
            .map {
                NearbyAgent(
                    it.id, it.displayName, it.latitude!!, it.longitude!!,
                    GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!),
                )
            }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
            .take(50)
            .toList()
    }

    @Transactional
    fun assignOperator(agentId: String, userId: String): AgentOperator {
        get(agentId)
        if (agentOperatorRepository.findByUserId(userId) != null) {
            throw AgentOperatorAlreadyAssignedException("This user is already assigned to an agent")
        }
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("Itunda user not found") }
        require(user.role != "ADMIN") { "An administrator cannot be assigned as an agent operator" }
        user.role = "AGENT"
        return agentOperatorRepository.save(AgentOperator("agent_operator_${UUID.randomUUID()}", agentId, userId))
    }

    fun getMyOperator(userId: String): AgentOperator = activeOperator(userId)

    // Real operator-management admin UI (item 132) -- see AgentOperatorRepository's
    // own doc comment on findByAgentId.
    @Transactional(readOnly = true)
    fun getOperators(agentId: String): List<AgentOperator> {
        get(agentId)
        return agentOperatorRepository.findByAgentId(agentId)
    }

    @Transactional
    fun setOperatorStatus(agentId: String, userId: String, isActive: Boolean): AgentOperator {
        val operator = agentOperatorRepository.findByUserId(userId)
            ?: throw AgentOperatorNotAuthorizedException("Agent operator not found")
        require(operator.agentId == agentId) { "This operator is not assigned to this agent" }
        operator.isActive = isActive
        return agentOperatorRepository.save(operator)
    }

    @Transactional(readOnly = true)
    fun getTillSnapshot(userId: String): AgentTillSnapshot {
        val operator = activeOperator(userId)
        val agent = get(operator.agentId)
        val account = ledgerAccountRepository.findById(agent.cashAccountId)
            .orElseThrow { IllegalStateException("Agent cash account is missing") }
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return AgentTillSnapshot(
            agent.id,
            agent.displayName,
            account.balance.negate(),
            agentCashInRepository.sumAmountByAgentIdBetween(agent.id, from, to),
            agentCashOutRepository.sumAmountByAgentIdBetween(agent.id, from, to),
            tillReconciliationRepository.findByAgentIdAndBusinessDate(agent.id, today),
        )
    }

    @Transactional(readOnly = true)
    fun getActivity(userId: String, limit: Int): List<AgentActivity> {
        require(limit in 1..100) { "limit must be between 1 and 100" }
        val operator = activeOperator(userId)
        val cashIns = agentCashInRepository.findByAgentIdOrderByCreatedAtDesc(operator.agentId).map {
            AgentActivity(it.id, "CASH_IN", it.amount, it.receiptNumber, it.ledgerTransactionId, it.createdAt)
        }
        val cashOuts = agentCashOutRepository.findByAgentIdOrderByCreatedAtDesc(operator.agentId).map {
            AgentActivity(it.id, "CASH_OUT", it.amount, it.receiptNumber, it.ledgerTransactionId, it.createdAt)
        }
        return (cashIns + cashOuts).sortedByDescending { it.createdAt }.take(limit)
    }

    @Transactional
    fun submitTillCount(userId: String, countedCash: BigDecimal): AgentTillReconciliation {
        require(countedCash >= BigDecimal.ZERO) { "Counted cash cannot be negative" }
        val operator = activeOperator(userId)
        val agent = getForUpdate(operator.agentId)
        val businessDate = LocalDate.now(ZoneOffset.UTC)
        if (tillReconciliationRepository.findByAgentIdAndBusinessDate(agent.id, businessDate) != null) {
            throw TillReconciliationAlreadySubmittedException("A till count has already been submitted for today")
        }
        val account = ledgerAccountRepository.findByIdForUpdate(agent.cashAccountId)
            .orElseThrow { IllegalStateException("Agent cash account is missing") }
        val expectedCash = account.balance.negate()
        val variance = countedCash.subtract(expectedCash)
        val status = if (variance.compareTo(BigDecimal.ZERO) == 0) TillReconciliationStatus.MATCHED else TillReconciliationStatus.PENDING_REVIEW
        return tillReconciliationRepository.save(AgentTillReconciliation(
            "tillrec_${UUID.randomUUID()}", agent.id, businessDate, expectedCash, countedCash, variance, userId, status,
        ))
    }

    fun pendingTillReconciliations(): List<AgentTillReconciliation> = tillReconciliationRepository.findByStatusOrderByCreatedAtDesc(TillReconciliationStatus.PENDING_REVIEW)

    @Transactional(readOnly = true)
    fun reconciliationReport(from: LocalDate, to: LocalDate): AgentReconciliationReport {
        require(!to.isBefore(from)) { "Report end date must not be before its start date" }
        require(!from.plusDays(30).isBefore(to)) { "Reconciliation reports are limited to 31 days" }
        val reconciliations = tillReconciliationRepository.findByBusinessDateBetweenOrderByCreatedAtDesc(from, to)
        return AgentReconciliationReport(
            from = from,
            to = to,
            reconciliations = reconciliations,
            pendingReviewCount = reconciliations.count { it.status == TillReconciliationStatus.PENDING_REVIEW },
            totalVariance = reconciliations.fold(BigDecimal.ZERO) { total, reconciliation -> total.add(reconciliation.variance) },
        )
    }

    @Transactional
    fun resolveTillReconciliation(id: String, reviewerUserId: String, note: String): AgentTillReconciliation {
        // Review decisions are audit records.  Lock before checking the status so two
        // administrators cannot both resolve the same variance with different notes.
        val reconciliation = tillReconciliationRepository.findByIdForUpdate(id)
            .orElseThrow { TillReconciliationNotFoundException("Till reconciliation not found") }
        require(reconciliation.status == TillReconciliationStatus.PENDING_REVIEW) { "This reconciliation is not awaiting review" }
        require(note.trim().isNotEmpty() && note.trim().length <= 255) { "A review note between 1 and 255 characters is required" }
        reconciliation.status = TillReconciliationStatus.RESOLVED
        reconciliation.reviewedByUserId = reviewerUserId
        reconciliation.reviewNote = note.trim()
        reconciliation.reviewedAt = java.time.Instant.now()
        return tillReconciliationRepository.save(reconciliation)
    }

    @Transactional
    fun setStatus(agentId: String, status: AgentStatus): Agent {
        val agent = get(agentId)
        agent.status = status
        return agentRepository.save(agent)
    }

    @Transactional
    fun cashIn(agentId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String, acceptedByUserId: String): Map<String, Any?> {
        // Real gap found (2026-09-07, Agents product-completeness pass): zero abuse-rate
        // limiting on the core money-moving op, only the daily-*amount* limit above (a
        // different concern -- that caps total value, not call frequency). Sized like a
        // real high-frequency business actor, not a low-frequency consumer -- same real
        // precedent PaymentsApiController's own merchant-API `payment-create` limit
        // already establishes (30/minute), not the 20-30/hour consumer pattern
        // Bills/Eats/Commerce use.
        rateLimiter.checkLimit("agent:cash-in:$agentId", limit = 30, window = Duration.ofMinutes(1))
        require(amount > BigDecimal.ZERO) { "Cash-in amount must be greater than zero" }
        val receipt = receiptNumber.trim()
        require(receipt.isNotEmpty() && receipt.length <= 80) { "Receipt number must be between 1 and 80 characters" }
        if (agentCashInRepository.existsByReceiptNumber(receipt) || agentCashOutRepository.existsByReceiptNumber(receipt)) {
            throw AgentReceiptAlreadyUsedException("This cash receipt has already been processed")
        }

        val agent = getForUpdate(agentId)
        if (agent.status != AgentStatus.ACTIVE) throw AgentSuspendedException("This agent is suspended")

        val today = LocalDate.now(ZoneOffset.UTC)
        val totalToday = agentCashInRepository.sumAmountByAgentIdBetween(
            agent.id, today.atStartOfDay().toInstant(ZoneOffset.UTC), today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
        )
        if (totalToday.add(amount) > agent.dailyCashInLimit) {
            throw AgentDailyLimitExceededException("This cash-in exceeds the agent's daily limit")
        }

        val account = accountRepository.findByAccountNumber(accountNumber.trim())
            ?: throw IllegalArgumentException("Itunda account not found")
        require(account.type == AccountType.MAIN) { "Cash-in is only available for a main account" }
        require(account.isActive) { "This account is frozen pending review" }

        // Real MTN MoMo-style agent commission -- see AgentCommissionSchedule's own doc
        // comment. Folded into this SAME ledger transaction (one atomic settlement,
        // matching how a real fee-inclusive transfer already posts more than 2 legs in
        // this codebase) rather than a second call -- honestly skipped, never blocking
        // the real customer cash-in, if the operator who accepted it has no real account
        // of their own to receive it (shouldn't happen for a real registered operator,
        // but this is customer money moving, not something to risk on that assumption).
        val commission = AgentCommissionSchedule.computeCommission(amount)
        val operatorAccount = accountRepository.findByUserIdAndType(acceptedByUserId, AccountType.MAIN)
        val legs = mutableListOf(
            LedgerLeg(agent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.DEBIT, amount, "Cash accepted at ${agent.displayName} receipt $receipt"),
            LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Cash-in at ${agent.displayName} receipt $receipt"),
        )
        if (operatorAccount != null) {
            legs.add(LedgerLeg("agent_commission_expense", LedgerAccountType.AGENT_COMMISSION_EXPENSE, LedgerDirection.DEBIT, commission, "Agent commission - cash-in receipt $receipt"))
            legs.add(LedgerLeg(operatorAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, commission, "Agent commission - cash-in receipt $receipt"))
        }

        val ledger = ledgerService.postLedgerTransaction(account.currency, legs)
        // Real gap found 2026-08-08 (East African mobile-money research pass, checking
        // this module against real M-Pesa/MTN MoMo agent-network fraud patterns):
        // agent cash-in/cash-out had zero FraudRuleEngine coverage -- the exact same
        // missing-wiring bug already found and fixed 3 times this session in
        // Gift/GiftVoucher/SplitBill. recipientUserId is null (an agent isn't a
        // recurring itunda counterparty the NEW_RECIPIENT rule's shape fits), so only
        // HIGH_VALUE/VELOCITY apply -- still real signal for the two most-cited agent-
        // channel risks: deposit structuring (repeated/large cash-ins) and a
        // compromised-account being rapidly drained via agent counters.
        fraudRuleEngine.evaluate(account.userId, null, amount, ledger.transactionId)
        val cashIn = agentCashInRepository.save(AgentCashIn(
            id = "cashin_${UUID.randomUUID()}", agentId = agent.id, accountId = account.id, receiptNumber = receipt,
            ledgerTransactionId = ledger.transactionId, amount = amount, acceptedByUserId = acceptedByUserId,
        ))
        val transaction = transactionRepository.save(Transaction(
            id = ledger.transactionId, referenceNumber = "CASH${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = agent.id, recipientId = account.userId, toAccountId = account.id, amount = amount, fee = BigDecimal.ZERO,
            currency = account.currency, type = TransactionType.DEPOSIT, status = TransactionStatus.COMPLETED,
            description = "Cash-in at ${agent.displayName}", channel = "ITUNDA_AGENT", providerReference = receipt,
            completedAt = cashIn.createdAt,
        ))
        val cashInTitle = "Cash added"
        val cashInBody = "${amount.toPlainString()} ${account.currency} was added at ${agent.displayName}"
        notificationRepository.save(Notification(
            id = "notification_${UUID.randomUUID()}", userId = account.userId, type = "cash_in",
            title = cashInTitle, body = cashInBody,
            isRead = false, createdAt = cashIn.createdAt,
            dataJson = "{\"agentId\":\"${agent.id}\",\"receiptNumber\":\"$receipt\",\"transactionId\":\"${ledger.transactionId}\"}",
        ))
        sendPushAfterCommit(account.userId, cashInTitle, cashInBody, ledger.transactionId)
        return mapOf(
            "cashIn" to cashIn, "transaction" to transaction, "newBalance" to account.balance,
            "operatorCommission" to (if (operatorAccount != null) commission else BigDecimal.ZERO),
        )
    }

    fun cashInForOperator(userId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String): Map<String, Any?> {
        val operator = activeOperator(userId)
        return cashIn(operator.agentId, accountNumber, amount, receiptNumber, userId)
    }

    @Transactional
    fun cashOut(agentId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String, authorizationCode: String, paidByUserId: String): Map<String, Any?> {
        // Same real gap, same fix as cashIn above -- see its own comment for the full
        // account.
        rateLimiter.checkLimit("agent:cash-out:$agentId", limit = 30, window = Duration.ofMinutes(1))
        require(amount > BigDecimal.ZERO) { "Cash-out amount must be greater than zero" }
        val receipt = receiptNumber.trim()
        require(receipt.isNotEmpty() && receipt.length <= 80) { "Receipt number must be between 1 and 80 characters" }
        if (agentCashOutRepository.existsByReceiptNumber(receipt) || agentCashInRepository.existsByReceiptNumber(receipt)) {
            throw AgentReceiptAlreadyUsedException("This cash receipt has already been processed")
        }
        val agent = getForUpdate(agentId)
        if (agent.status != AgentStatus.ACTIVE) throw AgentSuspendedException("This agent is suspended")

        val today = LocalDate.now(ZoneOffset.UTC)
        val totalToday = agentCashOutRepository.sumAmountByAgentIdBetween(
            agent.id, today.atStartOfDay().toInstant(ZoneOffset.UTC), today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
        )
        if (totalToday.add(amount) > agent.dailyCashOutLimit) {
            throw AgentDailyLimitExceededException("This cash-out exceeds the agent's daily limit")
        }
        val account = accountRepository.findByAccountNumber(accountNumber.trim())
            ?: throw IllegalArgumentException("Itunda account not found")
        require(account.type == AccountType.MAIN) { "Cash-out is only available from a main account" }
        // This is deliberately before cash leaves the till. consume() joins this outer
        // transaction, so a ledger failure rolls its consumed marker back too.
        withdrawalAuthorizationService.consume(authorizationCode, account.id, amount)

        // LedgerService records an asset debit as a negative signed balance. For an
        // agent's cash-on-hand asset, negate it back to the amount physically expected
        // in the till. Lock it before the debit/credit posting below: concurrent cash-outs
        // at the same store cannot both observe the same notes and overpay the till.
        val cashAccount = ledgerAccountRepository.findByIdForUpdate(agent.cashAccountId)
            .orElseThrow { IllegalStateException("Agent cash account is missing") }
        val availableCash = cashAccount.balance.negate()
        if (availableCash < amount) {
            throw AgentInsufficientCashException("This agent has insufficient cash on hand")
        }

        // Real MTN MoMo-style agent commission -- see AgentCommissionSchedule's own doc
        // comment and cashIn's own identical call-site comment for the full account.
        // Paid out of itunda's own real commission expense, never out of the real
        // customer's own cash-out amount above.
        val commission = AgentCommissionSchedule.computeCommission(amount)
        val operatorAccount = accountRepository.findByUserIdAndType(paidByUserId, AccountType.MAIN)
        val legs = mutableListOf(
            LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Cash-out at ${agent.displayName} receipt $receipt"),
            LedgerLeg(agent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.CREDIT, amount, "Cash paid at ${agent.displayName} receipt $receipt"),
        )
        if (operatorAccount != null) {
            legs.add(LedgerLeg("agent_commission_expense", LedgerAccountType.AGENT_COMMISSION_EXPENSE, LedgerDirection.DEBIT, commission, "Agent commission - cash-out receipt $receipt"))
            legs.add(LedgerLeg(operatorAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, commission, "Agent commission - cash-out receipt $receipt"))
        }

        val ledger = ledgerService.postLedgerTransaction(account.currency, legs)
        // Same gap, same fix as cashIn above -- see its own comment for the full
        // account. Cash-out is the closer analogue to P2P/Gift's own "account owner
        // moving money away" shape (a compromised account rapidly drained via an
        // agent counter is the single most classic real fraud pattern here).
        fraudRuleEngine.evaluate(account.userId, null, amount, ledger.transactionId)
        val cashOut = agentCashOutRepository.save(AgentCashOut(
            id = "cashout_${UUID.randomUUID()}", agentId = agent.id, accountId = account.id, receiptNumber = receipt,
            ledgerTransactionId = ledger.transactionId, amount = amount, paidByUserId = paidByUserId,
        ))
        val transaction = transactionRepository.save(Transaction(
            id = ledger.transactionId, referenceNumber = "CASH${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = account.userId, recipientId = agent.id, fromAccountId = account.id, amount = amount, fee = BigDecimal.ZERO,
            currency = account.currency, type = TransactionType.WITHDRAWAL, status = TransactionStatus.COMPLETED,
            description = "Cash-out at ${agent.displayName}", channel = "ITUNDA_AGENT", providerReference = receipt,
            completedAt = cashOut.createdAt,
        ))
        val cashOutTitle = "Cash withdrawn"
        val cashOutBody = "${amount.toPlainString()} ${account.currency} was withdrawn at ${agent.displayName}"
        notificationRepository.save(Notification(
            id = "notification_${UUID.randomUUID()}", userId = account.userId, type = "cash_out",
            title = cashOutTitle, body = cashOutBody,
            isRead = false, createdAt = cashOut.createdAt,
            dataJson = "{\"agentId\":\"${agent.id}\",\"receiptNumber\":\"$receipt\",\"transactionId\":\"${ledger.transactionId}\"}",
        ))
        sendPushAfterCommit(account.userId, cashOutTitle, cashOutBody, ledger.transactionId)
        return mapOf(
            "cashOut" to cashOut, "transaction" to transaction, "newBalance" to account.balance,
            "operatorCommission" to (if (operatorAccount != null) commission else BigDecimal.ZERO),
        )
    }

    fun cashOutForOperator(userId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String, authorizationCode: String): Map<String, Any?> {
        val operator = activeOperator(userId)
        return cashOut(operator.agentId, accountNumber, amount, receiptNumber, authorizationCode, userId)
    }

    private fun get(agentId: String): Agent = agentRepository.findById(agentId)
        .orElseThrow { AgentNotFoundException("Agent not found") }

    /** A push is irreversible; only expose a cash movement after its ledger transaction commits. */
    private fun sendPushAfterCommit(userId: String, title: String, body: String, transactionId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("transactionId" to transactionId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    private fun getForUpdate(agentId: String): Agent = agentRepository.findByIdForUpdate(agentId)
        .orElseThrow { AgentNotFoundException("Agent not found") }

    /**
     * An operator assignment alone is not enough to operate a cash location.  Keeping
     * this check here, rather than only on cash-in/out, makes an agent suspension a
     * complete operational stop: the assigned account cannot inspect a till, submit a
     * count, or continue using the operator app while a review is in progress.
     */
    private fun activeOperator(userId: String): AgentOperator {
        val operator = agentOperatorRepository.findByUserId(userId)
            ?.takeIf { it.isActive }
            ?: throw AgentOperatorNotAuthorizedException("This account is not an active agent operator")
        val agent = get(operator.agentId)
        if (agent.status != AgentStatus.ACTIVE) throw AgentSuspendedException("This agent is suspended")
        return operator
    }
}
