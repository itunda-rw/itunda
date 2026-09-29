package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class EmoticonAcquisitionSource { PURCHASED, GIFTED }

/**
 * A real KakaoTalk Emoticon Store-style purchasable sticker pack -- see Kakao Corp's
 * own official "Our new language, emoticons!" product page and Inquivix's coverage
 * ("Kakao Emoticons: The $300M Sticker Market Driving Korean Digital Culture",
 * cumulative sends past 300 billion as of Nov 2025). Server-seeded/admin-managed
 * catalog, not user-generated content -- no moderation surface needed, the same
 * "itunda's own real catalog" shape `LoanOffer`/`InsuranceProduct` already establish
 * for other non-user-generated product catalogs.
 *
 * Deliberately, honestly scoped: the real Emoticon Plus monthly-subscription
 * all-you-can-use tier remains a real, separate, not-attempted-here follow-up -- this
 * is the one-time-purchase-per-pack half only, the same real "buy it once, own it"
 * model every purchase this backend already supports (shopping, insurance enrollment)
 * uses. **Gifting a pack to another user, the sibling follow-up this comment used to
 * name as also open, closed 2026-07-28** -- see `EmoticonService.giftPack`'s own doc
 * comment.
 */
@Entity
@Table(name = "emoticon_packs")
class EmoticonPack(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 120)
    val title: String,

    @Column(name = "artist_name", nullable = false, length = 120)
    val artistName: String,

    @Column(name = "thumbnail_url", nullable = false, length = 255)
    val thumbnailUrl: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val price: BigDecimal,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", title = "", artistName = "", thumbnailUrl = "", price = BigDecimal.ZERO)
}

/** One real sticker image within an [EmoticonPack] -- the actual content a user picks
 * and sends as a message, not the pack itself (a user owns a pack, but sends one of
 * its individual emoticons at a time, same real Kakao UX). */
@Entity
@Table(name = "emoticons")
class Emoticon(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "pack_id", nullable = false, length = 64)
    val packId: String,

    @Column(name = "image_url", nullable = false, length = 255)
    val imageUrl: String,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,
) {
    protected constructor() : this(id = "", packId = "", imageUrl = "")
}

/** A real, owned pack -- unique on (userId, packId), same "own it once" shape a real
 * purchase establishes anywhere else in this backend. `source` distinguishes a real
 * paid purchase from a future gifted-pack path (out of scope this pass, see
 * EmoticonPack's own doc comment) without needing a schema change later. */
@Entity
@Table(name = "user_emoticon_packs")
class UserEmoticonPack(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "pack_id", nullable = false, length = 64)
    val packId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val source: EmoticonAcquisitionSource,

    @Column(name = "acquired_at", nullable = false)
    val acquiredAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", packId = "", source = EmoticonAcquisitionSource.PURCHASED)
}
