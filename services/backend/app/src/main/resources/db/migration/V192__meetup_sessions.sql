-- Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in
-- (rw.itunda.community.CommunityService) -- see MeetupSession.kt / MeetupAttendance.kt's
-- own doc comments.

CREATE TABLE meetup_sessions (
    id            VARCHAR(64) NOT NULL PRIMARY KEY,
    post_id       VARCHAR(64) NOT NULL,
    sequence      INT         NOT NULL,
    scheduled_for DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_meetup_sessions_post_id ON meetup_sessions (post_id);

CREATE TABLE meetup_attendances (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    session_id     VARCHAR(64) NOT NULL,
    user_id        VARCHAR(64) NOT NULL,
    checked_in_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_meetup_attendances_session_user UNIQUE (session_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
