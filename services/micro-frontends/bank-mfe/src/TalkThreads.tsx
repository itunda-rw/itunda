import { useEffect, useState } from 'react';
import { Bot, Send } from 'lucide-react';
import { IconBack } from './icons/ItundaIcons';
import { EmptyState, ErrorCard } from './EmptyState';
import { ApiError } from './lib/api';
import { markNotificationRead } from './lib/notifications';
import { useDeferredLoading } from './useDeferredLoading';
import {
  AiChatBusyError, fetchAiChatHistory, fetchServiceChannel, sendAiChatMessage,
  type AiChatMessage, type ServiceChannelBubble,
} from './lib/talk';

// Real itunda service channel + AI chatbot thread views (itunda Talk redesign,
// 2026-08-28) -- split into their own file (not left in BankDashboard.tsx), same
// file-size-lint-driven convention BankCardChip.tsx/PayHomeExtras.tsx already
// established. Both are entered from a client-side-synthesized pinned row at the top
// of the conversation list -- there is deliberately no real backend conversation row
// for either (see ServiceChannelService/AiChatService's own doc comments).

function ThreadHeader({ title, subtitle, onBack }: { title: string; subtitle?: string; onBack: () => void }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
      <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversations">
        <IconBack size={20} />
      </button>
      <div>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{title}</h3>
        {subtitle && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{subtitle}</p>}
      </div>
    </div>
  );
}

