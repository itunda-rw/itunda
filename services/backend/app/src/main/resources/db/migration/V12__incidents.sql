-- Real incident-response tooling -- previously nothing existed here at all (see
-- docs/TOSS_PARITY_MATRIX.md's Operations/Incidents row: SECURITY.md's runbooks are a
-- process document, not product surface). Opened automatically by IncidentDetector when a
-- real provider rail crosses a real failure threshold, not typed in by a human.
CREATE TABLE incidents (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    rail_id           VARCHAR(64)  NOT NULL,
    rail_display_name VARCHAR(255) NOT NULL,
    description       VARCHAR(255) NOT NULL,
    failure_count     INT          NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    opened_at         DATETIME(6)  NOT NULL,
    resolved_at       DATETIME(6)  NULL,
    resolved_by       VARCHAR(64)  NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_incidents_status ON incidents (status);
CREATE INDEX idx_incidents_rail_id ON incidents (rail_id);
