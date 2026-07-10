package rw.itunda.core.ledger.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "ledger_accounts")
data class Account(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val accountNumber: String,

    @Column(nullable = false)
    val ownerId: UUID, // Reference to the User or System Entity

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val accountType: AccountType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val currency: Currency = Currency.RWF,

    // The derived balance from all line items.
    // In a high-volume Toss-like system, this might be a materialized view or updated asynchronously,
    // but caching it here with optimistic locking prevents overdrafts concurrently.
    @Column(nullable = false)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Version
    var version: Long = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class AccountType {
    LIABILITY, // User deposits (e.g., Main Account)
    ASSET,     // Bank's money (e.g., Suspense accounts, Loan principal)
    EQUITY,
    REVENUE,   // Fees collected by Itunda
    EXPENSE
}

enum class Currency {
    RWF, USD
}
