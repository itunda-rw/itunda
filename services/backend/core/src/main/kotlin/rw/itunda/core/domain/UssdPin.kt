package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real USSD basic-banking PIN (item 231) -- the fourth feature in this document not
 * sourced from Toss Bank/당근마켓/Coupang/Naver/Kakao, going beyond those reference
 * ecosystems per the same explicit standing instruction as Ikimina/SACCO/Cooperative
 * harvest advance. Sourced from real, published statistics: Rwanda's smartphone
 * penetration is only ~34-35% as of 2024/2025 despite ~87.4% overall mobile-phone
 * penetration (IGIHE/Statista) -- roughly two-thirds of Rwandans with a phone have a
 * feature phone, not a smartphone, and cannot use itunda's app at all. This is the
 * single largest access gap any feature in this session addresses.
 *
 * A real, separate 4-6 digit numeric PIN, distinct from the account password -- a
 * phone number alone is never sufficient USSD auth (the real M-Pesa-style USSD PIN
 * convention this session is deliberately matching), and the account password itself
 * is both too long to type reliably on a USSD numeric keypad and too sensitive to
 * reuse across a second, lower-assurance channel. `pinHash` uses the exact same real
 * `BCryptPasswordEncoder` this codebase's own `AuthService` already establishes for
 * the account password -- no second hashing scheme invented.
 */
@Entity
@Table(name = "ussd_pins")
class UssdPin(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "pin_hash", nullable = false, length = 100)
    var pinHash: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", pinHash = "")
}
