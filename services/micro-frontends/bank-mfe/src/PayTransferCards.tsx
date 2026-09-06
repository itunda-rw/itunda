import { useEffect, useState } from 'react';
import { EmptyState } from './EmptyState';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { useI18n } from './i18n/I18nContext';
import { type TranslationKey } from './i18n/translations';
import { ApiError } from './lib/api';
import { resolveRecipient, type P2pRecipientPreview } from './lib/p2p';
import {
  cancelScheduledTransfer, createScheduledTransfer, fetchMyScheduledTransfers, type ScheduledTransfer,
} from './lib/scheduledTransfers';
import {
  cancelDelayedTransfer, fetchMyDelayedTransfers, sendDelayed, type DelayedTransfer,
} from './lib/delayedTransfers';

const SCHEDULED_TRANSFER_STATUS_KEY: Record<ScheduledTransfer['status'], TranslationKey> = {
  PENDING: 'scheduledTransfers.statusScheduled',
  EXECUTED: 'scheduledTransfers.statusSent',
  CANCELLED: 'scheduledTransfers.statusCancelled',
  FAILED: 'scheduledTransfers.statusFailed',
};

// Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
// lib/scheduledTransfers.ts's own doc comment. Distinct from AutoTransfer (recurring,
// which itself still has no bank-mfe client anywhere -- left as its own separately
// named, still-deferred gap; not expanded in this pass).
export function ScheduledTransfersCard() {
  const { t } = useI18n();
  const [transfers, setTransfers] = useState<ScheduledTransfer[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [recipient, setRecipient] = useState('');
  const [amount, setAmount] = useState('');
  const [scheduledDate, setScheduledDate] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyScheduledTransfers().then(setTransfers).catch(() => {});
  };

  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = Number(amount);
    if (!recipient.trim() || !(parsedAmount > 0) || !scheduledDate) return;
    setBusy(true);
    setError(null);
    try {
      await createScheduledTransfer(recipient.trim(), parsedAmount, scheduledDate, description);
      setRecipient('');
      setAmount('');
      setScheduledDate('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('scheduledTransfers.createError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelScheduledTransfer(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('scheduledTransfers.cancelError'));
    } finally {
      setBusyId(null);
    }
  };

  const pending = (transfers ?? []).filter((tr) => tr.status === 'PENDING');
  const past = (transfers ?? []).filter((tr) => tr.status !== 'PENDING');
  const minDate = new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 10);

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('scheduledTransfers.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('scheduledTransfers.cancel') : t('scheduledTransfers.schedule')}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('scheduledTransfers.recipientPlaceholder')} value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder={t('scheduledTransfers.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="date" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} min={minDate} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder={t('scheduledTransfers.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('scheduledTransfers.scheduling') : t('scheduledTransfers.scheduleButton')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {pending.length === 0 && past.length === 0 && (
        <EmptyState message={t('scheduledTransfers.noTransfers')} />
      )}

      {[...pending, ...past.slice(0, 3)].map((tr) => (
        <div key={tr.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{tr.recipientName} · {tr.amount.toLocaleString('en-US')} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{tr.scheduledDate} · {t(SCHEDULED_TRANSFER_STATUS_KEY[tr.status])}</p>
          </div>
          {tr.status === 'PENDING' && (
            <button className="itunda-btn itunda-btn-secondary" disabled={busyId === tr.id} onClick={() => handleCancel(tr.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {busyId === tr.id ? '…' : t('scheduledTransfers.cancel')}
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

const DELAYED_TRANSFER_STATUS_KEY: Record<DelayedTransfer['status'], TranslationKey> = {
  PENDING: 'delayedTransfers.statusPending', COMPLETED: 'delayedTransfers.statusCompleted', CANCELLED: 'delayedTransfers.statusCancelled',
};

function formatReleaseCountdown(releaseAt: string): string {
  const ms = new Date(releaseAt).getTime() - Date.now();
  if (ms <= 0) return '';
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

// Real Korean 지연이체서비스 (Delayed Transfer Service) client -- see
// lib/delayedTransfers.ts's own doc comment for the full sourced account. Reuses
// resolveRecipient (lib/p2p.ts) the exact same way TransferFlow's own instant-send
// step already does, so the sender sees the real resolved account-holder name before
// committing here too -- the whole point of a "send safely" option is a real chance to
// catch a wrong recipient, so skipping that same confirmation here would defeat it.
export function DelayedTransfersCard() {
  const { t } = useI18n();
  const [transfers, setTransfers] = useState<DelayedTransfer[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [recipient, setRecipient] = useState('');
  const [recipientPreview, setRecipientPreview] = useState<P2pRecipientPreview | null>(null);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real device step-up (see TransferFlow's own handleConfirm) -- send-delayed is a
  // real money-moving endpoint carrying an Idempotency-Key header, so it's gated by
  // the same DeviceVerificationFilter every other one is.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real, honest v1 limitation: the backend's delayed-transfer list has no resolved
  // recipient name (P2pDelayedTransfer only stores recipientUserId) -- names resolved
  // at send time in THIS session are remembered here for display, a transfer loaded
  // fresh from a prior session honestly falls back to a generic label instead of
  // fabricating one.
  const [knownNames, setKnownNames] = useState<Record<string, string>>({});

  const load = () => {
    fetchMyDelayedTransfers().then(setTransfers).catch(() => {});
  };

  useEffect(load, []);

  useEffect(() => {
    const trimmed = recipient.trim();
    if (!trimmed) { setRecipientPreview(null); return; }
    const handle = setTimeout(() => {
      resolveRecipient(trimmed).then(setRecipientPreview).catch(() => setRecipientPreview(null));
    }, 400);
    return () => clearTimeout(handle);
  }, [recipient]);

  const handleCreate = async (e?: React.FormEvent) => {
    e?.preventDefault();
    const parsedAmount = Number(amount);
    if (!recipient.trim() || !(parsedAmount > 0)) return;
    setBusy(true);
    setError(null);
    try {
      const transfer = await sendDelayed(recipient.trim(), parsedAmount, description);
      if (recipientPreview) {
        setKnownNames((prev) => ({ ...prev, [transfer.recipientUserId]: recipientPreview.displayName }));
      }
      setRecipient('');
      setRecipientPreview(null);
      setAmount('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      // Real device step-up retries this exact same handleCreate call once verified,
      // same pattern as TransferFlow's own handleConfirm.
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('delayedTransfers.createError'));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelDelayedTransfer(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('delayedTransfers.cancelError'));
    } finally {
      setBusyId(null);
    }
  };

  const pending = (transfers ?? []).filter((tr) => tr.status === 'PENDING');
  const past = (transfers ?? []).filter((tr) => tr.status !== 'PENDING');

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('delayedTransfers.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('scheduledTransfers.cancel') : t('delayedTransfers.new')}
        </button>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>{t('delayedTransfers.subtitle')}</p>

      {showCreate && needsDeviceVerification && (
        <div style={{ marginBottom: '12px' }}>
          <DeviceStepUpPrompt onVerified={() => { setNeedsDeviceVerification(false); handleCreate(); }} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      )}

      {showCreate && !needsDeviceVerification && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('scheduledTransfers.recipientPlaceholder')} value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          {recipientPreview && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>{t('delayedTransfers.sendingTo')} {recipientPreview.displayName}</p>
          )}
          <input
            type="number" placeholder={t('scheduledTransfers.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder={t('scheduledTransfers.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('scheduledTransfers.scheduling') : t('delayedTransfers.sendButton')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {pending.length === 0 && past.length === 0 && (
        <EmptyState message={t('delayedTransfers.noTransfers')} />
      )}

      {[...pending, ...past.slice(0, 3)].map((tr) => (
        <div key={tr.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
              {knownNames[tr.recipientUserId] ?? t('delayedTransfers.recipientFallback')} · {tr.amount.toLocaleString('en-US')} RWF
            </p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
              {tr.status === 'PENDING'
                ? `${t('delayedTransfers.releasesIn')} ${formatReleaseCountdown(tr.releaseAt)}`
                : t(DELAYED_TRANSFER_STATUS_KEY[tr.status])}
            </p>
          </div>
          {tr.status === 'PENDING' && (
            <button className="itunda-btn itunda-btn-secondary" disabled={busyId === tr.id} onClick={() => handleCancel(tr.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {busyId === tr.id ? '…' : t('scheduledTransfers.cancel')}
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

