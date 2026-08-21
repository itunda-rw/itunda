import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { Badge } from '../components/Badge';
import { EmptyState } from '../components/EmptyState';
import { ApiError } from '../lib/api';
import { fetchMyDevices, getOrCreateDeviceId, revokeDevice, type TrustedDevice } from '../lib/device';
import { applyForFeeWaiver, broadcastToFollowers, fetchFollowerCount, generateApiKey, getMyIdentitySubmissions, getWebhookDeliveries, replayWebhookDelivery, setAcceptingOrders, setAcceptsScheduledOrders, setCashbackRate, setCategory, setClosedWeekdays, setMerchantAvgPrepTimeMinutes, setMerchantOpeningHours, setMerchantPhoneNumber, setMerchantPhotoUrl, setMerchantPickupDiscount, setMinOrderAmount, setParticipatesInEatsMembership, setWebhookUrl, submitKyb, type IdentitySubmission, type Merchant, type WebhookDelivery } from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

export default function SettingsScreen({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{merchant.businessName}</h2>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '20px' }}>
          {t('settings.merchantIdPrefix')} {merchant.id}
        </p>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.webhookUrlLabel')}</span>
            <input
              type="url"
              value={webhookUrl}
              onChange={(e) => setWebhookUrlInput(e.target.value)}
              placeholder="https://your-server.example.com/webhooks/itunda"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
            <span style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
              {t('settings.webhookUrlBody')}
            </span>
          </label>

          {error && (
            <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
              {error}
            </p>
          )}
          {saved && !error && (
            <p style={{ fontSize: '13px', color: 'var(--itunda-indigo)', margin: 0 }}>{t('settings.saved')}</p>
          )}

          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
            {submitting ? t('settings.saving') : t('settings.saveButton')}
          </button>
        </form>
      </div>

      <CategoryCard merchant={merchant} onUpdated={onUpdated} />
      <StoreSettingsCard merchant={merchant} onUpdated={onUpdated} />
      <EatsMembershipParticipationCard merchant={merchant} onUpdated={onUpdated} />
      <FeeWaiverCard merchant={merchant} onUpdated={onUpdated} />
      <FollowersCard />
      <KybCard merchant={merchant} />
      <ApiIntegrationCard />
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
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('settings.broadcastError'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('settings.followersTitle')}</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        {count === null ? t('settings.loading') : t(count === 1 ? 'settings.followersCountSingular' : 'settings.followersCountPlural', { count })}
      </p>
      <form onSubmit={handleSend} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.broadcastTitleLabel')}</span>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t('settings.broadcastTitlePlaceholder')}
            maxLength={100}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.broadcastMessageLabel')}</span>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder={t('settings.broadcastMessagePlaceholder')}
            maxLength={500}
            required
            rows={3}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px', resize: 'vertical' }}
          />
        </label>
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={sending || count === 0}>
          {sending ? t('settings.sending') : t('settings.broadcastButton')}
        </button>
        {count === 0 && !error && (
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', margin: 0 }}>{t('settings.broadcastNeedsFollower')}</p>
        )}
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
        )}
        {sentCount !== null && !error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-indigo)', margin: 0 }}>{t(sentCount === 1 ? 'settings.broadcastSentSingular' : 'settings.broadcastSentPlural', { count: sentCount })}</p>
        )}
      </form>
    </div>
  );
}

