-- Real KakaoTalk-style "pin chat room to top" (2026-08-17). See
-- ConversationPreference.pinned's own doc comment for the full account.
ALTER TABLE conversation_preferences ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
