// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// bill-pay/airtime client (own lib/bills.ts data layer, exactly one external call
// site -- `{tab === 'BILLS' && <BillsView />}`).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  fetchBillProviders, fetchPendingBills, payBill, buyAirtime, fetchAutoPaySettings, setAutoPay, clearAutoPay,
  type BillProvider, type PendingBill, type BillAutoPaySetting,
} from './lib/bills';


// Real bill-pay/airtime client -- see lib/bills.ts's own doc comment. Android/iOS
// already have this via the Saronite RN mini-app bridge; bank-mfe itself never had a
// screen for it despite the real, ledger-backed backend.
export function BillsView() {
  const { t } = useI18n();
  const [providers, setProviders] = useState<BillProvider[] | null>(null);
  const [pending, setPending] = useState<PendingBill[]>([]);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [airtimePhone, setAirtimePhone] = useState('');
  const [airtimeAmount, setAirtimeAmount] = useState('');
  const [airtimeProvider, setAirtimeProvider] = useState('');
  const [buyingAirtime, setBuyingAirtime] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  // Real Kakao Pay 자동납부 -- see lib/bills.ts's own doc comment.
  const [autoPaySettings, setAutoPaySettings] = useState<BillAutoPaySetting[]>([]);
  const [autoPayProviderId, setAutoPayProviderId] = useState('');
  const [autoPayAccount, setAutoPayAccount] = useState('');
  const [autoPayMax, setAutoPayMax] = useState('');
  const [savingAutoPay, setSavingAutoPay] = useState(false);
  const [clearingAutoPayId, setClearingAutoPayId] = useState<string | null>(null);

  const load = () => {
    fetchBillProviders().then(setProviders).catch(() => setProviders([]));
    fetchPendingBills().then(setPending).catch(() => setPending([]));
    fetchAutoPaySettings().then(setAutoPaySettings).catch(() => setAutoPaySettings([]));
  };
  useEffect(load, []);

  const handleSetAutoPay = async () => {
    const maxAmount = Number(autoPayMax);
    if (!autoPayProviderId || !autoPayAccount.trim() || !Number.isFinite(maxAmount) || maxAmount <= 0) {
      setError('Choose a biller, enter your account number, and a maximum amount greater than zero.');
      return;
    }
    setSavingAutoPay(true);
    setError(null);
    setMessage(null);
    try {
      await setAutoPay(autoPayProviderId, autoPayAccount.trim(), maxAmount);
      setMessage('Auto-pay set up — this bill will be paid automatically each cycle, up to your cap.');
      setAutoPayProviderId('');
      setAutoPayAccount('');
      setAutoPayMax('');
      fetchAutoPaySettings().then(setAutoPaySettings).catch(() => {});
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSavingAutoPay(false);
    }
  };

  const handleClearAutoPay = async (providerId: string) => {
    setClearingAutoPayId(providerId);
    setError(null);
    setMessage(null);
    try {
      await clearAutoPay(providerId);
      setAutoPaySettings((prev) => prev.filter((s) => s.providerId !== providerId));
      setMessage('Auto-pay turned off.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setClearingAutoPayId(null);
    }
  };

  const handlePay = async (bill: PendingBill) => {
    setPayingId(bill.id);
    setError(null);
    setMessage(null);
    try {
      const result = await payBill(bill.id, bill.amount, bill.accountNumber, bill.provider);
      setMessage(`Paid ${result.amount.toLocaleString()} RWF — ${result.referenceNumber}`);
      setPending((prev) => prev.filter((b) => b.id !== bill.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setPayingId(null);
    }
  };

  const handleBuyAirtime = async () => {
    const amount = Number(airtimeAmount);
    if (!airtimePhone.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real phone number and an amount greater than zero.');
      return;
    }
    setBuyingAirtime(true);
    setError(null);
    setMessage(null);
    try {
      const result = await buyAirtime(airtimePhone.trim(), amount, airtimeProvider || undefined);
      setMessage(`Sent ${result.amount.toLocaleString()} RWF airtime — ${result.referenceNumber}`);
      setAirtimePhone('');
      setAirtimeAmount('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBuyingAirtime(false);
    }
  };

  if (!providers) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="itunda-flat-section skeleton" style={{ height: '200px' }} />;
  }

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card wrapping -- 5
  // real sections shown together on one screen, now separated by
  // itunda-flat-section's own border-bottom divider instead of separate cards.
  return (
    <div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}

      {pending.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Pending bills</h3>
          {pending.map((b) => (
            <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{b.provider}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{b.accountNumber} · due {b.dueDate} · {b.amount.toLocaleString()} RWF</p>
              </div>
              <button className="itunda-btn itunda-btn-secondary" disabled={payingId === b.id} onClick={() => handlePay(b)}>
                {payingId === b.id ? '...' : 'Pay'}
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Auto-pay</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
          Register a bill once and it's paid automatically every cycle, up to the cap you set.
        </p>
        {autoPaySettings.filter((s) => s.active).map((s) => {
          const provider = providers.find((p) => p.id === s.providerId);
          return (
            <div key={s.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{provider ? `${provider.logo} ${provider.name}` : s.providerId}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{s.accountNumber} · up to {s.maxAmount.toLocaleString()} RWF</p>
              </div>
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={clearingAutoPayId === s.providerId}
                onClick={() => handleClearAutoPay(s.providerId)}
              >
                {clearingAutoPayId === s.providerId ? '...' : 'Turn off'}
              </button>
            </div>
          );
        })}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
          <select
            value={autoPayProviderId}
            onChange={(e) => setAutoPayProviderId(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          >
            <option value="">Choose a biller</option>
            {providers.filter((p) => p.category !== 'airtime').map((p) => (
              <option key={p.id} value={p.id}>{p.logo} {p.name}</option>
            ))}
          </select>
          <input
            placeholder="Account number"
            value={autoPayAccount}
            onChange={(e) => setAutoPayAccount(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          />
          <input
            type="number"
            placeholder="Maximum amount per bill (RWF)"
            value={autoPayMax}
            onChange={(e) => setAutoPayMax(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          />
          <button className="itunda-btn itunda-btn-secondary" disabled={savingAutoPay} onClick={handleSetAutoPay}>
            {savingAutoPay ? '...' : 'Turn on auto-pay'}
          </button>
        </div>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Buy airtime</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <input
            placeholder="Phone number"
            value={airtimePhone}
            onChange={(e) => setAirtimePhone(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          />
          <input
            type="number"
            placeholder="Amount (RWF)"
            value={airtimeAmount}
            onChange={(e) => setAirtimeAmount(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          />
          <select
            value={airtimeProvider}
            onChange={(e) => setAirtimeProvider(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          >
            <option value="">Default provider</option>
            {providers.filter((p) => p.category === 'airtime').map((p) => (
              <option key={p.id} value={p.name}>{p.logo} {p.name}</option>
            ))}
          </select>
          <button className="itunda-btn itunda-btn-secondary" disabled={buyingAirtime} onClick={handleBuyAirtime}>
            {buyingAirtime ? '...' : 'Buy airtime'}
          </button>
        </div>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>All billers</h3>
        {providers.map((p) => (
          <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <span>{p.logo} {p.name}</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{p.category}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