// Real API key + webhook delivery log/replay -- see lib/merchant.ts's own doc comment.
// The natural companion to the Webhook URL field above: generate a key to authenticate
// programmatic requests, and see whether the configured URL is actually receiving
// delivery attempts (with a Replay action for any exhausted -- all 7 real retries used
// up -- delivery).
function ApiIntegrationCard() {
  const { t } = useI18n();
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[] | null>(null);
  const [replayingId, setReplayingId] = useState<string | null>(null);
  const [deliveriesError, setDeliveriesError] = useState<string | null>(null);

  const loadDeliveries = () => {
    getWebhookDeliveries()
      .then(setDeliveries)
      .catch((err) => setDeliveriesError(err instanceof ApiError ? err.message : t('settings.webhookDeliveriesLoadError')));
  };
  useEffect(loadDeliveries, []);

  const handleGenerate = async () => {
    setGenerating(true);
    setGenerateError(null);
    try {
      setApiKey(await generateApiKey());
    } catch (err) {
      setGenerateError(err instanceof ApiError ? err.message : t('settings.apiKeyGenerateError'));
    } finally {
      setGenerating(false);
    }
  };

  const handleReplay = async (deliveryId: string) => {
    setReplayingId(deliveryId);
    setDeliveriesError(null);
    try {
      await replayWebhookDelivery(deliveryId);
      loadDeliveries();
    } catch (err) {
      setDeliveriesError(err instanceof ApiError ? err.message : t('settings.replayError'));
    } finally {
      setReplayingId(null);
    }
  };

  const statusColor: Record<WebhookDelivery['status'], string> = {
    DELIVERED: 'var(--itunda-green)',
    PENDING: 'var(--itunda-grey-500)',
    EXHAUSTED: 'var(--itunda-red)',
  };
  const statusLabelKey: Record<WebhookDelivery['status'], TranslationKey> = {
    DELIVERED: 'settings.statusDelivered',
    PENDING: 'settings.statusPending',
    EXHAUSTED: 'settings.statusExhausted',
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('settings.apiIntegrationTitle')}</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        {t('settings.apiIntegrationBody')}
      </p>

      <div style={{ marginBottom: '20px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" disabled={generating} onClick={handleGenerate}>
          {generating ? t('settings.generating') : t('settings.generateApiKeyButton')}
        </button>
        {apiKey && (
          <p style={{ fontSize: '12px', fontFamily: 'monospace', wordBreak: 'break-all', marginTop: '10px', padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '8px' }}>
            {apiKey}
          </p>
        )}
        {generateError && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{generateError}</p>
        )}
      </div>

      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>{t('settings.recentDeliveriesTitle')}</h3>
      {deliveriesError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{deliveriesError}</p>
      )}
      {deliveries === null ? (
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('settings.loading')}</p>
      ) : // Real copy-voice fix (item 244, round 6 of the empty-state pass): honest
      // that this is event-driven, not something to set up further here.
      deliveries.length === 0 ? (
        <EmptyState message={t('settings.deliveriesEmpty')} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {deliveries.slice(0, 20).map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{d.eventType}</p>
                <p style={{ fontSize: '12px', color: statusColor[d.status] }}>
                  {t(statusLabelKey[d.status])} · {t(d.attemptCount === 1 ? 'settings.attemptSingular' : 'settings.attemptPlural', { count: d.attemptCount })}
                </p>
                {d.lastError && <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{d.lastError}</p>}
              </div>
              {d.status === 'EXHAUSTED' && (
                <button
                  className="itunda-btn itunda-btn-secondary"
                  disabled={replayingId === d.id}
                  onClick={() => handleReplay(d.id)}
                  style={{ padding: '8px 12px', fontSize: '12px' }}
                >
                  {replayingId === d.id ? t('settings.replaying') : t('settings.replayButton')}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real device management (2026-07-28 port) -- the same self-service "your devices"
// control bank-mfe's own Devices tab and Android/iOS Settings already offer, backed
// by the same real GET/DELETE /api/v1/auth/devices endpoints. See lib/device.ts's own
// doc comment.
function DevicesCard() {
  const { t } = useI18n();
  const [devices, setDevices] = useState<TrustedDevice[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const myDeviceId = getOrCreateDeviceId();

  const load = () => {
    setError(null);
    fetchMyDevices().then(setDevices).catch((err) => setError(err instanceof ApiError ? err.message : t('settings.devicesLoadError')));
  };
  useEffect(load, []);

  const handleRevoke = async (deviceId: string) => {
    setRevokingId(deviceId);
    setError(null);
    try {
      await revokeDevice(deviceId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('settings.deviceRemoveError'));
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('settings.devicesTitle')}</h2>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        {t('settings.devicesBody')}
      </p>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>
      )}
      {devices === null ? (
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('settings.loading')}</p>
      ) : // Real copy-voice fix (item 244, round 6 of the empty-state pass): honest
      // that this is auto-recorded on sign-in, not a setup step to take here.
      devices.length === 0 ? (
        <EmptyState message={t('settings.devicesEmpty')} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {devices.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
              <div>
                <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
                  {d.deviceName ?? t('settings.unknownDevice')} {d.deviceId === myDeviceId && <span style={{ color: 'var(--itunda-indigo)' }}>{t('settings.thisDeviceSuffix')}</span>}
                </p>
                <p style={{ fontSize: '12px', color: d.trusted ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>
                  {d.trusted ? t('settings.deviceVerified') : t('settings.deviceNotVerified')}
                </p>
                <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{t('settings.lastSeenPrefix')} {new Date(d.lastSeenAt).toLocaleString()}</p>
              </div>
              <button
                className="itunda-btn itunda-btn-danger"
                disabled={revokingId === d.deviceId}
                onClick={() => handleRevoke(d.deviceId)}
                style={{ padding: '8px 12px', fontSize: '12px' }}
              >
                {revokingId === d.deviceId ? t('settings.removing') : t('settings.removeButton')}
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
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '12px' }}>{t('settings.categoryTitle')}</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px', alignItems: 'flex-end' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.categoryExamplesLabel')}</span>
          <input
            type="text"
            value={category}
            onChange={(e) => setCategoryInput(e.target.value)}
            placeholder={t('settings.categoryPlaceholder')}
            required
            maxLength={64}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ height: '46px' }}>
          {submitting ? t('settings.saving') : t('settings.saveButton')}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">
          {error}
        </p>
      )}
      {saved && !error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-indigo)', margin: '8px 0 0' }}>{t('settings.saved')}</p>
      )}
    </div>
  );
}

