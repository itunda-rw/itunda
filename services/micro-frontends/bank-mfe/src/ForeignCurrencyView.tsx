import { useEffect, useState } from 'react';
import { IconBell } from './icons/ItundaIcons';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { useCountUp } from './hooks/useCountUp';
import {
  clearRateAlert, convertCurrency, fetchExchangeRate, fetchMyCurrencyConversions, fetchMyForeignCurrencyAccounts, fetchMyRateAlerts, openForeignCurrencyAccount, setRateAlert,
  FOREIGN_CURRENCY_SUPPORTED, type CurrencyConversion, type ExchangeRateAlert, type ForeignCurrencyCode, type ForeignCurrencyAccount,
} from './lib/foreignCurrency';

// Real fix (2026-08-26): split out of BankDashboard.tsx once that file grew past
// its file-size-lint baseline. The real 토스뱅크 외화통장 (foreign-currency account)
// feature is fully self-contained -- own data fetching, own sub-components -- and
// only ever rendered from the FOREIGN_CURRENCY tab, matching this codebase's own
// established pattern of splitting standalone views into their own file (see
// PayHomeExtras.tsx/PinSetupCard.tsx/AccountDetailScreen.tsx).

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (item 154) -- see
// lib/foreignCurrency.ts's own doc comment.
export function ForeignCurrencyView() {
  const { t } = useI18n();
  const [accounts, setAccounts] = useState<ForeignCurrencyAccount[] | null>(null);
  const [conversions, setConversions] = useState<CurrencyConversion[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyForeignCurrencyAccounts()
      .then(setAccounts)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchMyCurrencyConversions().then(setConversions).catch(() => setConversions([]));
  };
  useEffect(load, []);

  if (accounts === null) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  const openCurrencies = new Set(accounts.map((w) => w.currency));
  const availableToOpen = FOREIGN_CURRENCY_SUPPORTED.filter((c) => !openCurrencies.has(c));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {accounts.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Open a USD, EUR, or GBP account to hold foreign currency and convert between it and RWF at a real live rate.
        </p>
      ) : (
        accounts.map((w) => <ForeignCurrencyAccountRow key={w.id} account={w} />)
      )}

      {availableToOpen.length > 0 && <OpenForeignAccountCard currencies={availableToOpen} onOpened={load} />}
      {accounts.length > 0 && <ConvertCurrencyCard accounts={accounts} onConverted={load} />}
      {accounts.length > 0 && <RateAlertCard accounts={accounts} />}

      {conversions.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Recent conversions</h3>
          {conversions.map((c) => (
            <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
              <p>{c.fromCurrency} → {c.toCurrency}</p>
              <p>{c.fromAmount.toLocaleString()} {c.fromCurrency} → {c.toAmount.toLocaleString()} {c.toCurrency}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Extracted so useCountUp -- see its own doc comment -- can be called once per
// real row rather than inside the parent's accounts.map() callback, which the
// Rules of Hooks forbid.
function ForeignCurrencyAccountRow({ account }: { account: ForeignCurrencyAccount }) {
  const animatedBalance = useCountUp(account.balance);
  return (
    <div style={{ padding: '12px 0' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{account.currency} account</p>
      <h2 style={{ fontSize: 'var(--itunda-type-scale-24-size)', fontWeight: 700 }}>{animatedBalance.toLocaleString()} {account.currency}</h2>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{account.accountNumber}</p>
    </div>
  );
}

function OpenForeignAccountCard({ currencies, onOpened }: { currencies: readonly ForeignCurrencyCode[]; onOpened: () => void }) {
  const { t } = useI18n();
  const [opening, setOpening] = useState<ForeignCurrencyCode | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleOpen = async (currency: ForeignCurrencyCode) => {
    setError(null);
    setOpening(currency);
    try {
      await openForeignCurrencyAccount(currency);
      onOpened();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setOpening(null);
    }
  };

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Open an account</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        {currencies.map((c) => (
          <button
            key={c}
            className="itunda-btn itunda-btn-secondary"
            style={{ flex: 1 }}
            disabled={opening !== null}
            onClick={() => handleOpen(c)}
          >
            {opening === c ? '…' : c}
          </button>
        ))}
      </div>
    </div>
  );
}

function ConvertCurrencyCard({ accounts, onConverted }: { accounts: ForeignCurrencyAccount[]; onConverted: () => void }) {
  const { t } = useI18n();
  const [direction, setDirection] = useState<'TO_FOREIGN' | 'TO_RWF'>('TO_FOREIGN');
  const [currency, setCurrency] = useState(accounts[0]?.currency ?? '');
  const [amount, setAmount] = useState('');
  const [rate, setRate] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CurrencyConversion | null>(null);

  const fromCurrency = direction === 'TO_FOREIGN' ? 'RWF' : currency;
  const toCurrency = direction === 'TO_FOREIGN' ? currency : 'RWF';

  useEffect(() => {
    if (!currency) return;
    fetchExchangeRate(fromCurrency, toCurrency).then((r) => setRate(r.rate)).catch(() => setRate(null));
  }, [fromCurrency, toCurrency, currency]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setResult(null);
    setSubmitting(true);
    try {
      const conversion = await convertCurrency(fromCurrency, toCurrency, Number(amount));
      setResult(conversion);
      setAmount('');
      onConverted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Convert</h3>
      <div style={{ display: 'flex', gap: '8px' }}>
        <select value={currency} onChange={(e) => setCurrency(e.target.value)} style={{ flex: 1, padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)' }}>
          {accounts.map((w) => <option key={w.currency} value={w.currency}>{w.currency}</option>)}
        </select>
        <select value={direction} onChange={(e) => setDirection(e.target.value as 'TO_FOREIGN' | 'TO_RWF')} style={{ flex: 1, padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)' }}>
          <option value="TO_FOREIGN">RWF → {currency}</option>
          <option value="TO_RWF">{currency} → RWF</option>
        </select>
      </div>
      <input
        type="number"
        min="0"
        step="0.01"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        placeholder={`Amount (${fromCurrency})`}
        required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
      />
      {rate !== null && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Live rate: 1 {fromCurrency} ≈ {rate.toFixed(4)} {toCurrency} (before itunda's 1.5% margin)</p>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      {result && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)', fontWeight: 700, margin: 0 }}>
          Converted {result.fromAmount.toLocaleString()} {result.fromCurrency} → {result.toAmount.toLocaleString()} {result.toCurrency}
        </p>
      )}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? 'Converting…' : 'Convert'}
      </button>
    </form>
  );
}

// Real Toss 외환 환율 알림 (exchange rate alert, section 121/168) -- see
// lib/foreignCurrency.ts's own doc comment for why this is the first client wiring
// for a backend feature that shipped fully with a live-verified-safe scheduler but
// zero callers anywhere.
function RateAlertCard({ accounts }: { accounts: ForeignCurrencyAccount[] }) {
  const { t } = useI18n();
  const [alerts, setAlerts] = useState<ExchangeRateAlert[] | null>(null);
  const [currency, setCurrency] = useState(accounts[0]?.currency ?? '');
  const [target, setTarget] = useState('');
  const [direction, setDirection] = useState<'ABOVE' | 'BELOW'>('ABOVE');
  const [expanded, setExpanded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => { fetchMyRateAlerts().then(setAlerts).catch(() => setAlerts([])); };
  useEffect(load, []);

  const alertFor = (c: string) => alerts?.find((a) => a.fromCurrency === 'RWF' ? a.toCurrency === c : a.fromCurrency === c);

  const handleSet = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const rate = Number(target);
    if (!rate || rate <= 0) { setError('Enter a real target rate.'); return; }
    setBusy(true);
    try {
      await setRateAlert('RWF', currency, rate, direction);
      setTarget('');
      setExpanded(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleClear = async (c: string) => {
    setBusy(true);
    try {
      await clearRateAlert('RWF', c);
      load();
    } catch {
      // Non-critical -- same "no error surfaced" convention as other clear actions here.
    } finally {
      setBusy(false);
    }
  };

  if (alerts === null) return null;

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Rate alerts</h3>
      {accounts.map((w) => {
        const a = alertFor(w.currency);
        return a ? (
          <div key={w.currency} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
                <IconBell size={13} style={{ verticalAlign: '-2px', marginRight: '4px' }} />
                RWF/{w.currency}: notify when {a.direction === 'ABOVE' ? '≥' : '≤'} {a.targetRate}
              </p>
              {a.alertTriggeredAt && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>Already triggered -- set a new target to re-arm it.</p>}
            </div>
            <button onClick={() => handleClear(w.currency)} disabled={busy} style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-red)' }}>Remove</button>
          </div>
        ) : null;
      })}
      {expanded ? (
        <form onSubmit={handleSet}>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
            <select value={currency} onChange={(e) => setCurrency(e.target.value)} style={{ flex: 1, padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)' }}>
              {accounts.map((w) => <option key={w.currency} value={w.currency}>RWF/{w.currency}</option>)}
            </select>
            <div style={{ display: 'flex', gap: '4px', padding: '4px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
              {(['ABOVE', 'BELOW'] as const).map((d) => (
                <button
                  key={d} type="button" onClick={() => setDirection(d)}
                  style={{
                    padding: '8px 12px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
                    color: direction === d ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                    backgroundColor: direction === d ? 'var(--itunda-indigo)' : 'transparent',
                  }}
                >
                  {d === 'ABOVE' ? 'Above' : 'Below'}
                </button>
              ))}
            </div>
          </div>
          <div style={{ display: 'flex', gap: '10px' }}>
            <input
              type="number" min="0.000001" step="any" value={target} onChange={(e) => setTarget(e.target.value)}
              placeholder={`Target rate (1 RWF = ? ${currency})`} required
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? 'Working…' : 'Set'}</button>
          </div>
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
        </form>
      ) : (
        <button onClick={() => setExpanded(true)} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
          <IconBell size={14} /> Set a rate alert
        </button>
      )}
    </div>
  );
}
