// Extracted from StocksView.tsx (2026-08-30) -- StocksView.tsx itself crossed the
// 500-line guideline right after being extracted from BankDashboard.tsx, so this
// piece (real buy/sell + Toss Securities 목표가 알림 price-alert UI) got its own
// second-stage extraction rather than a baseline bump, same "extract before it
// grows further" discipline docs/ARCHITECTURE_GUIDELINES.md §2 names.

import { useEffect, useState } from 'react';
import { TrendingDown, TrendingUp } from 'lucide-react';
import { IconBack, IconBell, IconStar } from './icons/ItundaIcons';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { Sparkline } from './Sparkline';
import {
  buyStock, clearPriceAlert, fetchPriceAlert, fetchStockHistory, sellStock, setPriceAlert, unwatchStock, watchStock,
  type PriceAlert, type PricePoint, type Stock,
} from './lib/stocks';
import { useDeferredLoading } from './useDeferredLoading';

export function StockDetailSheet({ stock, isWatched, onClose, onTraded, onWatchToggled }: {
  stock: Stock;
  isWatched: boolean;
  onClose: () => void;
  onTraded: () => void;
  onWatchToggled: () => void;
}) {
  const { t } = useI18n();
  const [history, setHistory] = useState<PricePoint[] | null>(null);
  const showSkeleton = useDeferredLoading(history === null);
  const [shares, setShares] = useState('');
  const [mode, setMode] = useState<'BUY' | 'SELL'>('BUY');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [watching, setWatching] = useState(isWatched);
  const [watchBusy, setWatchBusy] = useState(false);
  // Real device binding step-up (2026-07-21) -- Stocks buy/sell was a real gap:
  // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
  // showed only a generic error, same fix already applied to Transfer/Savings/Group
  // Account above.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- see
  // lib/stocks.ts's own doc comment for why this is the first client wiring for a
  // backend feature that shipped fully live-verified with zero callers.
  const [alert, setAlert] = useState<PriceAlert | null>(null);
  const [alertExpanded, setAlertExpanded] = useState(false);
  const [alertTarget, setAlertTarget] = useState('');
  const [alertDirection, setAlertDirection] = useState<'ABOVE' | 'BELOW'>('ABOVE');
  const [alertBusy, setAlertBusy] = useState(false);
  const [alertError, setAlertError] = useState<string | null>(null);

  useEffect(() => {
    fetchStockHistory(stock.id, 14).then(setHistory).catch(() => setHistory([]));
    fetchPriceAlert(stock.id).then(setAlert).catch(() => setAlert(null));
  }, [stock.id]);

  const handleSetAlert = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setAlertError(null);
    const target = Number(alertTarget);
    if (!target || target <= 0) {
      setAlertError('Enter a real target price.');
      return;
    }
    setAlertBusy(true);
    try {
      await setPriceAlert(stock.id, target, alertDirection);
      setAlert({ targetPrice: target, targetDirection: alertDirection, alertTriggeredAt: null });
      setAlertTarget('');
      setAlertExpanded(false);
      if (!watching) { setWatching(true); onWatchToggled(); }
    } catch (err) {
      setAlertError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setAlertBusy(false);
    }
  };

  const handleClearAlert = async () => {
    setAlertBusy(true);
    try {
      await clearPriceAlert(stock.id);
      setAlert({ targetPrice: null, targetDirection: null, alertTriggeredAt: null });
    } catch {
      // Non-critical -- same "no error surfaced" convention the watch toggle above uses.
    } finally {
      setAlertBusy(false);
    }
  };

  const handleTrade = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError(null);
    setNeedsDeviceVerification(false);
    const shareCount = Number(shares);
    if (!shareCount || shareCount <= 0) {
      setError('Enter a real number of shares.');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'BUY') await buyStock(stock.id, shareCount);
      else await sellStock(stock.id, shareCount);
      setShares('');
      onTraded();
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : `Could not ${mode === 'BUY' ? 'buy' : 'sell'} this stock.`);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleWatch = async () => {
    setWatchBusy(true);
    try {
      if (watching) {
        await unwatchStock(stock.id);
        setWatching(false);
      } else {
        await watchStock(stock.id);
        setWatching(true);
      }
      onWatchToggled();
    } catch {
      // Non-critical -- the star just doesn't flip, no error surfaced for a real
      // watch/unwatch toggle failure.
    } finally {
      setWatchBusy(false);
    }
  };

  const positive = stock.changePercent >= 0;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '4px' }}>
        <button onClick={onClose} style={{ color: 'var(--itunda-grey-500)', display: 'flex', padding: '4px' }} aria-label="Back">
          <IconBack size={18} />
        </button>
        <button onClick={handleToggleWatch} disabled={watchBusy} style={{ color: watching ? '#FFC107' : 'var(--itunda-grey-300)', display: 'flex', padding: '4px' }} aria-label="Toggle watch">
          <IconStar size={20} fill={watching ? '#FFC107' : 'none'} />
        </button>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', fontWeight: 600 }}>{stock.symbol} · {stock.marketCap}</p>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, marginBottom: '6px' }}>{stock.name}</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700, marginBottom: '4px' }}>{stock.price.toLocaleString('en-US')} RWF</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: positive ? 'var(--itunda-green)' : 'var(--itunda-red)', display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '16px' }}>
        {positive ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
        {positive ? '+' : ''}{stock.change.toLocaleString('en-US')} ({positive ? '+' : ''}{stock.changePercent.toFixed(2)}%) today
      </p>

      {history === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '48px', borderRadius: '8px', marginBottom: '16px' }} /> : null
      ) : history.length > 0 ? (
        <div style={{ marginBottom: '16px' }}>
          <Sparkline values={history.map((h) => h.price)} positive={positive} />
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>Last 14 days -- real deterministic simulation, not live RSE data</p>
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '12px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
        {(['BUY', 'SELL'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: mode === m ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === m ? (m === 'BUY' ? 'var(--itunda-indigo)' : 'var(--itunda-red)') : 'transparent',
            }}
          >
            {m === 'BUY' ? 'Buy' : 'Sell'}
          </button>
        ))}
      </div>
      <form onSubmit={handleTrade} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" min="0.0001" step="any" value={shares} onChange={(e) => setShares(e.target.value)}
          placeholder="Shares" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className={mode === 'BUY' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn'} style={mode === 'SELL' ? { backgroundColor: 'var(--itunda-red)', color: 'white' } : undefined} disabled={submitting}>
          {submitting ? 'Working…' : mode === 'BUY' ? 'Buy' : 'Sell'}
        </button>
      </form>
      {needsDeviceVerification ? (
        <div style={{ marginTop: '10px' }}>
          {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
              full account. handleTrade resets needsDeviceVerification itself. */}
          <DeviceStepUpPrompt onVerified={() => handleTrade()} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}

      {/* Real Toss Securities 목표가 알림 (target price alert, section 113/167). */}
      <div style={{ marginTop: '16px', paddingTop: '14px', borderTop: '1px solid var(--itunda-grey-100)' }}>
        {alert && alert.targetPrice != null ? (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
                <IconBell size={13} style={{ verticalAlign: '-2px', marginRight: '4px' }} />
                Alert set: notify when {alert.targetDirection === 'ABOVE' ? '≥' : '≤'} {alert.targetPrice.toLocaleString('en-US')} RWF
              </p>
              {alert.alertTriggeredAt && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>Already triggered -- set a new target to re-arm it.</p>}
            </div>
            <button onClick={handleClearAlert} disabled={alertBusy} style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-red)' }}>Remove</button>
          </div>
        ) : alertExpanded ? (
          <form onSubmit={handleSetAlert}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '8px' }}>Notify me when the price goes</p>
            <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '10px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
              {(['ABOVE', 'BELOW'] as const).map((d) => (
                <button
                  key={d} type="button" onClick={() => setAlertDirection(d)}
                  style={{
                    flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
                    color: alertDirection === d ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                    backgroundColor: alertDirection === d ? 'var(--itunda-indigo)' : 'transparent',
                  }}
                >
                  {d === 'ABOVE' ? 'Above' : 'Below'}
                </button>
              ))}
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <input
                type="number" min="0.01" step="any" value={alertTarget} onChange={(e) => setAlertTarget(e.target.value)}
                placeholder="Target price (RWF)" required
                style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
              />
              <button type="submit" className="itunda-btn itunda-btn-primary" disabled={alertBusy}>{alertBusy ? 'Working…' : 'Set'}</button>
            </div>
            {alertError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{alertError}</p>}
          </form>
        ) : (
          <button onClick={() => setAlertExpanded(true)} style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
            <IconBell size={14} /> Set a price alert
          </button>
        )}
      </div>
    </div>
  );
}
