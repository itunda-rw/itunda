-- Real group photo/description -- see GroupMessagingService.setGroupPhotoUrl/
-- setGroupDescription's own doc comments. NULL means unset (no group photo/description
-- chosen yet), the pre-existing default for every current group.

ALTER TABLE group_conversations ADD COLUMN photo_url VARCHAR(2048) NULL;
ALTER TABLE group_conversations ADD COLUMN description VARCHAR(500) NULL;
