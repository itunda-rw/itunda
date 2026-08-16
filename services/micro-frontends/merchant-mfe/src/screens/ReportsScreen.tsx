import { useCallback, useMemo, useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { getReport, getTopSellingProducts } from '../lib/merchant';
import { QueueError, QueueSkeleton } from '../QueueState';
import { useI18n } from '../i18n/I18nContext';

// `<input type="date">` is a calendar-date control, so preserve the merchant's
// local calendar day rather than converting (and potentially shifting it) to UTC.
const isoDate = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

function rangeEndingToday(days: number) {
  const end = new Date();
  const start = new Date(end);
  start.setDate(start.getDate() - (days - 1));
  return { from: isoDate(start), to: isoDate(end) };
}

export default function ReportsScreen() {
  const { t } = useI18n();
  const initialRange = rangeEndingToday(7);
  const [from, setFrom] = useState(initialRange.from);
  const [to, setTo] = useState(initialRange.to);
  const [draftFrom, setDraftFrom] = useState(initialRange.from);
  const [draftTo, setDraftTo] = useState(initialRange.to);
  const [validationError, setValidationError] = useState<string | null>(null);

  // This callback is deliberately memoized. useQueue reloads when its fetcher changes,
  // which is exactly what applying a different report range should do.
  const fetchReport = useCallback(() => getReport(from, to).then((report) => report.days), [from, to]);
  const { items, error, refreshing, reload } = useQueue(fetchReport);

  // Real Coupang WING-style best-selling-products report (2026-08-16) -- same range,
  // fetched separately since it's a distinct backend aggregation, not derived from
  // `items` above (daily collection totals carry no product-level breakdown at all).
  const fetchTopProducts = useCallback(() => getTopSellingProducts(from, to).then((r) => r.products), [from, to]);
  const { items: topProducts, reload: reloadTopProducts } = useQueue(fetchTopProducts);

  const applyRange = (nextFrom = draftFrom, nextTo = draftTo) => {
    if (!nextFrom || !nextTo) {
      setValidationError(t('reports.validationBothDates'));
      return;
    }
    const start = new Date(`${nextFrom}T00:00:00Z`);
    const end = new Date(`${nextTo}T00:00:00Z`);
    if (start > end) {
      setValidationError(t('reports.validationStartBeforeEnd'));
      return;
    }
    if ((end.getTime() - start.getTime()) / 86_400_000 > 30) {
      setValidationError(t('reports.validationMaxRange'));
      return;
    }
    setValidationError(null);
    setFrom(nextFrom);
    setTo(nextTo);
  };

  const selectPreset = (days: number) => {
    const range = rangeEndingToday(days);
    setDraftFrom(range.from);
    setDraftTo(range.to);
    applyRange(range.from, range.to);
  };

  const totals = useMemo(() => {
    if (!items) return null;
    const channels: Record<string, number> = {};
    let collections = 0;
    for (const day of items) {
      collections += day.collectionCount;
      for (const [channel, count] of Object.entries(day.byChannel)) channels[channel] = (channels[channel] ?? 0) + count;
    }
    return {
      collections,
      gross: items.reduce((sum, day) => sum + day.grossAmount, 0),
      fees: items.reduce((sum, day) => sum + day.fees, 0),
      net: items.reduce((sum, day) => sum + day.netAmount, 0),
      channels: Object.entries(channels).sort(([, a], [, b]) => b - a),
    };
  }, [items]);

  if (error) return <QueueError message={error} onRetry={reload} />;
  if (items === null || totals === null) return <QueueSkeleton />;

  const rangeLabel = `${from} ${t('reports.rangeSeparator')} ${to}`;
  const presetKey: Record<number, 'reports.last7Days' | 'reports.last30Days'> = { 7: 'reports.last7Days', 30: 'reports.last30Days' };
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px', marginBottom: '16px', flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ fontSize: '20px', fontWeight: 700 }}>{t('reports.title')}</h2>
          <p style={{ color: 'var(--itunda-grey-500)', fontSize: '13px', marginTop: '4px' }}>{rangeLabel} · {t('reports.settledSuffix')}</p>
        </div>
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '8px 14px' }} disabled={refreshing} onClick={() => { reload(); reloadTopProducts(); }}>
          {t('reports.refresh')}
        </button>
      </div>

      <form
        className="itunda-card"
        onSubmit={(event) => { event.preventDefault(); applyRange(); }}
        style={{ display: 'flex', gap: '12px', alignItems: 'end', flexWrap: 'wrap' }}
      >
        <div style={{ display: 'flex', gap: '8px' }} aria-label="Report period shortcuts">
          {[7, 30].map((days) => (
            <button key={days} type="button" className="itunda-btn itunda-btn-secondary" style={{ padding: '8px 12px' }} onClick={() => selectPreset(days)}>
              {t(presetKey[days])}
            </button>
          ))}
        </div>
        <label style={{ display: 'grid', gap: '5px', fontSize: '13px', fontWeight: 600 }}>
          {t('reports.fromLabel')}
          <input type="date" value={draftFrom} max={draftTo} onChange={(event) => setDraftFrom(event.target.value)} />
        </label>
        <label style={{ display: 'grid', gap: '5px', fontSize: '13px', fontWeight: 600 }}>
          {t('reports.toLabel')}
          <input type="date" value={draftTo} min={draftFrom} max={isoDate(new Date())} onChange={(event) => setDraftTo(event.target.value)} />
        </label>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ padding: '9px 14px' }}>{t('reports.apply')}</button>
        {validationError && <p role="alert" style={{ width: '100%', fontSize: '13px', color: 'var(--itunda-red)' }}>{validationError}</p>}
      </form>

      <div className="itunda-card" style={{ display: 'flex', gap: '32px', flexWrap: 'wrap' }}>
        <Metric label={t('reports.metricCollections')} value={totals.collections.toLocaleString()} />
        <Metric label={t('reports.metricGross')} value={`${totals.gross.toLocaleString()} RWF`} />
        <Metric label={t('reports.metricFees')} value={`${totals.fees.toLocaleString()} RWF`} />
        <Metric label={t('reports.metricNet')} value={`${totals.net.toLocaleString()} RWF`} highlighted />
      </div>

      <div className="itunda-card">
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>{t('reports.channelsTitle')}</h3>
        {totals.channels.length === 0 ? (
          <p style={{ color: 'var(--itunda-grey-500)', fontSize: '14px' }}>{t('reports.channelsEmpty')}</p>
        ) : totals.channels.map(([channel, count]) => (
          <div key={channel} style={{ display: 'grid', gridTemplateColumns: '88px 1fr auto', gap: '10px', alignItems: 'center', marginTop: '10px', fontSize: '14px' }}>
            <span style={{ fontWeight: 600 }}>{channel.replace('_', ' ')}</span>
            <div aria-hidden="true" style={{ height: '8px', borderRadius: '99px', background: 'var(--itunda-grey-200)', overflow: 'hidden' }}>
              <div style={{ width: `${(count / totals.collections) * 100}%`, height: '100%', background: 'var(--itunda-blue)', borderRadius: 'inherit' }} />
            </div>
            <span style={{ color: 'var(--itunda-grey-500)' }}>{count} ({Math.round((count / totals.collections) * 100)}%)</span>
          </div>
        ))}
      </div>

      <div className="itunda-card">
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>{t('reports.topProductsTitle')}</h3>
        {topProducts === null ? (
          <div className="itunda-card skeleton" style={{ height: '60px' }} />
        ) : topProducts.length === 0 ? (
          <p style={{ color: 'var(--itunda-grey-500)', fontSize: '14px' }}>{t('reports.topProductsEmpty')}</p>
        ) : (
          <div style={{ overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px', minWidth: '480px' }}>
              <thead><tr style={{ backgroundColor: 'var(--itunda-grey-100)' }}>
                {(['reports.columnProduct', 'reports.columnUnits', 'reports.columnRevenue'] as const).map((headingKey, index) => <th key={headingKey} style={{ textAlign: index === 0 ? 'left' : 'right', padding: '10px 14px' }}>{t(headingKey)}</th>)}
              </tr></thead>
              <tbody>{topProducts.map((p) => (
                <tr key={p.productId} style={{ borderTop: '1px solid var(--itunda-grey-200)' }}>
                  <td style={{ padding: '10px 14px' }}>{p.productName}</td>
                  <td style={{ textAlign: 'right', padding: '10px 14px' }}>{p.unitsSold.toLocaleString()}</td>
                  <td style={{ textAlign: 'right', padding: '10px 14px', fontWeight: 600 }}>{p.revenue.toLocaleString()} RWF</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </div>

      <div className="itunda-card" style={{ padding: 0, overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px', minWidth: '640px' }}>
          <thead><tr style={{ backgroundColor: 'var(--itunda-grey-100)' }}>
            {(['reports.columnDate', 'reports.columnCollections', 'reports.columnGross', 'reports.columnFees', 'reports.columnNet'] as const).map((headingKey, index) => <th key={headingKey} style={{ textAlign: index === 0 ? 'left' : 'right', padding: '12px 16px' }}>{t(headingKey)}</th>)}
          </tr></thead>
          <tbody>{items.map((day) => (
            <tr key={day.date} style={{ borderTop: '1px solid var(--itunda-grey-200)' }}>
              <td style={{ padding: '12px 16px' }}>{day.date}</td><td style={{ textAlign: 'right', padding: '12px 16px' }}>{day.collectionCount}</td>
              <td style={{ textAlign: 'right', padding: '12px 16px' }}>{day.grossAmount.toLocaleString()}</td><td style={{ textAlign: 'right', padding: '12px 16px' }}>{day.fees.toLocaleString()}</td>
              <td style={{ textAlign: 'right', padding: '12px 16px', fontWeight: 600 }}>{day.netAmount.toLocaleString()}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </div>
  );
}

function Metric({ label, value, highlighted = false }: { label: string; value: string; highlighted?: boolean }) {
  return <div><p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{label}</p><p style={{ fontSize: '20px', fontWeight: 700, color: highlighted ? 'var(--itunda-blue)' : undefined }}>{value}</p></div>;
}
