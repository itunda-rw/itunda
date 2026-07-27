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
enum class SplitBillMode { EVEN, LADDER }

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
 * Even split (v1) has silent rounding-remainder absorption -- one designated
 * participant's share silently absorbs the leftover minor-currency-unit remainder so the
 * sum of every [SplitBillParticipant.shareAmount] always reconciles exactly to
 * [totalAmount]. [mode]/[ladderVarianceLevel] (2026-07-25) add KakaoPay's real
 * "사다리타기" (ladder-game) randomized mode -- see `SplitBillService.ladderSplit`'s own
 * doc comment for the 3 variance levels, sourced from `docs/DESIGN_REFERENCES.md`
 * Section 6. Scheduled reminder nudges closed 2026-07-27 -- see
 * `SplitBillReminderScheduler`'s own doc comment. Photo receipt attach closed 2026-07-28
 * -- see `SplitBillService.attachReceipt`'s own doc comment. Multi-round tracking (up
 * to 5) remains the one still-open, deferred follow-up.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var mode: SplitBillMode = SplitBillMode.EVEN,

    // 1/2/3, only meaningful when mode == LADDER -- see SplitBillService.ladderSplit's
    // own doc comment for what each level means. Stored (not just used transiently at
    // creation) so the split bill's own real transparency -- "this was randomized, at
    // this variance" -- survives for every viewer, not just the organizer who chose it.
    @Column(name = "ladder_variance_level")
    var ladderVarianceLevel: Int? = null,

    // Real photo receipt attach (2026-07-28) -- see SplitBillService.attachReceipt's own
    // doc comment. A URL, not a binary upload, same "no file-storage layer in this
    // backend" simplification IdentityController's documentReference/AuthService's
    // UpdateProfilePhotoRequest already established. NULL means no receipt attached yet.
    @Column(name = "receipt_image_url", length = 2048)
    var receiptImageUrl: String? = null,

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

    // Real scheduled reminder nudges (2026-07-27) -- see SplitBillReminderScheduler's
    // own doc comment. null means never reminded yet -- a fresh, still-unpaid
    // participant is immediately due for their first real nudge.
    @Column(name = "last_reminder_sent_at")
    var lastReminderSentAt: Instant? = null,
) {
    protected constructor() : this(
        id = "", splitBillId = "", userId = "", shareAmount = BigDecimal.ZERO,
    )
}
