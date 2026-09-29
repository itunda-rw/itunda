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
// its own dedicated Account row resolved by GroupAccount.accountId instead.
//
// WEEKLY_SAVINGS added 2026-07-21 for the real KakaoBank 26주적금 (26-week savings)
// equivalent -- see WeeklySavingsPlan.kt's doc comment. Same reasoning as GROUP: a
// user can open many 26-week plans over time (one at a time or several concurrently),
// so each plan gets its own dedicated Account resolved by WeeklySavingsPlan.accountId,
// never via findByUserIdAndType.
// FOREIGN_CURRENCY added 2026-07-25 for the real 토스뱅크 외화통장 (foreign-currency
// account) equivalent -- see ForeignCurrencyAccountService's own doc comment. Same
// multi-row-per-user reasoning as GROUP/WEEKLY_SAVINGS: a user can hold several (one per
// currency), so resolved via AccountRepository.findByUserIdAndTypeAndCurrency, never
// findByUserIdAndType.
//
// BUSINESS added 2026-07-25 for the real 토스뱅크 개인사업자 (business banking for sole
// proprietors) equivalent -- see MerchantBusinessAccountService's own doc comment. One
// per registered Merchant (unlike GROUP/WEEKLY_SAVINGS/FOREIGN_CURRENCY above), so this
// one IS resolved via the plain findByUserIdAndType, same as MAIN/SAVINGS.
// MINI added 2026-07-28 -- see YouthAccountService's own doc comment. No exhaustive
// `when (AccountType)` exists anywhere in this codebase (checked before adding), so this
// carries none of the blast-radius risk a new LedgerAccountType value would.
// Real Toss Bank/Toss Pay separation (2026-08-21, direct user correction): PAY is
// itunda's own real "itunda Pay money" stored-value balance, isolated from MAIN
// ("itunda Bank account") the same way Toss's own real architecture keeps them
// distinct products -- a payer's Bank account never needs to know which 가맹점
// (affiliated merchant) they paid; Pay money's own balance and transaction history
// carry that detail instead. See MerchantService.collect's own doc comment for how
// payment collection now debits PAY instead of MAIN, auto-funding the shortfall from
// MAIN (an itunda-internal instant transfer, itself just a normal visible Bank-side
// transaction) before falling back to an external AutoTopUpService.topUpShortfall
// pull from a linked outside bank/card -- matching the user's own description that
// real Toss Pay money "can be connected to different bank accounts and different
// cards," not only the same provider's own Bank product.
enum class AccountType { MAIN, PAY, SAVINGS, INVESTMENT, LOAN, GROUP, WEEKLY_SAVINGS, UPFRONT_DEPOSIT, FOREIGN_CURRENCY, BUSINESS, MINI, GROW31_SAVINGS }

/** Mirrors backend/src/types/index.ts Account. Money is BigDecimal, not float, on purpose. */
@Entity
@Table(name = "wallets")
class Account(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    var accountNumber: String,

    @Column(name = "account_name", nullable = false)
    var accountName: String,

    // Real Toss Bank reference (2026-09-12, 12 real "관리"/Manage-screen
    // screenshots showing "계좌 별명" -- account nickname): a real, user-editable
    // personal label, distinct from accountName above (a fixed, system-assigned
    // label set once at creation, e.g. "Youth Account") -- a user can rename this
    // to whatever they like, accountName never changes.
    @Column(length = 50)
    var nickname: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: AccountType,

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
        type = AccountType.MAIN, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
    )
}
