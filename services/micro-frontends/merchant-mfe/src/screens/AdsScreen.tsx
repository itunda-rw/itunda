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
import { useI18n } from '../i18n/I18nContext';

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
// lib/merchant.ts's own doc comment. One real ad slot per merchant; a merchant's own
// registered location is required first, same real "pull, not push" reasoning
// MerchantAd.kt's own doc comment gives for why this can't match a stored customer
// coordinate. Customer-facing "ads near me" browse is a real, separate follow-up.
export default function AdsScreen() {
  const { t } = useI18n();
  const [merchant, setMerchant] = useState<Merchant | null | undefined>(undefined);
  const [ad, setAd] = useState<MerchantAd | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    getMyMerchant()
      .then(setMerchant)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('ads.loadError')));
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
          <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('ads.activeTitle')}</h2>
          <p style={{ fontSize: '14px', fontWeight: 700, marginTop: '8px' }}>{ad.title}</p>
          {ad.description && <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{ad.description}</p>}
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)', marginTop: '4px' }}>
            {t('ads.activeBody', { radius: ad.radiusMeters, date: new Date(ad.activeUntil).toLocaleDateString() })}
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
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleShare = () => {
    if (!navigator.geolocation) {
      setError(t('ads.geolocationUnsupported'));
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
            setError(err instanceof ApiError ? err.message : t('ads.locationSaveError'));
          });
      },
      () => {
        setBusy(false);
        setError(t('ads.locationFetchError'));
      },
    );
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('ads.locationSetupTitle')}</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        {t('ads.locationSetupBody')}
      </p>
      {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}
      <button className="itunda-btn itunda-btn-primary" onClick={handleShare} disabled={busy}>
        {busy ? t('ads.gettingLocation') : t('ads.shareLocationButton')}
      </button>
    </div>
  );
}

function CreateOrExtendAdCard({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [radiusMeters, setRadiusMeters] = useState(AD_VALID_RADII_METERS[0]);
  const [days, setDays] = useState(AD_DURATION_TIERS[0].days);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const selectedTier = AD_DURATION_TIERS.find((tier) => tier.days === days) ?? AD_DURATION_TIERS[0];

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
      setError(err instanceof ApiError ? err.message : t('ads.createError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '16px', fontWeight: 700 }}>{t('ads.createTitle')}</h2>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('ads.titleLabel')}</span>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={t('ads.titlePlaceholder')}
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('ads.descriptionLabel')}</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('ads.descriptionPlaceholder')}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <div style={{ display: 'flex', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('ads.radiusLabel')}</span>
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
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('ads.durationLabel')}</span>
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          >
            {AD_DURATION_TIERS.map((tier) => (
              <option key={tier.days} value={tier.days}>{t('ads.durationOption', { days: tier.days, price: tier.price.toLocaleString() })}</option>
            ))}
          </select>
        </label>
      </div>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', margin: 0 }}>
        {t('ads.chargeNotice', { price: selectedTier.price.toLocaleString() })}
      </p>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? t('ads.starting') : t('ads.payAndRunButton', { price: selectedTier.price.toLocaleString() })}
      </button>
    </form>
  );
}
