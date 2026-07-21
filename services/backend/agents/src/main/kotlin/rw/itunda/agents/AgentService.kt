package rw.itunda.agents

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.geo.GeoUtils
import java.math.BigDecimal
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

data class AgentActivity(
    val id: String,
    val type: String,
    val amount: BigDecimal,
    val receiptNumber: String,
    val ledgerTransactionId: String,
    val createdAt: java.time.Instant,
)
data class NearbyAgent(val agent: Agent, val distanceKm: Double)

@Service
class AgentService(
    private val agentRepository: AgentRepository,
    private val agentOperatorRepository: AgentOperatorRepository,
    private val tillReconciliationRepository: AgentTillReconciliationRepository,
    private val agentCashInRepository: AgentCashInRepository,
    private val agentCashOutRepository: AgentCashOutRepository,
    private val walletRepository: WalletRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val withdrawalAuthorizationService: AgentWithdrawalAuthorizationService,
    private val notificationRepository: NotificationRepository,
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

    fun nearby(latitude: Double, longitude: Double, radiusKm: Double): List<NearbyAgent> {
        require(GeoUtils.isValidCoordinate(latitude, longitude) && GeoUtils.isWithinRwanda(latitude, longitude)) { "Search location must be within Rwanda" }
        require(radiusKm in 0.1..100.0) { "radiusKm must be between 0.1 and 100" }
        return agentRepository.findAll().asSequence()
            .filter { it.status == AgentStatus.ACTIVE && it.latitude != null && it.longitude != null }
            .map { NearbyAgent(it, GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!)) }
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

    @Transactional
    fun resolveTillReconciliation(id: String, reviewerUserId: String, note: String): AgentTillReconciliation {
        val reconciliation = tillReconciliationRepository.findById(id).orElseThrow { TillReconciliationNotFoundException("Till reconciliation not found") }
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

        val wallet = walletRepository.findByAccountNumber(accountNumber.trim())
            ?: throw IllegalArgumentException("Itunda account not found")
        require(wallet.type == WalletType.MAIN) { "Cash-in is only available for a main account" }
        require(wallet.isActive) { "This account is frozen pending review" }

        val ledger = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(agent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.DEBIT, amount, "Cash accepted at ${agent.displayName} receipt $receipt"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Cash-in at ${agent.displayName} receipt $receipt"),
            ),
        )
        val cashIn = agentCashInRepository.save(AgentCashIn(
            id = "cashin_${UUID.randomUUID()}", agentId = agent.id, walletId = wallet.id, receiptNumber = receipt,
            ledgerTransactionId = ledger.transactionId, amount = amount, acceptedByUserId = acceptedByUserId,
        ))
        val transaction = transactionRepository.save(Transaction(
            id = ledger.transactionId, referenceNumber = "CASH${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = agent.id, recipientId = wallet.userId, toWalletId = wallet.id, amount = amount, fee = BigDecimal.ZERO,
            currency = wallet.currency, type = TransactionType.DEPOSIT, status = TransactionStatus.COMPLETED,
            description = "Cash-in at ${agent.displayName}", channel = "ITUNDA_AGENT", providerReference = receipt,
            completedAt = cashIn.createdAt,
        ))
        notificationRepository.save(Notification(
            id = "notification_${UUID.randomUUID()}", userId = wallet.userId, type = "cash_in",
            title = "Cash added", body = "${amount.toPlainString()} ${wallet.currency} was added at ${agent.displayName}",
            isRead = false, createdAt = cashIn.createdAt,
            dataJson = "{\"agentId\":\"${agent.id}\",\"receiptNumber\":\"$receipt\",\"transactionId\":\"${ledger.transactionId}\"}",
        ))
        return mapOf("cashIn" to cashIn, "transaction" to transaction, "newBalance" to wallet.balance)
    }

    fun cashInForOperator(userId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String): Map<String, Any?> {
        val operator = activeOperator(userId)
        return cashIn(operator.agentId, accountNumber, amount, receiptNumber, userId)
    }

    @Transactional
    fun cashOut(agentId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String, authorizationCode: String, paidByUserId: String): Map<String, Any?> {
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
        val wallet = walletRepository.findByAccountNumber(accountNumber.trim())
            ?: throw IllegalArgumentException("Itunda account not found")
        require(wallet.type == WalletType.MAIN) { "Cash-out is only available from a main account" }
        // This is deliberately before cash leaves the till. consume() joins this outer
        // transaction, so a ledger failure rolls its consumed marker back too.
        withdrawalAuthorizationService.consume(authorizationCode, wallet.id, amount)

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

        val ledger = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Cash-out at ${agent.displayName} receipt $receipt"),
                LedgerLeg(agent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.CREDIT, amount, "Cash paid at ${agent.displayName} receipt $receipt"),
            ),
        )
        val cashOut = agentCashOutRepository.save(AgentCashOut(
            id = "cashout_${UUID.randomUUID()}", agentId = agent.id, walletId = wallet.id, receiptNumber = receipt,
            ledgerTransactionId = ledger.transactionId, amount = amount, paidByUserId = paidByUserId,
        ))
        val transaction = transactionRepository.save(Transaction(
            id = ledger.transactionId, referenceNumber = "CASH${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = wallet.userId, recipientId = agent.id, fromWalletId = wallet.id, amount = amount, fee = BigDecimal.ZERO,
            currency = wallet.currency, type = TransactionType.WITHDRAWAL, status = TransactionStatus.COMPLETED,
            description = "Cash-out at ${agent.displayName}", channel = "ITUNDA_AGENT", providerReference = receipt,
            completedAt = cashOut.createdAt,
        ))
        notificationRepository.save(Notification(
            id = "notification_${UUID.randomUUID()}", userId = wallet.userId, type = "cash_out",
            title = "Cash withdrawn", body = "${amount.toPlainString()} ${wallet.currency} was withdrawn at ${agent.displayName}",
            isRead = false, createdAt = cashOut.createdAt,
            dataJson = "{\"agentId\":\"${agent.id}\",\"receiptNumber\":\"$receipt\",\"transactionId\":\"${ledger.transactionId}\"}",
        ))
        return mapOf("cashOut" to cashOut, "transaction" to transaction, "newBalance" to wallet.balance)
    }

    fun cashOutForOperator(userId: String, accountNumber: String, amount: BigDecimal, receiptNumber: String, authorizationCode: String): Map<String, Any?> {
        val operator = activeOperator(userId)
        return cashOut(operator.agentId, accountNumber, amount, receiptNumber, authorizationCode, userId)
    }

    private fun get(agentId: String): Agent = agentRepository.findById(agentId)
        .orElseThrow { AgentNotFoundException("Agent not found") }

    private fun getForUpdate(agentId: String): Agent = agentRepository.findByIdForUpdate(agentId)
        .orElseThrow { AgentNotFoundException("Agent not found") }

    private fun activeOperator(userId: String): AgentOperator = agentOperatorRepository.findByUserId(userId)
        ?.takeIf { it.isActive }
        ?: throw AgentOperatorNotAuthorizedException("This account is not an active agent operator")
}
