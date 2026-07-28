import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { ApiError } from '../lib/api';
import { fetchMyDevices, getOrCreateDeviceId, revokeDevice, type TrustedDevice } from '../lib/device';
import { broadcastToFollowers, fetchFollowerCount, getMyIdentitySubmissions, setCategory, setParticipatesInEatsMembership, setWebhookUrl, submitKyb, type IdentitySubmission, type Merchant } from '../lib/merchant';

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
      <EatsMembershipParticipationCard merchant={merchant} onUpdated={onUpdated} />
      <FollowersCard />
      <KybCard merchant={merchant} />
      <DevicesCard />
    </div>
  );
}

// Real Naver Smart Store-style "관심고객" (interested-customer) follower count +
// broadcast-to-followers (item 118) -- the merchant-owner-facing half of
// MerchantFollowService; the customer-facing follow/unfollow toggle already shipped
// on bank-mfe/Android/iOS (item 117). First client anywhere for this half, found via
// the same content-grep sweep that found item 117.
function FollowersCard() {
  const [count, setCount] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentCount, setSentCount] = useState<number | null>(null);

  const load = () => {
    fetchFollowerCount().then(setCount).catch(() => {
      // Real, non-critical -- a merchant not yet registered under this account just
      // sees a blank count rather than a hard error blocking the rest of Settings.
    });
  };
  useEffect(load, []);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSentCount(null);
    setSending(true);
    try {
      const recipients = await broadcastToFollowers(title, body);
      setSentCount(recipients);
      setTitle('');
      setBody('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this broadcast.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="toss-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Followers</h2>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        {count === null ? 'Loading…' : `${count} customer${count === 1 ? '' : 's'} following your store`}
      </p>
      <form onSubmit={handleSend} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Title</span>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="New arrivals this week"
            maxLength={100}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Message</span>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Tell your followers what's new."
            maxLength={500}
            required
            rows={3}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px', resize: 'vertical' }}
          />
        </label>
        <button type="submit" className="toss-btn toss-btn-primary" disabled={sending || count === 0}>
          {sending ? 'Sending…' : 'Broadcast to followers'}
        </button>
        {count === 0 && !error && (
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', margin: 0 }}>You need at least one follower to send a broadcast.</p>
        )}
        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
        )}
        {sentCount !== null && !error && (
          <p style={{ fontSize: '13px', color: 'var(--toss-blue)', margin: 0 }}>Sent to {sentCount} follower{sentCount === 1 ? '' : 's'}.</p>
        )}
      </form>
    </div>
  );
}

// Real device management (2026-07-28 port) -- the same self-service "your devices"
// control bank-mfe's own Devices tab and Android/iOS Settings already offer, backed
// by the same real GET/DELETE /api/v1/auth/devices endpoints. See lib/device.ts's own
// doc comment.
function DevicesCard() {
  const [devices, setDevices] = useState<TrustedDevice[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const myDeviceId = getOrCreateDeviceId();

  const load = () => {
    setError(null);
    fetchMyDevices().then(setDevices).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your devices.'));
  };
  useEffect(load, []);

  const handleRevoke = async (deviceId: string) => {
    setRevokingId(deviceId);
    setError(null);
    try {
      await revokeDevice(deviceId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this device.');
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="toss-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Devices</h2>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        Devices that have signed in to this account. A device must be verified before it can move money.
      </p>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{error}</p>
      )}
      {devices === null ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
      ) : devices.length === 0 ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No devices recorded yet.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {devices.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
              <div>
                <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
                  {d.deviceName ?? 'Unknown device'} {d.deviceId === myDeviceId && <span style={{ color: 'var(--toss-blue)' }}>(this device)</span>}
                </p>
                <p style={{ fontSize: '12px', color: d.trusted ? 'var(--toss-green)' : '#E53935' }}>
                  {d.trusted ? '✓ Verified — can move money' : '⚠ Not verified — sign-in only'}
                </p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>Last seen {new Date(d.lastSeenAt).toLocaleString()}</p>
              </div>
              <button
                className="toss-btn toss-btn-danger"
                disabled={revokingId === d.deviceId}
                onClick={() => handleRevoke(d.deviceId)}
                style={{ padding: '8px 12px', fontSize: '12px' }}
              >
                {revokingId === d.deviceId ? 'Removing…' : 'Remove'}
              </button>
            </div>
          ))}
        </div>
      )}
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

// Real Baemin Club (배민클럽)-style participating-restaurant opt-in (2026-07-26) -- first
// client UI for this endpoint (item 103). See EatsMembership.kt's own doc comment: free
// delivery for a buyer's Eats Club membership only applies when the restaurant has
// itself opted in here -- never a blanket waiver.
function EatsMembershipParticipationCard({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleToggle = async () => {
    setBusy(true);
    setError(null);
    try {
      const updated = await setParticipatesInEatsMembership(!merchant.participatesInEatsMembership);
      onUpdated(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Eats Club</h2>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            Offer free delivery to buyers with an active Eats Club membership.
          </p>
        </div>
        <button
          type="button"
          className={merchant.participatesInEatsMembership ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          disabled={busy}
          onClick={handleToggle}
          style={{ fontSize: '13px', padding: '8px 14px', whiteSpace: 'nowrap' }}
        >
          {busy ? '…' : merchant.participatesInEatsMembership ? 'Participating' : 'Opt in'}
        </button>
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: '8px 0 0' }} role="alert">
          {error}
        </p>
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
