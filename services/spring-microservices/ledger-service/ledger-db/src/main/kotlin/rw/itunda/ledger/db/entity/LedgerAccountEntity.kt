package rw.itunda.ledger.db.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Database Entity mapping the domain model to MySQL.
 * Toss Rule: Strict DB layer separated from Domain.
 */
@Entity
@Table(name = "ledger_accounts")
class LedgerAccountEntity(
    @Id
    @Column(name = "account_id", length = 36, nullable = false)
    val accountId: String,

    @Column(name = "customer_id", length = 36, nullable = false)
    val customerId: String,

    @Column(name = "currency", length = 3, nullable = false)
    val currency: String = "RWF",

    @Column(name = "balance", precision = 19, scale = 4, nullable = false)
    var balance: BigDecimal,

    @Column(name = "account_type", length = 30, nullable = false)
    val accountType: String,

    @Column(name = "status", length = 20, nullable = false)
    var status: String,

    @Version
    @Column(name = "version")
    var version: Long = 0 // Optimistic locking to prevent race conditions during concurrent transfers
)
