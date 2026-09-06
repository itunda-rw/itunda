import { useEffect, useState } from 'react';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { useI18n } from './i18n/I18nContext';
import { type TranslationKey } from './i18n/translations';
import { ApiError } from './lib/api';
import { type LinkedAccount, fetchLinkedAccounts } from './lib/overview';
import {
  fetchMyP2pRequests, generateP2pRequest, payP2pRequest, type P2pPaymentRequestDto, type P2pPaymentRequestStatus,
} from './lib/p2p';
import {
  fetchAutoTopUpSetting, configureAutoTopUp, triggerAutoTopUp, type AutoTopUpSetting,
} from './lib/account';

const P2P_REQUEST_STATUS_KEY: Record<P2pPaymentRequestStatus, TranslationKey> = {
  PENDING: 'requestMoney.statusPending', COMPLETED: 'requestMoney.statusPaid', EXPIRED: 'requestMoney.statusExpired',
};

// Real fixed-amount person-to-person payment request (item 167) -- see lib/p2p.ts's
// own doc comment. A real 15-minute-expiring code the requester shares (typed/pasted,
// same real manual-code-entry convention MerchantController.collect's own bank-mfe
// client already established -- this app has no camera QR scanner anywhere); anyone
// who has the code can pay it directly, real account-to-account, no fee.
export function RequestMoneyCard() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<P2pPaymentRequestDto[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<P2pPaymentRequestDto | null>(null);
  const [payCode, setPayCode] = useState('');
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const load = () => {
    fetchMyP2pRequests().then(setRequests).catch(() => {});
  };
  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = Number(amount);
    if (!(parsedAmount > 0)) return;
    setCreating(true);
    setError(null);
    try {
      const req = await generateP2pRequest(parsedAmount, description.trim());
      setCreated(req);
      setAmount('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('requestMoney.createError'));
    } finally {
      setCreating(false);
    }
  };

  const handlePay = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setPaying(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await payP2pRequest(payCode.trim());
      setPayCode('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('requestMoney.payError'));
      }
    } finally {
      setPaying(false);
    }
  };

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('requestMoney.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('requestMoney.cancel') : t('requestMoney.newRequest')}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="number" placeholder={t('requestMoney.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder={t('requestMoney.whatsItFor')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={creating}>{creating ? t('requestMoney.creating') : t('requestMoney.createButton')}</button>
        </form>
      )}

      {created && (
        <div style={{ padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px', marginBottom: '12px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('requestMoney.shareCode')}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, fontFamily: 'monospace', wordBreak: 'break-all' }}>{created.id}</p>
        </div>
      )}

      {needsDeviceVerification ? (
        // Real fix (2026-08-10) -- see TransferFlow's own identical fix for the full
        // account. handlePay resets needsDeviceVerification itself.
        <DeviceStepUpPrompt onVerified={() => handlePay()} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <form onSubmit={handlePay} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('requestMoney.payCodePlaceholder')} value={payCode} onChange={(e) => setPayCode(e.target.value)} required
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={paying} style={{ padding: '10px 16px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {paying ? t('requestMoney.paying') : t('requestMoney.pay')}
          </button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {requests !== null && requests.length > 0 && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>{t('requestMoney.myRequests')}</p>
          {requests.slice(0, 5).map((r) => (
            <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{r.amount.toLocaleString('en-US')} RWF{r.description ? ` · ${r.description}` : ''}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{t(P2P_REQUEST_STATUS_KEY[r.status])}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168) -- see
// lib/account.ts's own doc comment.
export function AutoTopUpCard({ accountId }: { accountId: string }) {
  const { t } = useI18n();
  const [setting, setSetting] = useState<AutoTopUpSetting | null | undefined>(undefined);
  const [linkedAccounts, setLinkedAccounts] = useState<LinkedAccount[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [linkedAccountId, setLinkedAccountId] = useState('');
  const [thresholdAmount, setThresholdAmount] = useState('');
  const [topUpAmount, setTopUpAmount] = useState('');
  const [dailyTriggerCap, setDailyTriggerCap] = useState('3');
  const [busy, setBusy] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [triggerResult, setTriggerResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchAutoTopUpSetting(accountId)
      .then(setSetting)
      .catch(() => setSetting(null));
    fetchLinkedAccounts().then((accounts) => setLinkedAccounts(accounts.filter((a) => a.status === 'LINKED'))).catch(() => {});
  };
  useEffect(load, [accountId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const threshold = Number(thresholdAmount);
    const topUp = Number(topUpAmount);
    if (!linkedAccountId || !(threshold >= 0) || !(topUp > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await configureAutoTopUp(accountId, linkedAccountId, threshold, topUp, Number(dailyTriggerCap) || 3, true);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTopUp.saveError'));
    } finally {
      setBusy(false);
    }
  };

  const handleToggle = async () => {
    if (!setting) return;
    setBusy(true);
    setError(null);
    try {
      await configureAutoTopUp(accountId, setting.linkedAccountId, setting.thresholdAmount, setting.topUpAmount, setting.dailyTriggerCap, !setting.enabled);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTopUp.saveError'));
    } finally {
      setBusy(false);
    }
  };

  const handleTrigger = async () => {
    setTriggering(true);
    setTriggerResult(null);
    try {
      const r = await triggerAutoTopUp(accountId);
      setTriggerResult(r.reason);
      load();
    } catch (err) {
      setTriggerResult(err instanceof ApiError ? err.message : t('autoTopUp.checkError'));
    } finally {
      setTriggering(false);
    }
  };

  if (setting === undefined) return null;

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('autoTopUp.title')}</h3>
        {linkedAccounts.length > 0 && (
          <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowForm((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {showForm ? t('autoTopUp.cancel') : setting ? t('autoTopUp.edit') : t('autoTopUp.setUp')}
          </button>
        )}
      </div>

      {linkedAccounts.length === 0 && !setting && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('autoTopUp.linkFirst')}</p>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <select value={linkedAccountId} onChange={(e) => setLinkedAccountId(e.target.value)} required style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            <option value="">{t('autoTopUp.selectAccount')}</option>
            {linkedAccounts.map((a) => <option key={a.id} value={a.id}>{a.provider} · {a.externalAccountNumberMasked}</option>)}
          </select>
          <input type="number" placeholder={t('autoTopUp.thresholdPlaceholder')} value={thresholdAmount} onChange={(e) => setThresholdAmount(e.target.value)} min="0" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }} />
          <input type="number" placeholder={t('autoTopUp.topUpPlaceholder')} value={topUpAmount} onChange={(e) => setTopUpAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }} />
          <input type="number" placeholder={t('autoTopUp.maxPerDay')} value={dailyTriggerCap} onChange={(e) => setDailyTriggerCap(e.target.value)} min="1"
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }} />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('autoTopUp.saving') : t('autoTopUp.save')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {setting && !showForm && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {t('autoTopUp.summaryLine', { state: setting.enabled ? t('autoTopUp.on') : t('autoTopUp.off'), topUp: setting.topUpAmount.toLocaleString('en-US'), threshold: setting.thresholdAmount.toLocaleString('en-US') })}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
            {t('autoTopUp.upToPerDay', { cap: setting.dailyTriggerCap, count: setting.triggersToday })}
          </p>
          {triggerResult && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', marginBottom: '8px' }}>{triggerResult}</p>}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleToggle} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {setting.enabled ? t('autoTopUp.turnOff') : t('autoTopUp.turnOn')}
            </button>
            <button className="itunda-btn itunda-btn-secondary" disabled={triggering} onClick={handleTrigger} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {triggering ? t('autoTopUp.checking') : t('autoTopUp.checkNow')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// Real Coupang 정기배송 (subscribe & save) -- see lib/productSubscriptions.ts's own doc
// comment for the full sourced account. A minimal delivery-address prompt rather than a
// full address form, matching this pass's compact-card scope.
