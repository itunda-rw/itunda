-- Real Toss 사기계좌 조회 (fraud-account lookup before transfer)-style scam report
-- (rw.itunda.p2p.ScamReportService, 2026-07-27). See ScamReport.kt's own doc comment
-- for the full sourced account and honest scope boundary.

CREATE TABLE scam_reports (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    reporter_id VARCHAR(64) NOT NULL,
    reported_identifier VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_scam_reports_identifier (reported_identifier),
    INDEX idx_scam_reports_reporter (reporter_id)
);
