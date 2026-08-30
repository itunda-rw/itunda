// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4: "further internal decomposition
// of BankDashboard.tsx is the Toss-aligned move" -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2's own
// "Code that changes together lives together" rule). RSE stocks is a fully
// self-contained product vertical (own lib/stocks.ts data layer, own render tree,
// exactly one external call site -- `{tab === 'STOCKS' && <StocksView />}`) with
// zero shared state with the rest of BankDashboard.tsx, matching the safe staged
// extraction pattern the guidelines call for (move a cohesive, low-coupling piece
// first, zero behavior risk) rather than a risky whole-file rewrite.

import { useEffect, useState } from 'react';
import { TrendingDown, TrendingUp } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState, ErrorCard } from './EmptyState';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { Sparkline } from './Sparkline';
import { StockDetailSheet } from './StockDetailSheet';
import {
  fetchPortfolio, fetchPortfolioHistory, fetchStocks, fetchWatchlist, fundInvestmentAccount,
  type Portfolio, type PortfolioValuePoint, type Stock,
} from './lib/stocks';

// Real Investment-account top-up (2026-08-04) -- see lib/stocks.ts's own
// fundInvestmentAccount doc comment. Without this, a user with no pre-seeded
// investment balance had no in-app way to ever actually buy a stock.
function AddFundsCard({ onFunded }: { onFunded: () => void }) {
  const { t } = useI18n();
  const [expanded, setExpanded] = useState(false);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleFund = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError(null);
    setNeedsDeviceVerification(false);
    const value = Number(amount);
    if (!value || value <= 0) {
      setError('Enter a real amount.');
      return;
    }
    setBusy(true);
    try {
      await fundInvestmentAccount(value);
      setAmount('');
      setExpanded(false);
      onFunded();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10): dropped
  // itunda-card -- matches Android's identical AddFundsCard composable, already
  // flattened this session (InvestScreen.kt).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Investment cash</p>
        <button onClick={() => { setExpanded(!expanded); setError(null); }} style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
          {expanded ? 'Cancel' : 'Add funds'}
        </button>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Move money from your main account into your investment account.</p>
      {expanded && (
        <form onSubmit={handleFund} style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
          <input
            type="number" min="1" step="any" value={amount} onChange={(e) => setAmount(e.target.value)}
            placeholder="Amount (RWF)" required
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>
            {busy ? 'Working…' : 'Add'}
          </button>
        </form>
      )}
      {needsDeviceVerification ? (
        <div style={{ marginTop: '10px' }}>
          {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
              full account. handleFund resets needsDeviceVerification itself. */}
          <DeviceStepUpPrompt onVerified={() => handleFund()} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

export function StocksView() {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'MARKET' | 'PORTFOLIO' | 'WATCHLIST'>('MARKET');
  // Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- a market filter on
  // the existing Market browse, distinguishing the original 6 real RSE-domestic
  // symbols from the real US-listed names added 2026-08-01. See lib/stocks.ts's own
  // doc comment for the full sourced account.
  const [marketFilter, setMarketFilter] = useState<'ALL' | 'RSE' | 'NASDAQ'>('ALL');
  const [stocks, setStocks] = useState<Stock[] | null>(null);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [portfolioHistory, setPortfolioHistory] = useState<PortfolioValuePoint[] | null>(null);
  const [watchlist, setWatchlist] = useState<Stock[] | null>(null);
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadMarket = () => {
    setError(null);
    fetchStocks().then(setStocks).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  const loadPortfolio = () => {
    setError(null);
    Promise.all([fetchPortfolio(), fetchPortfolioHistory(30)])
      .then(([p, h]) => { setPortfolio(p); setPortfolioHistory(h); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  const loadWatchlist = () => {
    setError(null);
    fetchWatchlist().then(setWatchlist).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab === 'MARKET') loadMarket();
    else if (subTab === 'PORTFOLIO') loadPortfolio();
    else loadWatchlist();
    setSelectedStock(null);
  }, [subTab]);

  const watchedIds = new Set((watchlist ?? []).map((s) => s.id));

  const renderStockRow = (stock: Stock) => {
    const positive = stock.changePercent >= 0;
    return (
      <div
        key={stock.id}
        role="button"
        tabIndex={0}
        onClick={() => setSelectedStock(stock)}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSelectedStock(stock); } }}
        style={{ display: 'flex', alignItems: 'center', gap: '14px', padding: '12px 0', cursor: 'pointer' }}
      >
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
            {stock.symbol}
            <span style={{ fontSize: '10px', fontWeight: 700, color: 'var(--itunda-grey-500)', marginLeft: '6px' }}>{stock.market}</span>
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{stock.name}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{stock.price.toLocaleString()} RWF</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: positive ? 'var(--itunda-green)' : 'var(--itunda-red)', display: 'flex', alignItems: 'center', gap: '2px', justifyContent: 'flex-end' }}>
            {positive ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
            {positive ? '+' : ''}{stock.changePercent.toFixed(2)}%
          </p>
        </div>
      </div>
    );
  };

  if (selectedStock) {
    return (
      <StockDetailSheet
        stock={selectedStock}
        isWatched={watchedIds.has(selectedStock.id)}
        onClose={() => setSelectedStock(null)}
        onTraded={() => { loadPortfolio(); if (subTab === 'MARKET') loadMarket(); }}
        onWatchToggled={loadWatchlist}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {([{ id: 'MARKET', label: 'Market' }, { id: 'PORTFOLIO', label: 'Portfolio' }, { id: 'WATCHLIST', label: 'Watchlist' }] as const).map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setSubTab(id)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: subTab === id ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: subTab === id ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {error && (
        <ErrorCard message={error} onRetry={subTab === 'MARKET' ? loadMarket : subTab === 'PORTFOLIO' ? loadPortfolio : loadWatchlist} />
      )}

      {subTab === 'MARKET' && (
        stocks === null ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', gap: '6px', marginBottom: '4px' }}>
              {([{ id: 'ALL', label: 'All' }, { id: 'RSE', label: 'Rwanda (RSE)' }, { id: 'NASDAQ', label: 'Overseas' }] as const).map(({ id, label }) => (
                <button
                  key={id}
                  onClick={() => setMarketFilter(id)}
                  style={{
                    padding: '6px 12px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                    color: marketFilter === id ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                    backgroundColor: marketFilter === id ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
            {stocks.filter((s) => marketFilter === 'ALL' || s.market === marketFilter).map(renderStockRow)}
          </div>
        )
      )}

      {subTab === 'PORTFOLIO' && (
        portfolio === null ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : (
          // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- summary
          // section on a multi-section screen.
          <div>
            <div className="itunda-flat-section">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', fontWeight: 600 }}>Total value</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700, marginBottom: '4px' }}>{portfolio.totalValue.toLocaleString()} RWF</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: portfolio.totalReturn >= 0 ? 'var(--itunda-green)' : 'var(--itunda-red)', marginBottom: '12px' }}>
                {portfolio.totalReturn >= 0 ? '+' : ''}{portfolio.totalReturn.toLocaleString()} RWF ({portfolio.totalReturn >= 0 ? '+' : ''}{portfolio.totalReturnPercent.toFixed(2)}%)
              </p>
              {portfolioHistory && portfolioHistory.length > 0 && (
                <div>
                  <Sparkline values={portfolioHistory.map((h) => h.value)} positive={portfolio.totalReturn >= 0} />
                  <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                    Last 30 days -- based on your current holdings applied to real historical prices, not a full historical reconstruction
                  </p>
                </div>
              )}
            </div>
            <AddFundsCard onFunded={loadPortfolio} />
            {portfolio.holdings.length === 0 ? (
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', padding: '10px 0' }}>You don't hold any real shares yet. Browse the Market tab to buy some.</p>
            ) : (
              // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- entity
              // list (holdings owned), no divider needed, matching Android's identical
              // HoldingRow conversion this session (InvestScreen.kt).
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {portfolio.holdings.map((h) => (
                  <div key={h.stockId} style={{ padding: '10px 0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{h.symbol}</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{h.value.toLocaleString()} RWF</p>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{h.shares} shares @ {h.avgPrice.toLocaleString()} avg</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: h.return >= 0 ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>
                        {h.return >= 0 ? '+' : ''}{h.return.toFixed(2)}%
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      )}

      {subTab === 'WATCHLIST' && (
        watchlist === null ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : watchlist.length === 0 ? (
          // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          // conditional empty-state message.
          <EmptyState message="No stocks watched yet. Tap the star on any stock in the Market tab to follow it." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {watchlist.map(renderStockRow)}
          </div>
        )
      )}
    </div>
  );
}

