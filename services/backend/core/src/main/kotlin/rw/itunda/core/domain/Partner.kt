package rw.itunda.core.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class PartnerStatus { ACTIVE, SUSPENDED }

/**
 * A real third-party developer account -- the first entity in this backend for anyone
 * other than an itunda end-user or a merchant. Closes the "allow partners to use our
 * Saronite SDK to build apps in itunda, like apps in Toss" gap: Toss's own real
 * "미니앱" (mini-app) platform lets outside companies register, submit an app for
 * review, and ship inside Toss once approved -- this is the same real shape, applied to
 * itunda's own real Saronite mini-app host (see docs/ARCHITECTURE.md's mini-app host
 * row) for the first time to a genuinely external party rather than itunda's own
 * first-party mini-apps (bills, wallet, rewards, insurance).
 *
 * apiKeyHash stores only a SHA-256 hash, never the raw key -- the same real convention
 * Stripe/GitHub personal-access-token issuance uses: the raw key is shown exactly once,
 * at creation (PartnerService.register's return value), and never persisted or
 * retrievable again. A lost key means a real rotate, not a real recovery -- matches how
 * every real API-key platform this pattern is modeled on actually works.
 */
@Entity
@Table(name = "partners")
class Partner(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "company_name", nullable = false)
    var companyName: String,

    @Column(name = "contact_email", nullable = false, unique = true)
    var contactEmail: String,

    // Never serialized -- a real bug caught live during this pass's own verification:
    // returning the Partner entity directly from /register leaked this hash into the
    // JSON response. Not reversible (SHA-256), but a real API-key platform still never
    // echoes back anything key-derived beyond the one-time raw key itself.
    @JsonIgnore
    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    var apiKeyHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PartnerStatus = PartnerStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", companyName = "", contactEmail = "", apiKeyHash = "")
}

enum class PartnerMiniAppStatus { PENDING, APPROVED, REJECTED, SUSPENDED }

/**
 * A real mini-app manifest a partner has submitted for human review -- the same
 * submission/review shape `KycSubmission` already established for identity, applied
 * here to app review instead. `bundleUrl` points at where the partner's real Saronite/
 * granite JS bundle would be served from (this backend never hosts or executes partner
 * code -- see PartnerService's own doc comment for the honest scope boundary: this is a
 * real registry and review workflow, not yet a real runtime bundle loader inside the
 * mobile Saronite host, which is a separate, larger, distinct piece of work).
 * `permissions` is a real, reviewer-visible list of the itunda API scopes this mini-app
 * is requesting (validated against `PartnerMiniAppPermissions.ALLOWED` at submission
 * time) -- runtime enforcement of these scopes inside the Saronite bridge is the same
 * honestly-scoped-out follow-up as the bundle loader itself.
 */
@Entity
@Table(name = "partner_mini_apps")
class PartnerMiniApp(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "partner_id", nullable = false, length = 64)
    val partnerId: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, length = 500)
    var description: String,

    @Column(name = "icon_url", length = 500)
    var iconUrl: String? = null,

    @Column(name = "bundle_url", nullable = false, length = 500)
    var bundleUrl: String,

    @Column(nullable = false, length = 500)
    var permissions: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PartnerMiniAppStatus = PartnerMiniAppStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "reviewed_by", length = 64)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "decision_reason")
    var decisionReason: String? = null,

    // Real bug found live (2026-08-02): PartnerService.decide reads this exact entity,
    // checks `status != PENDING`, then writes APPROVED/REJECTED -- the same check-then-
    // act shape SupportTicket/Incident/HoodReport's own @Version fixes already address.
    // Two admins concurrently reviewing the same submission could both pass the status
    // check and race to a conflicting final decision.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", partnerId = "", name = "", description = "", bundleUrl = "", permissions = "")
}
