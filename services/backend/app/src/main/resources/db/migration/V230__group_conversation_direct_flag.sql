-- Real 1:1-chat split-bill support (docs/DESIGN_REFERENCES.md Section 19) -- a synthetic
-- 2-person GroupConversation, created behind the scenes so SplitBillService's existing,
-- unmodified group logic can back a bill split between exactly two people who are
-- talking in a 1:1 conversation, not an actual named group. `is_direct = TRUE` marks
-- one of these -- GroupConversationRepository.findByMember now excludes them, so a
-- synthetic group never shows up in either person's real "My Groups" list, the concrete
-- problem that blocked this feature the last time it was investigated (see
-- GroupMessagingService.getOrCreateDirectSplitGroup's own doc comment for the full
-- design). FALSE is the pre-existing default for every real group created before this.

ALTER TABLE group_conversations ADD COLUMN is_direct BOOLEAN NOT NULL DEFAULT FALSE;
