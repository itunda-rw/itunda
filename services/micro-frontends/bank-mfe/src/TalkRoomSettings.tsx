import { useState } from 'react';
import { IconBack } from './icons/ItundaIcons';
import { getRoomTheme, ROOM_THEMES, setRoomLocked, setRoomTheme, type RoomThemeId } from './lib/roomSettings';
import type { Message } from './lib/messaging';

// Real per-room settings panel (itunda Talk redesign, 2026-08-28) -- split into its
// own file, same file-size-lint-driven convention BankCardChip.tsx already
// established. Scoped to 1:1 ConversationThread only this pass (group-thread parity
// is a real, separate follow-up, not silently promised here).
//
// Two capabilities this app genuinely cannot offer, documented honestly rather than
// faked:
// - Per-room notification sound: the browser Notification API has no per-tag custom-
//   sound support, unlike Android's per-channel sound or iOS's per-category sound.
// - Home-screen shortcut: this shell has no PWA manifest/service worker, so it isn't
//   actually installable -- promising a shortcut button here would fabricate a
//   capability that doesn't exist.

export function TalkRoomSettings({
  conversationId, otherUserName, messages, onBack,
}: { conversationId: string; otherUserName: string; messages: Message[]; onBack: () => void }) {
  const [theme, setTheme] = useState<RoomThemeId>(getRoomTheme(conversationId));
  const [locked, setLocked] = useState(false);

  const applyTheme = (id: RoomThemeId) => {
    setTheme(id);
    setRoomTheme(conversationId, id);
  };

  const applyLock = (value: boolean) => {
    setLocked(value);
    setRoomLocked(conversationId, value);
  };

  const exportChat = () => {
    const lines = messages.map((m) => `[${new Date(m.sentAt).toLocaleString()}] ${m.body}`);
    const text = `Chat with ${otherUserName}\n\n${lines.join('\n')}`;
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `itunda-talk-${otherUserName.replace(/\s+/g, '_')}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)', overflowY: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversation">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Room settings</h3>
      </div>

      <section style={{ marginBottom: '24px' }}>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>Theme</h4>
        <div style={{ display: 'flex', gap: '10px' }}>
          {ROOM_THEMES.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => applyTheme(t.id)}
              aria-label={t.label}
              style={{
                width: '40px', height: '40px', borderRadius: '20px', cursor: 'pointer',
                backgroundColor: t.color ?? 'var(--itunda-white)',
                border: theme === t.id ? '2px solid var(--itunda-indigo)' : '1px solid var(--itunda-grey-200)',
              }}
            />
          ))}
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '6px' }}>Stored on this device only.</p>
      </section>

      <section style={{ marginBottom: '24px' }}>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>Notification sound</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>
          Per-room sounds aren't available on web — the itunda Android and iOS apps support this.
        </p>
      </section>

      <section style={{ marginBottom: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Lock this chat</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Requires your password to open.</p>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={locked}
            aria-label="Lock this chat"
            onClick={() => applyLock(!locked)}
            style={{
              width: '44px', height: '26px', borderRadius: '13px', border: 'none', cursor: 'pointer', position: 'relative',
              backgroundColor: locked ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)',
            }}
          >
            <span style={{ position: 'absolute', top: '3px', left: locked ? '21px' : '3px', width: '20px', height: '20px', borderRadius: '10px', backgroundColor: 'var(--itunda-white)', transition: 'left 0.15s' }} />
          </button>
        </div>
      </section>

      <section>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>Export chat</h4>
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={exportChat}>
          Save as text file
        </button>
      </section>
    </div>
  );
}
