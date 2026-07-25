-- Real structured job applications (2026-07-25) -- closes docs/DESIGN_REFERENCES.md
-- Section 4 recommendation #10. This session's 당근알바 (Karrot Jobs) research corrected
-- an unsourced assumption baked into the original JobPost/JobPostService doc comments:
-- Karrot's real official product page (daangn.com/kr/jobs/about) documents a three-step
-- flow -- 알바공고 작성하기 (post) -> 지원자 확인하기 (review applicants) -> 채팅으로
-- 약속 잡기 (chat) -- explicitly AFTER reviewing applicants, not as the application
-- mechanism itself. `contactPoster` (a bare DM) stays as-is for quick/informal posts;
-- this adds the missing structured review step alongside it.
CREATE TABLE job_applications (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    job_post_id    VARCHAR(64)  NOT NULL,
    applicant_id   VARCHAR(64)  NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    submitted_at   TIMESTAMP    NOT NULL,
    responded_at   TIMESTAMP    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_job_applications_job_post_id ON job_applications (job_post_id);
CREATE INDEX idx_job_applications_applicant_id ON job_applications (applicant_id);
