-- Real 당근모임-style structured meetup fields (rw.itunda.community.CommunityService,
-- 2026-07-25) -- Karrot's own real product spun 모임 out of the freeform 같이해요 post
-- type specifically to add mandatory date-setting and a real capacity cap.

ALTER TABLE community_posts ADD COLUMN event_date DATETIME(6) NULL;
ALTER TABLE community_posts ADD COLUMN capacity INT NULL;
