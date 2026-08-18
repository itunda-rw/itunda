package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real, immutable per-user consent audit record -- see `TermsCatalog`'s own doc comment
 * for the full account of why this exists (itunda had zero terms-consent tracking
 * before this, and Korea's real 2025-02-14 dark-pattern regulation and 2026-09-11
 * penalty increase are exactly the kind of thing an auditable "who agreed to what,
 * and when" trail is required to defend against). One row per accepted term per user,
 * written once at `AuthService.register` and never mutated or deleted afterward --
 * a real compliance record, not a live-editable preference (an unsubscribe from
 * marketing is a separate, later action, not a rewrite of this historical fact).
 */
@Entity
@Table(name = "terms_acceptances")
class TermsAcceptance(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "terms_id", nullable = false, length = 64)
    val termsId: String,

    @Column(name = "terms_version", nullable = false, length = 32)
    val termsVersion: String,

    @Column(name = "accepted_at", nullable = false)
    val acceptedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", termsId = "", termsVersion = "")
}