// Real photo/min-order/cashback-rate/scheduled-orders settings -- see
// MerchantService.setPhotoUrl/setMinOrderAmount/setCashbackRate/
// setAcceptsScheduledOrders's own doc comments on the backend. Found 2026-08-01 with
// zero client anywhere (not even here) despite each being real since ship day --
// a dead-endpoint sweep, not a dead-field-on-one-platform gap like this row's other
// entries.
function StoreSettingsCard({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const { t } = useI18n();
  const [photoUrl, setPhotoUrlInput] = useState(merchant.photoUrl ?? '');
  const [minOrderAmount, setMinOrderAmountInput] = useState(merchant.minOrderAmount != null ? String(merchant.minOrderAmount) : '');
  const [cashbackPercent, setCashbackPercentInput] = useState(merchant.cashbackRate != null ? String(merchant.cashbackRate * 100) : '');
  const [phoneNumber, setPhoneNumberInput] = useState(merchant.phoneNumber ?? '');
  const [openingHours, setOpeningHoursInput] = useState(merchant.openingHours ?? '');
  const [avgPrepTimeMinutes, setAvgPrepTimeMinutesInput] = useState(merchant.avgPrepTimeMinutes != null ? String(merchant.avgPrepTimeMinutes) : '');
  const [pickupDiscountPercent, setPickupDiscountPercentInput] = useState(merchant.pickupDiscountPercent != null ? String(merchant.pickupDiscountPercent) : '');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [scheduledBusy, setScheduledBusy] = useState(false);
  const [scheduledError, setScheduledError] = useState<string | null>(null);
  const [acceptingBusy, setAcceptingBusy] = useState(false);
  const [acceptingError, setAcceptingError] = useState<string | null>(null);
  const [closedWeekdaysBusy, setClosedWeekdaysBusy] = useState(false);
  const [closedWeekdaysError, setClosedWeekdaysError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSubmitting(true);
    try {
      let updated = await setMerchantPhotoUrl(photoUrl.trim());
      updated = await setMinOrderAmount(minOrderAmount.trim() === '' ? null : Number(minOrderAmount));
      const rate = cashbackPercent.trim() === '' ? null : Number(cashbackPercent) / 100;
      updated = await setCashbackRate(rate);
      updated = await setMerchantPhoneNumber(phoneNumber.trim() === '' ? null : phoneNumber.trim());
      updated = await setMerchantOpeningHours(openingHours.trim() === '' ? null : openingHours.trim());
      updated = await setMerchantAvgPrepTimeMinutes(avgPrepTimeMinutes.trim() === '' ? null : Number(avgPrepTimeMinutes));
      updated = await setMerchantPickupDiscount(pickupDiscountPercent.trim() === '' ? null : Number(pickupDiscountPercent));
      onUpdated(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleScheduledOrders = async () => {
    setScheduledBusy(true);
    setScheduledError(null);
    try {
      onUpdated(await setAcceptsScheduledOrders(!merchant.acceptsScheduledOrders));
    } catch (err) {
      setScheduledError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setScheduledBusy(false);
    }
  };

  // Real Baemin CEO app 영업일시중지 (temporarily pause business) (2026-08-16) -- see
  // MerchantService.setAcceptingOrders's own doc comment. Same real toggle shape as
  // handleToggleScheduledOrders above.
  const handleToggleAcceptingOrders = async () => {
    setAcceptingBusy(true);
    setAcceptingError(null);
    try {
      onUpdated(await setAcceptingOrders(!merchant.isAcceptingOrders));
    } catch (err) {
      setAcceptingError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setAcceptingBusy(false);
    }
  };

  // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule) (2026-08-16)
  // -- see MerchantService.setClosedWeekdays's own doc comment. Same real toggle-array
  // shape a day-of-week picker needs; weekdays are 1=Monday..7=Sunday.
  const closedWeekdaySet = new Set((merchant.closedWeekdays ?? '').split(',').filter(Boolean).map(Number));
  const handleToggleClosedWeekday = async (day: number) => {
    setClosedWeekdaysBusy(true);
    setClosedWeekdaysError(null);
    try {
      const next = new Set(closedWeekdaySet);
      if (next.has(day)) next.delete(day); else next.add(day);
      onUpdated(await setClosedWeekdays([...next]));
    } catch (err) {
      setClosedWeekdaysError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setClosedWeekdaysBusy(false);
    }
  };
  const weekdayLabels: { day: number; key: 'settings.weekdayMon' | 'settings.weekdayTue' | 'settings.weekdayWed' | 'settings.weekdayThu' | 'settings.weekdayFri' | 'settings.weekdaySat' | 'settings.weekdaySun' }[] = [
    { day: 1, key: 'settings.weekdayMon' }, { day: 2, key: 'settings.weekdayTue' }, { day: 3, key: 'settings.weekdayWed' },
    { day: 4, key: 'settings.weekdayThu' }, { day: 5, key: 'settings.weekdayFri' }, { day: 6, key: 'settings.weekdaySat' },
    { day: 7, key: 'settings.weekdaySun' },
  ];

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '12px' }}>{t('settings.storeSettingsTitle')}</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.storePhotoUrlLabel')}</span>
          <input
            type="url"
            value={photoUrl}
            onChange={(e) => setPhotoUrlInput(e.target.value)}
            placeholder="https://example.com/photo.jpg"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.minOrderAmountLabel')}</span>
          <input
            type="number"
            min="0"
            value={minOrderAmount}
            onChange={(e) => setMinOrderAmountInput(e.target.value)}
            placeholder="0"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.cashbackRateLabel')}</span>
          <input
            type="number"
            min="0"
            max="5"
            step="0.1"
            value={cashbackPercent}
            onChange={(e) => setCashbackPercentInput(e.target.value)}
            placeholder="0"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.phoneNumberLabel')}</span>
          <input
            type="tel"
            value={phoneNumber}
            onChange={(e) => setPhoneNumberInput(e.target.value)}
            placeholder="+250 788 123 456"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.openingHoursLabel')}</span>
          <input
            type="text"
            value={openingHours}
            onChange={(e) => setOpeningHoursInput(e.target.value)}
            placeholder="Mon-Sat 8:00-20:00"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.avgPrepTimeMinutesLabel')}</span>
          <input
            type="number"
            min="0"
            max="90"
            value={avgPrepTimeMinutes}
            onChange={(e) => setAvgPrepTimeMinutesInput(e.target.value)}
            placeholder="15"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.pickupDiscountPercentLabel')}</span>
          <input
            type="number"
            min="1"
            max="100"
            value={pickupDiscountPercent}
            onChange={(e) => setPickupDiscountPercentInput(e.target.value)}
            placeholder="10"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
        )}
        {saved && !error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-indigo)', margin: 0 }}>{t('settings.saved')}</p>
        )}
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? t('settings.saving') : t('settings.saveButton')}
        </button>
      </form>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '16px', paddingTop: '16px', borderTop: '1px solid var(--itunda-grey-200)' }}>
        <div>
          <p style={{ fontSize: '14px', fontWeight: 600 }}>{t('settings.acceptScheduledOrdersTitle')}</p>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('settings.acceptScheduledOrdersBody')}</p>
        </div>
        <button className="itunda-btn itunda-btn-secondary" disabled={scheduledBusy} onClick={handleToggleScheduledOrders}>
          {scheduledBusy ? '…' : merchant.acceptsScheduledOrders ? t('settings.on') : t('settings.off')}
        </button>
      </div>
      {scheduledError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">{scheduledError}</p>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '16px', paddingTop: '16px', borderTop: '1px solid var(--itunda-grey-200)' }}>
        <div>
          <p style={{ fontSize: '14px', fontWeight: 600 }}>{t('settings.acceptingOrdersTitle')}</p>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('settings.acceptingOrdersBody')}</p>
        </div>
        <button className="itunda-btn itunda-btn-secondary" disabled={acceptingBusy} onClick={handleToggleAcceptingOrders}>
          {acceptingBusy ? '…' : merchant.isAcceptingOrders ? t('settings.on') : t('settings.paused')}
        </button>
      </div>
      {acceptingError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">{acceptingError}</p>
      )}
      <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid var(--itunda-grey-200)' }}>
        <p style={{ fontSize: '14px', fontWeight: 600 }}>{t('settings.closedWeekdaysTitle')}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>{t('settings.closedWeekdaysBody')}</p>
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          {weekdayLabels.map(({ day, key }) => {
            const active = closedWeekdaySet.has(day);
            return (
              <button
                key={day}
                type="button"
                disabled={closedWeekdaysBusy}
                onClick={() => handleToggleClosedWeekday(day)}
                className={active ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                style={{ padding: '8px 12px', fontSize: '13px' }}
              >
                {t(key)}
              </button>
            );
          })}
        </div>
      </div>
      {closedWeekdaysError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">{closedWeekdaysError}</p>
      )}
    </div>
  );
}

