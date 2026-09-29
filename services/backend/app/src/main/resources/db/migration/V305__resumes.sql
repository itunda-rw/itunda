CREATE TABLE resumes (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    self_intro VARCHAR(2000) NULL,
    strengths VARCHAR(500) NULL,
    additional_info VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_resumes_user_id UNIQUE (user_id)
);

CREATE TABLE resume_experiences (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    resume_id VARCHAR(64) NOT NULL,
    company VARCHAR(200) NOT NULL,
    role VARCHAR(200) NOT NULL,
    period VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_resume_experiences_resume_id (resume_id)
);

CREATE TABLE resume_educations (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    resume_id VARCHAR(64) NOT NULL,
    school VARCHAR(200) NOT NULL,
    degree VARCHAR(100) NULL,
    major VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_resume_educations_resume_id (resume_id)
);

CREATE TABLE resume_certifications (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    resume_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    issued_date VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_resume_certifications_resume_id (resume_id)
);
