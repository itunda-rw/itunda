-- Real KakaoTalk @mention support (2026-07-25) -- closes docs/DESIGN_REFERENCES.md
-- Section 3 recommendation #7. Server-resolved against real group membership at send
-- time (never a client-asserted user id list) -- see GroupMessagingService.sendMessage's
-- own doc comment for the parsing/resolution discipline.
ALTER TABLE group_messages ADD COLUMN mentioned_user_ids VARCHAR(1000) NULL;
