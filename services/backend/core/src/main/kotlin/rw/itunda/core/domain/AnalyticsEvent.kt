package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real, minimal product-analytics event (2026-08-10) -- see the "itunda: the wedge,
 * not the mirror" strategy memo from this same session, recommendation (ii): itunda
 * has no equivalent of Grab's own real 12%→54% cross-sell number (FoxData, 2020
 * disclosure) because it has never recorded a single user-behavior event anywhere --
 * every "lead with X" product decision until now has been a judgment call, not a
 * measured one.
 *
 * Deliberately NOT a general-purpose event-schema platform: `eventName` is a plain,
 * small, closed vocabulary (see AnalyticsEventName in the analytics module), and
 * `metadataJson` is optional free-form detail for the one event that needs it (which
 * cooperative-savings item was tapped). A real analytics platform (Amplitude/Segment-
 * shaped, arbitrary event schemas, funnels, cohorts) is a genuinely larger, separate
 * build -- this is scoped to answering the one real question the memo poses, not
 * building the infrastructure to answer every future one.
 */
@Entity
@Table(name = "analytics_events")
class AnalyticsEvent(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "event_name", nullable = false, length = 64)
    val eventName: String,

    // Real platform breakdown (android/ios/web) -- the client reports its own platform
    // since the backend has no reliable way to infer it from the request alone (same
    // "client reports what only the client can know" boundary DailyStepReward's own
    // doc comment draws for step counts).
    @Column(nullable = false, length = 16)
    val platform: String,

    @Column(name = "metadata_json", length = 512)
    val metadataJson: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", eventName = "", platform = "")
}
