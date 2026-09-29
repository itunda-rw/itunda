import { useState } from 'react';
import { Send } from 'lucide-react';
import { broadcastNotification } from '../lib/queues';
import { ApiError } from '../lib/api';

const MAX_TITLE_LENGTH = 200;
const MAX_BODY_LENGTH = 1000;

export default function NotificationBroadcastView() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentCount, setSentCount] = useState<number | null>(null);

  const canSend = title.trim().length > 0 && body.trim().length > 0;

  const send = async () => {
    setSending(true);
    setError(null);
    try {
      const result = await broadcastNotification(title.trim(), body.trim());
      setSentCount(result.sentCount);
      setTitle('');
      setBody('');
      setConfirming(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this broadcast.');
      setConfirming(false);
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ maxWidth: '520px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>Send an announcement</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        Delivers a real in-app notification to every itunda user's inbox -- for outages, promotions, or other
        system-wide announcements. This can't be undone or unsent once confirmed.
      </p>
      {sentCount !== null && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-green)', marginBottom: '12px' }} role="status">
          Sent to {sentCount.toLocaleString()} user{sentCount === 1 ? '' : 's'}.
        </p>
      )}
      {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}
      <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '20px' }}>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value.slice(0, MAX_TITLE_LENGTH))}
          placeholder="Title"
          disabled={confirming}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }}
        />
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value.slice(0, MAX_BODY_LENGTH))}
          placeholder="Message"
          rows={4}
          disabled={confirming}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
        />
        {confirming && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
            Send this to every itunda user right now? This can't be undone.
          </p>
        )}
        <div style={{ display: 'flex', gap: '8px' }}>
          {confirming ? (
            <>
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={sending} onClick={() => setConfirming(false)}>
                Cancel
              </button>
              <button className="itunda-btn itunda-btn-primary" style={{ flex: 1, gap: '6px' }} disabled={sending} onClick={send}>
                <Send size={16} />
                {sending ? 'Sending…' : 'Confirm send'}
              </button>
            </>
          ) : (
            <button
              className="itunda-btn itunda-btn-primary"
              style={{ flex: 1, gap: '6px' }}
              disabled={!canSend}
              onClick={() => setConfirming(true)}
            >
              <Send size={16} />
              Send to all users
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
