package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Kakao Bank 모임통장 (group/shared account) equivalent -- see
 * docs/TOSS_PARITY_MATRIX.md's Group Account row. Modeled on the real, sourced
 * mechanics: the creator (organizer/총무) holds real withdrawal/settlement authority,
 * every other invited member can view balance and deposit but cannot withdraw --
 * matching how a real 모임통장 works, not a symmetric joint account. Backed by a real
 * `Account` (AccountType.GROUP) rather than inventing a second balance concept --
 * deposits/withdrawals are the same real ledger-backed ACCOUNT-to-ACCOUNT movement
 * every other real money-moving feature in this backend already uses.
 */
@Entity
@Table(name = "group_accounts")
class GroupAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "owner_id", nullable = false, length = 64)
    val ownerId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    // Real KakaoBank 회비 (dues) amount, null until the organizer sets one -- see
    // GroupAccountService.setDuesAmount's own doc comment.
    @Column(name = "monthly_dues_amount", precision = 18, scale = 2)
    var monthlyDuesAmount: BigDecimal? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", name = "", ownerId = "", accountId = "")
}
