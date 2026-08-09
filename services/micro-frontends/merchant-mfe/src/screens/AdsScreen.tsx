import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import {
  AD_DURATION_TIERS,
  AD_VALID_RADII_METERS,
  createOrExtendAd,
  fetchMyAd,
  getMyMerchant,
  setMerchantLocation,
  type Merchant,
  type MerchantAd,
} from '../lib/merchant';

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
// lib/merchant.ts's own doc comment. One real ad slot per merchant; a merchant's own
// registered location is required first, same real "pull, not push" reasoning
// MerchantAd.kt's own doc comment gives for why this can't match a stored customer
// coordinate. Customer-facing "ads near me" browse is a real, separate follow-up.
export default function AdsScreen() {
  const [merchant, setMerchant] = useState<Merchant | null | undefined>(undefined);
  const [ad, setAd] = useState<MerchantAd | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    getMyMerchant()
      .then(setMerchant)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your business account.'));
    fetchMyAd()
      .then(setAd)
      .catch(() => {});
  };

  useEffect(load, []);

  if (merchant === undefined) {
    return <div className="itunda-card skeleton" style={{ height: '160px' }} />;
  }

  if (error) {
    return (
      <div className="itunda-card">
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      </div>
    );
  }

  const hasLocation = merchant?.latitude != null && merchant?.longitude != null;

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {ad && new Date(ad.activeUntil).getTime() > Date.now() && (
        <div className="itunda-card">
          <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Your active ad</h2>
          <p style={{ fontSize: '14px', fontWeight: 700, marginTop: '8px' }}>{ad.title}</p>
          {ad.description && <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{ad.description}</p>}
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)', marginTop: '4px' }}>
            {ad.radiusMeters}m radius · runs until {new Date(ad.activeUntil).toLocaleDateString()}
          </p>
        </div>
      )}

      {!hasLocation ? (
        <LocationSetupCard onDone={load} />
      ) : (
        <CreateOrExtendAdCard onCreated={load} />
      )}
    </div>
  );
}

function LocationSetupCard({ onDone }: { onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleShare = () => {
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setBusy(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setMerchantLocation(position.coords.latitude, position.coords.longitude)
          .then(() => { setBusy(false); onDone(); })
          .catch((err) => {
            setBusy(false);
            setError(err instanceof ApiError ? err.message : 'Could not save your location.');
          });
      },
      () => {
        setBusy(false);
        setError('Could not get your real location. Check your browser permissions.');
      },
    );
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Set your business location</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        A radius-targeted ad needs your business's real location to match nearby customers.
      </p>
      {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}
      <button className="itunda-btn itunda-btn-primary" onClick={handleShare} disabled={busy}>
        {busy ? 'Getting location…' : 'Share my location'}
      </button>
    </div>
  );
}

function CreateOrExtendAdCard({ onCreated }: { onCreated: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [radiusMeters, setRadiusMeters] = useState(AD_VALID_RADII_METERS[0]);
  const [days, setDays] = useState(AD_DURATION_TIERS[0].days);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const selectedTier = AD_DURATION_TIERS.find((t) => t.days === days) ?? AD_DURATION_TIERS[0];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createOrExtendAd(title.trim(), description.trim() || undefined, radiusMeters, days);
      setTitle('');
      setDescription('');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this ad.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '16px', fontWeight: 700 }}>Run a local ad</h2>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Title</span>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Fresh bread every morning"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Description (optional)</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Stop by for 10% off this week"
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <div style={{ display: 'flex', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Radius</span>
          <select
            value={radiusMeters}
            onChange={(e) => setRadiusMeters(Number(e.target.value))}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          >
            {AD_VALID_RADII_METERS.map((r) => (
              <option key={r} value={r}>{r >= 1000 ? `${(r / 1000).toFixed(1)}km` : `${r}m`}</option>
            ))}
          </select>
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Duration</span>
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          >
            {AD_DURATION_TIERS.map((t) => (
              <option key={t.days} value={t.days}>{t.days} days — {t.price.toLocaleString()} RWF</option>
            ))}
          </select>
        </label>
      </div>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', margin: 0 }}>
        {selectedTier.price.toLocaleString()} RWF will be charged from your wallet. If you already have an active ad, this extends it.
      </p>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? 'Starting…' : `Pay ${selectedTier.price.toLocaleString()} RWF & run ad`}
      </button>
    </form>
  );
}
