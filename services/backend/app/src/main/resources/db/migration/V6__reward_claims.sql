-- Real rewards backend -- previously only existed as an unused REWARDS_EXPENSE ledger
-- account type and static demo copy in the discover module. The Saronite reward-tasks
-- mini-app's native bridge has called GET /rewards/tasks and POST /rewards/claim since it
-- was built, against endpoints that never existed (see docs/TOSS_PARITY_MATRIX.md's Rewards
-- row, corrected 2026-07-13). The claim-once guard is a real unique constraint, not just an
-- application-level check -- a race between two concurrent claims for the same task can't
-- both succeed.
CREATE TABLE reward_claims (
    id          VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64)    NOT NULL,
    task_id     VARCHAR(64)    NOT NULL,
    amount      DECIMAL(18, 2) NOT NULL,
    claimed_at  TIMESTAMP      NOT NULL,
    CONSTRAINT uq_reward_claims_user_task UNIQUE (user_id, task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_reward_claims_user_id ON reward_claims (user_id);