// Real Baemin Club (배민클럽)-style participating-restaurant opt-in (2026-07-26) -- first
// client UI for this endpoint (item 103). See EatsMembership.kt's own doc comment: free
// delivery for a buyer's Eats Club membership only applies when the restaurant has
// itself opted in here -- never a blanket waiver.
function EatsMembershipParticipationCard({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleToggle = async () => {
    setBusy(true);
    setError(null);
    try {
      const updated = await setParticipatesInEatsMembership(!merchant.participatesInEatsMembership);
      onUpdated(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('settings.saveError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="itunda-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('settings.eatsClubTitle')}</h2>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            {t('settings.eatsClubBody')}
          </p>
        </div>
        <button
          type="button"
          className={merchant.participatesInEatsMembership ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          disabled={busy}
          onClick={handleToggle}
          style={{ fontSize: '13px', padding: '8px 14px', whiteSpace: 'nowrap' }}
        >
          {busy ? '…' : merchant.participatesInEatsMembership ? t('settings.eatsClubParticipating') : t('settings.eatsClubOptIn')}
        </button>
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

// Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
// see lib/merchant.ts's own applyForFeeWaiver doc comment. Eligibility (a real 30-day
// payment-volume threshold) is checked server-side; this card just surfaces the current
// state and lets an eligible merchant apply.
function FeeWaiverCard({ merchant, onUpdated }: { merchant: Merchant; onUpdated: (merchant: Merchant) => void }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const waived = merchant.feeRateOverride === 0;

  const handleApply = async () => {
    setBusy(true);
    setError(null);
    try {
      const updated = await applyForFeeWaiver();
      onUpdated(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('settings.feeWaiverError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="itunda-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('settings.feeWaiverTitle')}</h2>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            {waived ? t('settings.feeWaiverActiveBody') : t('settings.feeWaiverEligibleBody')}
          </p>
        </div>
        {!waived && (
          <button
            type="button" className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleApply}
            style={{ fontSize: '13px', padding: '8px 14px', whiteSpace: 'nowrap' }}
          >
            {busy ? '…' : t('settings.feeWaiverApplyButton')}
          </button>
        )}
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">
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
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('settings.kybSubmitError'));
    } finally {
      setSubmitting(false);
    }
  };

  const latestKyb = submissions?.filter((s) => s.documentType === 'BUSINESS_TIN').sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0] ?? null;
  const pending = latestKyb?.status === 'PENDING';

  const autoCheckLabelKey: Record<string, TranslationKey> = {
    MATCHED: 'settings.kybAutoMatched',
    NOT_FOUND: 'settings.kybAutoNotFound',
    INVALID_FORMAT: 'settings.kybAutoInvalidFormat',
  };

  return (
    <div className="itunda-card">
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
        <ShieldCheck size={18} color={merchant.kybVerified ? 'var(--itunda-green)' : 'var(--itunda-grey-500)'} />
        <h2 style={{ fontSize: '16px', fontWeight: 700 }}>{t('settings.kybTitle')}</h2>
      </div>

      {merchant.kybVerified ? (
        <Badge text={t('settings.kybVerifiedBadge')} tint="var(--itunda-green)" />
      ) : pending ? (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)', marginBottom: '4px' }}>{t('settings.kybReviewingBody')}</p>
          {latestKyb?.autoVerificationStatus && (
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
              {t(autoCheckLabelKey[latestKyb.autoVerificationStatus] ?? 'settings.kybAutoDefault')}
            </p>
          )}
        </div>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px', alignItems: 'flex-end', marginTop: '12px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('settings.kybTinLabel')}</span>
            <input
              type="text"
              inputMode="numeric"
              value={tin}
              onChange={(e) => setTin(e.target.value)}
              placeholder="123456789"
              required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          {/* Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11): "Submit"
              doesn't say what happens -- this starts a real verification process (see the
              autoCheckLabel/"team member will take a look" copy above), not an instant action. */}
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ height: '46px' }}>
            {submitting ? t('settings.kybSubmitting') : t('settings.kybSubmitButton')}
          </button>
        </form>
      )}
      {latestKyb?.status === 'REJECTED' && (
        <p style={{ fontSize: '12px', color: 'var(--itunda-red)', marginTop: '8px' }}>
          {latestKyb.autoVerificationDetail ? t('settings.kybRejectedWithDetail', { detail: latestKyb.autoVerificationDetail }) : t('settings.kybRejectedPlain')}
        </p>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '8px 0 0' }} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
