package rw.itunda.core.agents

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class WithdrawalAuthorizationInvalidException(message: String) : RuntimeException(message)
class TooManyWithdrawalAuthorizationsException(message: String) : RuntimeException(message)

data class WithdrawalAuthorizationView(
    val id: String,
    val code: String,
    val amount: BigDecimal,
    val expiresAt: Instant,
    val status: String,
    val createdAt: Instant,
)

@Entity
@Table(name = "agent_withdrawal_authorizations")
class AgentWithdrawalAuthorization(
    @Id @Column(length = 64) val id: String,
    @Column(name = "user_id", nullable = false, length = 64) val userId: String,
    @Column(name = "wallet_id", nullable = false, length = 64) val walletId: String,
    @Column(nullable = false, unique = true, length = 12) val code: String,
    @Column(nullable = false, precision = 18, scale = 2) val amount: BigDecimal,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Column(name = "consumed_at") var consumedAt: Instant? = null,
    @Column(name = "cancelled_at") var cancelledAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
) { protected constructor() : this("", "", "", "", BigDecimal.ZERO, Instant.EPOCH) }

interface AgentWithdrawalAuthorizationRepository : org.springframework.data.jpa.repository.JpaRepository<AgentWithdrawalAuthorization, String> {
    fun findByCode(code: String): AgentWithdrawalAuthorization?
    fun existsByCode(code: String): Boolean
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<AgentWithdrawalAuthorization>
}

@Service
class AgentWithdrawalAuthorizationService(
    private val repository: AgentWithdrawalAuthorizationRepository,
    private val walletRepository: WalletRepository,
) {
    fun list(userId: String): List<WithdrawalAuthorizationView> = repository.findByUserIdOrderByCreatedAtDesc(userId).map { authorization ->
        val status = when {
            authorization.cancelledAt != null -> "CANCELLED"
            authorization.consumedAt != null -> "CONSUMED"
            authorization.expiresAt.isBefore(Instant.now()) -> "EXPIRED"
            else -> "ACTIVE"
        }
        WithdrawalAuthorizationView(authorization.id, authorization.code, authorization.amount, authorization.expiresAt, status, authorization.createdAt)
    }

    @Transactional
    fun create(userId: String, amount: BigDecimal): AgentWithdrawalAuthorization {
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be greater than zero" }
        val active = repository.findByUserIdOrderByCreatedAtDesc(userId).count {
            it.consumedAt == null && it.cancelledAt == null && it.expiresAt.isAfter(Instant.now())
        }
        if (active >= 3) throw TooManyWithdrawalAuthorizationsException("Cancel or use an existing withdrawal authorization first")
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw IllegalArgumentException("Main wallet not found")
        require(wallet.isActive) { "Wallet is frozen pending review" }
        var code: String
        do { code = UUID.randomUUID().toString().replace("-", "").take(8).uppercase() } while (repository.existsByCode(code))
        return repository.save(AgentWithdrawalAuthorization("withdrawal_auth_${UUID.randomUUID()}", userId, wallet.id, code, amount, Instant.now().plusSeconds(600)))
    }

    @Transactional
    fun consume(code: String, walletId: String, amount: BigDecimal): AgentWithdrawalAuthorization {
        val authorization = repository.findByCode(code.trim().uppercase())
            ?: throw WithdrawalAuthorizationInvalidException("Withdrawal authorization is invalid")
        if (authorization.consumedAt != null || authorization.cancelledAt != null || authorization.expiresAt.isBefore(Instant.now()) || authorization.walletId != walletId || authorization.amount.compareTo(amount) != 0) {
            throw WithdrawalAuthorizationInvalidException("Withdrawal authorization is invalid or expired")
        }
        authorization.consumedAt = Instant.now()
        return authorization
    }

    @Transactional
    fun cancel(userId: String, code: String): AgentWithdrawalAuthorization {
        val authorization = repository.findByCode(code.trim().uppercase())
            ?: throw WithdrawalAuthorizationInvalidException("Withdrawal authorization is invalid")
        if (authorization.userId != userId || authorization.consumedAt != null || authorization.cancelledAt != null || authorization.expiresAt.isBefore(Instant.now())) {
            throw WithdrawalAuthorizationInvalidException("Withdrawal authorization cannot be cancelled")
        }
        authorization.cancelledAt = Instant.now()
        return authorization
    }
}
