package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// Real customer-presented payment code (2026-08-11) -- matches real KakaoPay/Toss
// Pay's actual primary in-person flow: the CUSTOMER opens Pay and a scannable code
// is already on screen, no typing on either side -- a merchant's POS scans it and
// enters the amount. This is the reverse of the existing merchant-generated
// PaymentIntent QR (merchant sets the amount upfront, customer scans/types it): here
// the code carries no amount at all, just a real, short-lived, single-use identity
// token resolved server-side at charge time. Same real single-use/expiring-token
// shape as PhoneVerificationToken/EmailVerificationToken, not a new pattern.
@Entity
@Table(name = "customer_payment_codes")
class CustomerPaymentCode(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, unique = true, length = 64)
    val code: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", code = "", expiresAt = Instant.now())
}
