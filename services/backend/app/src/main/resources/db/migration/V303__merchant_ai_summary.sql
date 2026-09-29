-- Real self-hosted AI place summary (itunda Maps redesign, 2026-08-28, direct Naver
-- Map reference: the place-detail "AI 요약" tag). Generated offline/in-batch by
-- AiSummaryService, never live per-request.

ALTER TABLE merchants ADD COLUMN ai_summary VARCHAR(500) NULL;
ALTER TABLE merchants ADD COLUMN ai_summary_generated_at DATETIME(6) NULL;
