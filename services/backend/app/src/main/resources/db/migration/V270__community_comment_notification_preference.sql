-- Real Karrot(당근마켓) 동네생활 "새 댓글 알림 끄기" (turn off new-comment
-- notifications) -- sourced from Karrot's own official support FAQ
-- (cs.kr.karrotmarket.com/wv/faqs/3106). See
-- CommunityNotificationPreference's own doc comment for the full account.
CREATE TABLE community_notification_preferences (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    comment_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_community_notification_preference_user UNIQUE (user_id)
);
