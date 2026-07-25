package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class GiftStatus { PENDING, CLAIMED, EXPIRED }

// Real KakaoPay 송금봉투 (money envelope) themed presets (2026-07-26) -- see
// docs/DESIGN_REFERENCES.md's own sourced account (story.kakaopay.com/
// support.kakaopay.com): "themed presets ([축하해요]/[내마음]/[행운만땅]/[정산해요]) carry
// occasion/message instead of free text." Exactly these 4 real, sourced presets --
// DESIGN_REFERENCES.md itself notes "a complete, verified catalog... wasn't found," so
// this doesn't invent a 5th one. Optional and additive alongside the pre-existing free-
// text `note`, never replacing it -- a theme communicates occasion, a note still
// carries an actual message, the same way KakaoPay's own envelope still shows the
// sender's typed text alongside the preset.
enum class GiftTheme { CONGRATULATIONS, HEARTFELT, GOOD_LUCK, SETTLE_UP }

/**
 * A real KakaoTalk-style "선물하기" (gift) money gift sent within an existing 1:1 chat
 * conversation -- distinct from `P2pService.sendDirect`'s instant push-transfer: a
 * gift's money leaves the sender's wallet immediately into a real GIFT_HOLDING escrow
 * account (same "hold, don't move directly" pattern `EatsOrder`'s own delivery-fee
 * escrow already established), and only actually reaches the recipient's wallet when
 * they explicitly "open"/claim it -- the same real two-step UX every KakaoPay/Toss
 * gift-money product uses, not a cosmetic delay. An unclaimed gift auto-refunds to the
 * sender after [EXPIRY] via [rw.itunda.gift.GiftExpiryScheduler], mirroring
 * `DispatchOfferScheduler`'s own "scan for expired rows, act, done" shape.
 */
@Entity
@Table(name = "gifts")
class Gift(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "sender_id", nullable = false, length = 64)
    val senderId: String,

    @Column(name = "recipient_id", nullable = false, length = 64)
    val recipientId: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(length = 500)
    val note: String?,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val theme: GiftTheme? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: GiftStatus = GiftStatus.PENDING,

    @Column(name = "hold_transaction_id", nullable = false, length = 64)
    val holdTransactionId: String,

    @Column(name = "claim_transaction_id", length = 64)
    var claimTransactionId: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", senderId = "", recipientId = "", conversationId = "", messageId = "",
        amount = BigDecimal.ZERO, note = null, holdTransactionId = "", expiresAt = Instant.now(),
    )

    companion object {
        val EXPIRY: java.time.Duration = java.time.Duration.ofDays(7)
    }
}
