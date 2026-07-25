package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// GROUP added 2026-07-20 for the real 모임통장/group-account equivalent -- see
// GroupAccount.kt's doc comment. Deliberately not queried via findByUserIdAndType the
// way MAIN/SAVINGS are: a user can own or belong to many group accounts, so each has
// its own dedicated Wallet row resolved by GroupAccount.walletId instead.
//
// WEEKLY_SAVINGS added 2026-07-21 for the real KakaoBank 26주적금 (26-week savings)
// equivalent -- see WeeklySavingsPlan.kt's doc comment. Same reasoning as GROUP: a
// user can open many 26-week plans over time (one at a time or several concurrently),
// so each plan gets its own dedicated Wallet resolved by WeeklySavingsPlan.walletId,
// never via findByUserIdAndType.
// FOREIGN_CURRENCY added 2026-07-25 for the real 토스뱅크 외화통장 (foreign-currency
// account) equivalent -- see ForeignCurrencyWalletService's own doc comment. Same
// multi-row-per-user reasoning as GROUP/WEEKLY_SAVINGS: a user can hold several (one per
// currency), so resolved via WalletRepository.findByUserIdAndTypeAndCurrency, never
// findByUserIdAndType.
//
// BUSINESS added 2026-07-25 for the real 토스뱅크 개인사업자 (business banking for sole
// proprietors) equivalent -- see MerchantBusinessAccountService's own doc comment. One
// per registered Merchant (unlike GROUP/WEEKLY_SAVINGS/FOREIGN_CURRENCY above), so this
// one IS resolved via the plain findByUserIdAndType, same as MAIN/SAVINGS.
enum class WalletType { MAIN, SAVINGS, INVESTMENT, LOAN, GROUP, WEEKLY_SAVINGS, UPFRONT_DEPOSIT, FOREIGN_CURRENCY, BUSINESS }

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
