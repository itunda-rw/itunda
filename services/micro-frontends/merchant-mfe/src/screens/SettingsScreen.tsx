import { useState } from 'react';
import { ApiError } from '../lib/api';
import { setWebhookUrl, type Merchant } from '../lib/merchant';

export default function SettingsScreen({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const [webhookUrl, setWebhookUrlInput] = useState(merchant.webhookUrl ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSubmitting(true);
    try {
      const updated = await setWebhookUrl(webhookUrl);
      onUpdated(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ maxWidth: '480px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{merchant.businessName}</h2>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '20px' }}>
        Merchant ID {merchant.id}
      </p>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Webhook URL</span>
          <input
            type="url"
            value={webhookUrl}
            onChange={(e) => setWebhookUrlInput(e.target.value)}
            placeholder="https://your-server.example.com/webhooks/itunda"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
          <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
            Real PAYMENT_STATUS_CHANGED events post here on every collection, retried up to 7 times over ~2.8 days.
          </span>
        </label>

        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
            {error}
          </p>
        )}
        {saved && !error && (
          <p style={{ fontSize: '13px', color: 'var(--toss-blue)', margin: 0 }}>Saved.</p>
        )}

        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Saving…' : 'Save'}
        </button>
      </form>
    </div>
  );
}
