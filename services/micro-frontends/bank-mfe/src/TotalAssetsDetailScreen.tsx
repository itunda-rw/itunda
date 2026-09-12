import { useEffect, useState } from 'react';
import { IconBack } from './icons/ItundaIcons';
import { fetchNetWorthHistory, type NetWorthHistoryPoint, type Overview } from './lib/overview';
import { useI18n } from './i18n/I18nContext';
import { useCountUp } from './hooks/useCountUp';

// Real Toss "총자산" detail screen (2026-09-12, direct user-supplied screenshots) --
// tapping the net worth header on OverviewAssetsView opens this: a proportional
// breakdown bar + percentage list of the SAME two categories NetWorthSnapshot
// tracks on the backend (account balances + reward points -- see that entity's own
// doc comment for why savings/investments/loans are deliberately excluded, matching
// Toss's own real disclosure on this exact screen), plus the "자산 변화" monthly
// trend chart backed by the new GET /api/v1/overview/net-worth-history endpoint.
//
// Deliberately does NOT reproduce Toss's own "만원 단위" (10,000-KRW-unit) chart
// scaling -- itunda has no equivalent denomination convention, and this codebase's
// own established money format already shows a full raw RWF figure
// (project_itunda_money_formatting_sweep) rather than inventing a new unit.

const LOCALE_TAG: Record<string, string> = { en: 'en-US', fr: 'fr-FR', rw: 'rw-RW' };

function monthLabel(month: string, locale: string): string {
  const [year, monthNum] = month.split('-').map(Number);
  const date = new Date(year, monthNum - 1, 1);
  try {
    return new Intl.DateTimeFormat(LOCALE_TAG[locale] ?? 'en-US', { month: 'short' }).format(date);
  } catch {
    return month;
  }
}

function BreakdownBar({ segments }: { segments: { label: string; amount: number; percent: number; color: string }[] }) {
  return (
    <div>
      <div style={{ display: 'flex', width: '100%', height: '10px', borderRadius: '5px', overflow: 'hidden', marginBottom: '16px' }}>
        {segments.map((s) => (
          <div key={s.label} style={{ width: `${s.percent}%`, backgroundColor: s.color }} />
        ))}
      </div>
      {segments.map((s) => (
        <div key={s.label} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '10px 0' }}>
          <div style={{ width: '10px', height: '10px', borderRadius: '50%', backgroundColor: s.color, flexShrink: 0 }} />
          <div style={{ flex: 1 }}>
            <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{s.label}</p>
            <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{s.percent}%</p>
          </div>
          <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{s.amount.toLocaleString('en-US')} RWF</p>
        </div>
      ))}
    </div>
  );
}

function TrendChart({ history, locale }: { history: NetWorthHistoryPoint[]; locale: string }) {
  const max = Math.max(...history.map((h) => h.liquidTotal), 1);
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '10px', height: '160px', padding: '16px 0' }}>
      {history.map((h, i) => {
        const isLatest = i === history.length - 1;
        return (
          <div key={h.month} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%' }}>
            <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-11-size)', color: isLatest ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)', fontWeight: isLatest ? 700 : 400 }}>
              {h.liquidTotal.toLocaleString('en-US')}
            </p>
            <div style={{ flex: 1, display: 'flex', alignItems: 'flex-end', width: '100%' }}>
              <div
                style={{
                  width: '100%',
                  height: `${Math.max((h.liquidTotal / max) * 100, 4)}%`,
                  borderRadius: '4px 4px 0 0',
                  backgroundColor: isLatest ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)',
                }}
              />
            </div>
            <p style={{ margin: '6px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{monthLabel(h.month, locale)}</p>
          </div>
        );
      })}
    </div>
  );
}

export function TotalAssetsDetailScreen({ overview, onBack }: { overview: Overview; onBack: () => void }) {
  const { t, locale } = useI18n();
  const [history, setHistory] = useState<NetWorthHistoryPoint[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchNetWorthHistory()
      .then(setHistory)
      .catch(() => setError(t('totalAssets.historyError')));
  }, [t]);

  const accountsTotal = overview.accounts.reduce((sum, a) => sum + a.balance, 0);
  const pointsTotal = overview.points.rewardsTotal;
  const trackedTotal = accountsTotal + pointsTotal;
  const animatedTotal = useCountUp(trackedTotal);

  const segments = trackedTotal > 0
    ? [
        { label: t('totalAssets.accounts'), amount: accountsTotal, percent: Math.round((accountsTotal / trackedTotal) * 100), color: 'var(--itunda-indigo)' },
        { label: t('totalAssets.points'), amount: pointsTotal, percent: Math.round((pointsTotal / trackedTotal) * 100), color: 'var(--itunda-green)' },
      ].filter((s) => s.amount > 0)
    : [];

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>
        <div style={{ padding: '4px 20px 24px' }}>
          <p style={{ margin: '0 0 6px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('totalAssets.currentTotal')}</p>
          <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{animatedTotal.toLocaleString('en-US')} RWF</p>
        </div>
        <div style={{ padding: '0 20px 8px' }}>
          {segments.length > 0 ? (
            <BreakdownBar segments={segments} />
          ) : (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('totalAssets.empty')}</p>
          )}
        </div>

        <div style={{ height: '8px', backgroundColor: 'var(--itunda-grey-50)', margin: '16px 0' }} />

        <div style={{ padding: '0 20px 24px' }}>
          <p style={{ margin: '0 0 4px', fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{t('totalAssets.trendTitle')}</p>
          {error ? (
            <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>{error}</p>
          ) : history === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
          ) : history.length === 0 ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', padding: '24px 0' }}>{t('totalAssets.trendEmpty')}</p>
          ) : (
            <TrendChart history={history} locale={locale} />
          )}
          <p style={{ margin: '8px 0 4px', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)' }}>{t('totalAssets.trendDisclosureA')}</p>
          <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)' }}>{t('totalAssets.trendDisclosureB')}</p>
        </div>
      </div>
    </div>
  );
}
