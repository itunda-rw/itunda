-- A job application has one terminal hiring decision.
ALTER TABLE job_applications
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
