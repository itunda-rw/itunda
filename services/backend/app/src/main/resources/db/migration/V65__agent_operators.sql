CREATE TABLE agent_operators (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    agent_id   VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    is_active  BOOLEAN     NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_agent_operators_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_agent_operators_agent_id ON agent_operators (agent_id);
