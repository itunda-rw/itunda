package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class MerchantStatus { ACTIVE, SUSPENDED }

/**
 * A real, minimal merchant record -- registration + a settlement wallet reference.
 * Reuses the owner's existing MAIN wallet as the settlement wallet rather than
 * introducing a new WalletType, since AuthService.register already provisions one
 * for every user. See rw.itunda.merchant.MerchantService for the real subset of
 * docs/MERCHANT_SERVICES.md this implements (QR-style payment collection), and
 * that doc's own header for why the rest of its spec (POS/cards/B2B/webhooks)
 * isn't built.
 */
@Entity
@Table(name = "merchants")
class Merchant(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "owner_user_id", nullable = false, unique = true, length = 64)
    val ownerUserId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "business_name", nullable = false)
    var businessName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MerchantStatus = MerchantStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", ownerUserId = "", walletId = "", businessName = "")
}
