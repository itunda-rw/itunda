-- Real Naver 지식iN (Knowledge iN)-style open-topic Q&A (rw.itunda.knowledge.KnowledgeService)
-- -- see KnowledgeQuestion.kt / KnowledgeAnswer.kt's own doc comments.

CREATE TABLE knowledge_questions (
    id                 VARCHAR(64)   NOT NULL PRIMARY KEY,
    asker_id           VARCHAR(64)   NOT NULL,
    category           VARCHAR(32)   NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    body               VARCHAR(4000) NOT NULL,
    adopted_answer_id  VARCHAR(64)   NULL,
    created_at         DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_knowledge_questions_asker_id ON knowledge_questions (asker_id);
CREATE INDEX idx_knowledge_questions_category ON knowledge_questions (category);

CREATE TABLE knowledge_answers (
    id            VARCHAR(64)   NOT NULL PRIMARY KEY,
    question_id   VARCHAR(64)   NOT NULL,
    answerer_id   VARCHAR(64)   NOT NULL,
    body          VARCHAR(4000) NOT NULL,
    is_adopted    TINYINT(1)    NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_knowledge_answers_question_id ON knowledge_answers (question_id);
CREATE INDEX idx_knowledge_answers_answerer_id ON knowledge_answers (answerer_id);
