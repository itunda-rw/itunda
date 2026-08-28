CREATE TABLE call_sessions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    caller_id VARCHAR(64) NOT NULL,
    callee_id VARCHAR(64) NOT NULL,
    call_type VARCHAR(16) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    answered_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    end_reason VARCHAR(16) NULL
);

CREATE INDEX idx_call_sessions_caller_id ON call_sessions (caller_id, started_at);
CREATE INDEX idx_call_sessions_callee_id ON call_sessions (callee_id, started_at);