export function TalkServiceChannelThread({ onBack }: { onBack: () => void }) {
  const [bubbles, setBubbles] = useState<ServiceChannelBubble[] | null>(null);
  const showSkeleton = useDeferredLoading(bubbles === null);
  const [error, setError] = useState<string | null>(null);
  const [bubblesPage, setBubblesPage] = useState(0);
  const [bubblesHasMore, setBubblesHasMore] = useState(false);
  const [loadingMoreBubbles, setLoadingMoreBubbles] = useState(false);

  const load = () => {
    setError(null);
    fetchServiceChannel(0)
      .then((r) => { setBubbles(r.bubbles); setBubblesPage(0); setBubblesHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load notifications.'));
  };
  useEffect(load, []);

  const loadMoreBubbles = () => {
    const nextPage = bubblesPage + 1;
    setLoadingMoreBubbles(true);
    fetchServiceChannel(nextPage)
      .then((r) => {
        setBubbles((prev) => [...(prev ?? []), ...r.bubbles]);
        setBubblesPage(nextPage);
        setBubblesHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreBubbles(false));
  };

  // Real mark-as-read on tap -- reuses the existing NotificationsCard's own
  // markNotificationRead call (a ServiceChannelBubble.id is a real Notification.id,
  // see ServiceChannelService's own doc comment). bank-mfe has no path-based router
  // (screens are a Tab enum + internal view state, not route strings) to wire
  // ctaRoute's real "/bank/transactions"-style values into yet -- that's a real,
  // separate follow-up, not faked here with a navigation call that goes nowhere.
  const handleTap = (bubble: ServiceChannelBubble) => {
    if (bubble.isRead) return;
    markNotificationRead(bubble.id).then(load).catch(() => {});
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <ThreadHeader title="itunda" subtitle="Real-time updates about your account" onBack={onBack} />
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {error && <ErrorCard message={error} onRetry={load} />}
        {!error && bubbles === null && showSkeleton && <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />}
        {!error && bubbles?.length === 0 && <EmptyState message="No updates yet." />}
        {bubbles?.map((bubble) => (
          <button
            key={bubble.id}
            type="button"
            onClick={() => handleTap(bubble)}
            style={{
              textAlign: 'left', background: 'var(--itunda-grey-50)', border: 'none', borderRadius: 'var(--itunda-radius-md)',
              padding: '12px 14px', cursor: 'pointer', opacity: bubble.isRead ? 0.7 : 1,
            }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{bubble.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', marginTop: '2px' }}>{bubble.body}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '6px' }}>
              {new Date(bubble.createdAt).toLocaleString()}
            </p>
          </button>
        ))}
        {bubblesHasMore && (
          <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreBubbles} onClick={loadMoreBubbles}>
            {loadingMoreBubbles ? 'Loading…' : 'Load more'}
          </button>
        )}
      </div>
    </div>
  );
}

function AiChatBubble({ message }: { message: AiChatMessage }) {
  const isAssistant = message.role === 'assistant';
  return (
    <div style={{ display: 'flex', justifyContent: isAssistant ? 'flex-start' : 'flex-end', gap: '6px' }}>
      <div
        style={{
          maxWidth: '78%', padding: '10px 14px', borderRadius: 'var(--itunda-radius-md)',
          backgroundColor: isAssistant ? 'var(--itunda-grey-100)' : 'var(--itunda-indigo)',
          color: isAssistant ? 'var(--itunda-grey-900)' : 'var(--itunda-white)',
        }}
      >
        {isAssistant && (
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)', marginBottom: '4px' }}>AI</p>
        )}
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', whiteSpace: 'pre-wrap' }}>{message.content}</p>
      </div>
    </div>
  );
}

export function TalkAiChatThread({ onBack }: { onBack: () => void }) {
  const [messages, setMessages] = useState<AiChatMessage[] | null>(null);
  const showSkeleton = useDeferredLoading(messages === null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  // Real single-flight AI model (see AiChatService's own doc comment) -- a genuine,
  // always-visible "busy" state, never a toast that disappears silently.
  const [busy, setBusy] = useState(false);
  // Real pagination fix (2026-09-11): backend returns newest-first (page 0 =
  // most recent), reversed here for oldest-at-top display. Older pages get
  // reversed the same way and PREPENDED once loaded, so scrollback grows
  // upward -- the standard "load older messages" chat pattern.
  const [historyPage, setHistoryPage] = useState(0);
  const [olderMessagesHasMore, setOlderMessagesHasMore] = useState(false);
  const [loadingOlderMessages, setLoadingOlderMessages] = useState(false);

  useEffect(() => {
    fetchAiChatHistory(0)
      .then((r) => { setMessages(r.messages.reverse()); setOlderMessagesHasMore(r.page + 1 < r.totalPages); })
      .catch(() => setMessages([]));
  }, []);

  const loadOlderMessages = () => {
    const nextPage = historyPage + 1;
    setLoadingOlderMessages(true);
    fetchAiChatHistory(nextPage)
      .then((r) => {
        setMessages((prev) => [...r.messages.reverse(), ...(prev ?? [])]);
        setHistoryPage(nextPage);
        setOlderMessagesHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingOlderMessages(false));
  };

  const send = () => {
    const text = draft.trim();
    if (!text || sending) return;
    setDraft('');
    setSending(true);
    setBusy(false);
    setMessages((prev) => [...(prev ?? []), { id: `pending_${Date.now()}`, userId: '', role: 'user', content: text, createdAt: new Date().toISOString() }]);
    sendAiChatMessage(text)
      .then((r) => setMessages((prev) => [...(prev ?? []).filter((m) => !m.id.startsWith('pending_')), r.message, r.reply]))
      .catch((err) => {
        setMessages((prev) => (prev ?? []).filter((m) => !m.id.startsWith('pending_')));
        if (err instanceof AiChatBusyError) setBusy(true);
      })
      .finally(() => setSending(false));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <ThreadHeader title="itunda AI" subtitle="Real self-hosted assistant, short answers only" onBack={onBack} />
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px', paddingBottom: '8px' }}>
        {messages === null && showSkeleton && <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />}
        {messages?.length === 0 && (
          <EmptyState message="Ask itunda AI anything about the app. Replies are short and come from a small self-hosted model." />
        )}
        {olderMessagesHasMore && (
          <button className="itunda-btn itunda-btn-secondary" disabled={loadingOlderMessages} onClick={loadOlderMessages}>
            {loadingOlderMessages ? 'Loading…' : 'Load older messages'}
          </button>
        )}
        {messages?.map((m) => <AiChatBubble key={m.id} message={m} />)}
        {busy && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-12-size)' }}>
            <Bot size={14} />
            itunda AI is busy right now — try again shortly.
          </div>
        )}
      </div>
      <div style={{ display: 'flex', gap: '8px', paddingTop: '8px' }}>
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') send(); }}
          placeholder="Message itunda AI"
          style={{ flex: 1, padding: '10px 14px', borderRadius: 'var(--itunda-radius-pill)', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button
          type="button"
          onClick={send}
          disabled={sending || !draft.trim()}
          aria-label="Send"
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center', width: '40px', height: '40px', borderRadius: '20px',
            border: 'none', backgroundColor: 'var(--itunda-indigo)', color: 'var(--itunda-white)', cursor: sending ? 'default' : 'pointer',
            opacity: sending || !draft.trim() ? 0.5 : 1,
          }}
        >
          <Send size={18} />
        </button>
      </div>
    </div>
  );
}
