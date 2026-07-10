package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/** Mirrors backend/src/types/index.ts Holding. */
@Entity
@Table(name = "holdings")
class Holding(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "stock_id", nullable = false, length = 16)
    val stockId: String,

    @Column(nullable = false, precision = 18, scale = 4)
    var shares: BigDecimal,

    @Column(name = "avg_price", nullable = false, precision = 18, scale = 4)
    var avgPrice: BigDecimal,
) {
    protected constructor() : this(id = "", userId = "", walletId = "", stockId = "", shares = BigDecimal.ZERO, avgPrice = BigDecimal.ZERO)
}
