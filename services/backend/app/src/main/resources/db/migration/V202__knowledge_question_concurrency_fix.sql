-- Real bug found live (2026-08-02): KnowledgeService.adoptAnswer already read/checked/
-- wrote KnowledgeQuestion.adoptedAnswerId in one transaction (the correct shape), but
-- with no @Version, two concurrent adopt calls could both pass the check before either
-- committed -- giving a question two "adopted" answers. See KnowledgeQuestion.kt's own
-- doc comment for the full account.

ALTER TABLE knowledge_questions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
