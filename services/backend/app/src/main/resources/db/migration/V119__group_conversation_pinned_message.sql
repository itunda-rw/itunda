-- Real group-chat pin (rw.itunda.messaging.GroupMessagingService, 2026-07-26) -- closes
-- the "pin" half of docs/DESIGN_REFERENCES.md's Talk long-press-menu recommendation for
-- group chats; 1:1 conversations already had this (see V-whichever added
-- conversations.pinned_message_id).

ALTER TABLE group_conversations
    ADD COLUMN pinned_message_id VARCHAR(64) NULL AFTER last_message_at;
