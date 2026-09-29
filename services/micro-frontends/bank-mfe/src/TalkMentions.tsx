// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4). Group-chat-only @mention
// composer helpers (mentions only make sense with 3+ participants, confirmed via
// usage grep -- GroupThread is their only real caller).

import { type GroupMember } from './lib/groupMessaging';

// Real @mention composer UI -- ports Android TalkScreen.kt's own identical addition
// (2026-08-04) to bank-mfe. GroupMessagingService.parseMentions (backend) already
// resolves `@FirstName` tokens against real group members purely from the message body
// text -- no separate mentionedUserIds field on the send request, so this composer
// only needs to insert the right text, not call any new endpoint. v1 scope matches the
// backend's own honest limitation (first-name collisions resolve to whichever member
// matches first): only suggests/inserts a plain `@FirstName` token, not a richer
// inline chip.
export function activeMentionQuery(draft: string): string | null {
  const at = draft.lastIndexOf('@');
  if (at === -1) return null;
  const tail = draft.slice(at + 1);
  if (tail.includes(' ') || tail.includes('\n')) return null;
  return tail;
}

export function applyMention(draft: string, memberName: string): string {
  const at = draft.lastIndexOf('@');
  if (at === -1) return draft;
  const firstName = memberName.trim().split(' ')[0];
  return draft.slice(0, at) + `@${firstName} `;
}

export function MentionSuggestions({ draft, members, currentUserId, onPick }: {
  draft: string; members: GroupMember[]; currentUserId: string | undefined; onPick: (name: string) => void;
}) {
  const query = activeMentionQuery(draft);
  if (query === null) return null;
  const matches = members.filter((m) => m.userId !== currentUserId && m.name.split(' ')[0].toLowerCase().startsWith(query.toLowerCase()));
  if (matches.length === 0) return null;
  return (
    <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '6px' }}>
      {matches.map((m) => {
        const firstName = m.name.split(' ')[0];
        return (
          <button
            key={m.userId}
            type="button"
            onClick={() => onPick(m.name)}
            style={{
              whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)',
            }}
          >
            @{firstName}
          </button>
        );
      })}
    </div>
  );
}
