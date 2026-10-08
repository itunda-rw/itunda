package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class PartnerMiniAppReleaseStatus {
    APPROVED,
    STAGED,
    ACTIVE,
    ROLLED_BACK,
    REJECTED
}

/**
 * Immutable release history for a partner mini-app.
 *
 * PartnerMiniApp remains the catalog-facing manifest; this table preserves every
 * approved artifact so deployment can stage, activate, and roll back without
 * mutating the identity of an older release.
 */
@Entity
@Table(name = "partner_mini_app_releases")
class PartnerMiniAppRelease(
    @Id
    @Column(length = 64)
    val releaseId: String,

    @Column(name = "mini_app_id", nullable = false, length = 64)
    val miniAppId: String,

    @Column(name = "bundle_url", nullable = false, length = 500)
    val bundleUrl: String,

    @Column(name = "manifest_sha256", nullable = false, length = 64)
    val manifestSha256: String,

    @Column(name = "bundle_sha256", nullable = false, length = 64)
    val bundleSha256: String,

    @Column(name = "bundle_size_bytes", nullable = false)
    val bundleSizeBytes: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PartnerMiniAppReleaseStatus = PartnerMiniAppReleaseStatus.APPROVED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "activated_at")
    var activatedAt: Instant? = null,

    @Column(name = "rolled_back_at")
    var rolledBackAt: Instant? = null,

    @Column(name = "rollback_reason", length = 255)
    var rollbackReason: String? = null,
) {
    protected constructor() : this(
        releaseId = "",
        miniAppId = "",
        bundleUrl = "",
        manifestSha256 = "",
        bundleSha256 = "",
        bundleSizeBytes = 0,
    )
}
