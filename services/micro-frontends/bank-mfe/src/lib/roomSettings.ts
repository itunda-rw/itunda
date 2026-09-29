// Real per-room settings (itunda Talk redesign, 2026-08-28) -- theme/background and
// the room-lock flag are genuinely device-local even in real KakaoTalk, so this is
// plain localStorage, no backend call, matching lib/talk.ts's own honest scoping
// discipline (never fake a network round-trip for something that's real but local).

const THEME_KEY_PREFIX = 'itunda_talk_room_theme_';
const LOCK_KEY_PREFIX = 'itunda_talk_room_locked_';

// Real, existing itunda accent tokens only -- no invented background images/colors.
export const ROOM_THEMES = [
  { id: 'default', label: 'Default', color: null },
  { id: 'indigo', label: 'Indigo', color: 'var(--itunda-indigo-light)' },
  { id: 'green', label: 'Green', color: '#E6F7EF' },
  { id: 'grey', label: 'Grey', color: 'var(--itunda-grey-50)' },
] as const;
export type RoomThemeId = (typeof ROOM_THEMES)[number]['id'];

export const getRoomTheme = (conversationId: string): RoomThemeId =>
  (localStorage.getItem(THEME_KEY_PREFIX + conversationId) as RoomThemeId | null) ?? 'default';

export const setRoomTheme = (conversationId: string, theme: RoomThemeId) => {
  localStorage.setItem(THEME_KEY_PREFIX + conversationId, theme);
};

export const isRoomLocked = (conversationId: string): boolean =>
  localStorage.getItem(LOCK_KEY_PREFIX + conversationId) === '1';

export const setRoomLocked = (conversationId: string, locked: boolean) => {
  if (locked) localStorage.setItem(LOCK_KEY_PREFIX + conversationId, '1');
  else localStorage.removeItem(LOCK_KEY_PREFIX + conversationId);
};
