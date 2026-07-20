package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class SplitBillStatus { OPEN, SETTLED }
enum class SplitBillParticipantStatus { PENDING, PAID }

/**
 * A real KakaoPay-style "정산하기" (settlement/split-bill) request, chat-embedded in an
 * existing [GroupConversation] -- see `docs/DESIGN_REFERENCES.md` Section 6/7 for the
 * sourced design rationale (KakaoPay's real 정산하기 versus Toss's deliberately-minimal
 * spending-history-anchored 더치페이) and Section 7's own "chat-embedded financial
 * action" shell that this shares with [Gift].
 *
 * Structurally distinct from [Gift]: a gift moves money from one sender into escrow for
 * one recipient to later claim. A split bill is the reverse direction and shape -- the
 * organizer already fronted the whole bill in real life, and is now requesting it back
 * from N named group members. There is no escrow hold here (nothing needs to be "claimed
 * back," the organizer already has the money in hand outside this system); each
 * participant instead pays their own share directly into the organizer's wallet,
 * mirroring `P2pService.sendDirect`'s real direct WALLET-to-WALLET push, just fanned out
 * per-participant with a shared parent record for the settlement thread.
 *
 * Honestly scoped v1 (see `SplitBillService`'s own doc comment for the full account):
 * a single flat, even split with silent rounding-remainder absorption -- one designated
 * participant's share silently absorbs the leftover minor-currency-unit remainder so the
 * sum of every [SplitBillParticipant.shareAmount] always reconciles exactly to
 * [totalAmount]. KakaoPay's own randomized ladder-game mode, multi-round tracking, and
 * scheduled reminder nudges are deliberately deferred, named as follow-ups, not
 * attempted here.
 */
@Entity
@Table(name = "split_bills")
class SplitBill(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "organizer_id", nullable = false, length = 64)
    val organizerId: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    val totalAmount: BigDecimal,

    @Column(nullable = false, length = 200)
    val description: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SplitBillStatus = SplitBillStatus.OPEN,

    @Column(name = "settled_at")
    var settledAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", organizerId = "", groupConversationId = "", messageId = "",
        totalAmount = BigDecimal.ZERO, description = "",
    )
}

/**
 * One real named participant's share of a [SplitBill] -- never includes the organizer
 * themselves (they fronted the bill, they don't owe their own request). `shareAmount` is
 * this participant's exact, already-rounding-reconciled portion; `status` flips to PAID
 * the moment their own direct wallet-to-wallet payment posts, mirroring
 * `P2pPaymentRequestStatus`'s own PENDING/COMPLETED shape but per-participant rather than
 * a single request/payer pair.
 */
@Entity
@Table(name = "split_bill_participants")
class SplitBillParticipant(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "split_bill_id", nullable = false, length = 64)
    val splitBillId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "share_amount", nullable = false, precision = 18, scale = 2)
    val shareAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SplitBillParticipantStatus = SplitBillParticipantStatus.PENDING,

    @Column(name = "paid_transaction_id", length = 64)
    var paidTransactionId: String? = null,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", splitBillId = "", userId = "", shareAmount = BigDecimal.ZERO,
    )
}
