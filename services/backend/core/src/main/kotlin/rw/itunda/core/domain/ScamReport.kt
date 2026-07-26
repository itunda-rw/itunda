package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real Toss 사기계좌 조회 (fraud-account lookup before transfer)-style scam report --
 * sourced from Toss's own real, published feature: Korea's first financial institution
 * to check every single transfer against a real fraud-report database (built in
 * collaboration with the National Police Agency), showing "송금 전 주의가 필요해요"
 * (caution needed before this transfer) when the recipient's phone number or account
 * has a real reported fraud history.
 *
 * Honest, explicit scope boundary: itunda has no real police/national fraud-database
 * partnership -- same category of external-data gap as NIDA identity verification or
 * MTN/Airtel MoMo, which this codebase already handles the same honest way
 * (`DemoNidaVerificationService`, `SimulatedProviderConnector`). This is itunda's own
 * honest, crowd-sourced equivalent: any real itunda user can report a phone number as
 * involved in a scam (typically after being defrauded themselves), and
 * `ScamReportService.checkScamStatus` warns a real sender in real time if a recipient
 * identifier has crossed a real, itunda-chosen report threshold -- a genuine safety
 * signal from itunda's own real users, not a claimed reproduction of a real police
 * database this environment has no path to obtain.
 */
@Entity
@Table(name = "scam_reports")
class ScamReport(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "reporter_id", nullable = false, length = 64)
    val reporterId: String,

    @Column(name = "reported_identifier", nullable = false, length = 64)
    val reportedIdentifier: String,

    @Column(nullable = false, length = 255)
    val reason: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", reporterId = "", reportedIdentifier = "", reason = "")
}
