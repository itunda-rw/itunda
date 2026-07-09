package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class WalletType { MAIN, SAVINGS, INVESTMENT, LOAN }

/** Mirrors backend/src/types/index.ts Wallet. Money is BigDecimal, not float, on purpose. */
@Entity
@Table(name = "wallets")
class Wallet(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    var accountNumber: String,

    @Column(name = "account_name", nullable = false)
    var accountName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: WalletType,

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal,

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 2)
    var availableBalance: BigDecimal,

    @Column(nullable = false, length = 8)
    var currency: String = "RWF",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", userId = "", accountNumber = "", accountName = "",
        type = WalletType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
    )
}
