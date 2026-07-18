import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { ApiError } from '../lib/api';
import { getMyIdentitySubmissions, setCategory, setWebhookUrl, submitKyb, type IdentitySubmission, type Merchant } from '../lib/merchant';

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
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div className="toss-card">
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
              We'll notify this address every time a payment completes. If it doesn't respond, we'll keep retrying for about 3 days.
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

      <CategoryCard merchant={merchant} onUpdated={onUpdated} />
      <KybCard merchant={merchant} />
    </div>
  );
}

// Real category/cuisine (2026-07-19) -- lets a restaurant/shop set its own real
// category, which powers the buyer-side category chips + search/filter on bank-mfe's
// Eats tab (GET /api/v1/shopping/merchants?category=...&q=...).
function CategoryCard({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const [category, setCategoryInput] = useState(merchant.category ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSubmitting(true);
    try {
      const updated = await setCategory(category);
      onUpdated(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '12px' }}>Category</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px', alignItems: 'flex-end' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>e.g. Rwandan, Chinese, Bakery, Cafe</span>
          <input
            type="text"
            value={category}
            onChange={(e) => setCategoryInput(e.target.value)}
            placeholder="Category"
            required
            maxLength={64}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ height: '46px' }}>
          {submitting ? 'Saving…' : 'Save'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: '8px 0 0' }} role="alert">
          {error}
        </p>
      )}
      {saved && !error && (
        <p style={{ fontSize: '13px', color: 'var(--toss-blue)', margin: '8px 0 0' }}>Saved.</p>
      )}
    </div>
  );
}

// Real demo KYB structural pre-check (see DemoKybVerificationService.kt's own doc
// comment) -- not a real RDB/RRA registry lookup, but a real 9-digit-TIN structural
// validator plus a real human-review queue (the same ops-mfe Compliance queue personal
// KYC already uses), never auto-decided.
function KybCard({ merchant }: { merchant: Merchant }) {
  const [submissions, setSubmissions] = useState<IdentitySubmission[] | null>(null);
  const [tin, setTin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    getMyIdentitySubmissions()
      .then(setSubmissions)
      .catch(() => setSubmissions([]));
  };

  useEffect(load, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await submitKyb(tin);
      setTin('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit for KYB review.');
    } finally {
      setSubmitting(false);
    }
  };

  const latestKyb = submissions?.filter((s) => s.documentType === 'BUSINESS_TIN').sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0] ?? null;
  const pending = latestKyb?.status === 'PENDING';

  const autoCheckLabel: Record<string, string> = {
    MATCHED: 'Your TIN checked out automatically.',
    NOT_FOUND: "We couldn't find a match yet — a team member will take a look.",
    INVALID_FORMAT: 'The TIN format looked off — a team member will double-check it.',
  };

  return (
    <div className="toss-card">
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
        <ShieldCheck size={18} color={merchant.kybVerified ? 'var(--toss-green)' : 'var(--toss-grey-500)'} />
        <h2 style={{ fontSize: '16px', fontWeight: 700 }}>Business verification (KYB)</h2>
      </div>

      {merchant.kybVerified ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-green)', fontWeight: 600 }}>Verified</p>
      ) : pending ? (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)', marginBottom: '4px' }}>We're reviewing your business details.</p>
          {latestKyb?.autoVerificationStatus && (
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
              {autoCheckLabel[latestKyb.autoVerificationStatus] ?? 'A team member will take a look soon.'}
            </p>
          )}
        </div>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px', alignItems: 'flex-end', marginTop: '12px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Business TIN (9 digits)</span>
            <input
              type="text"
              inputMode="numeric"
              value={tin}
              onChange={(e) => setTin(e.target.value)}
              placeholder="123456789"
              required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ height: '46px' }}>
            {submitting ? 'Submitting…' : 'Submit'}
          </button>
        </form>
      )}
      {latestKyb?.status === 'REJECTED' && (
        <p style={{ fontSize: '12px', color: '#E53935', marginTop: '8px' }}>
          Previous submission was rejected{latestKyb.autoVerificationDetail ? `: ${latestKyb.autoVerificationDetail}` : '.'}
        </p>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: '8px 0 0' }} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
