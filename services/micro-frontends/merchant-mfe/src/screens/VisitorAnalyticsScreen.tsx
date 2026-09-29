import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import { fetchProfileViewTrend, type MerchantProfileView } from '../lib/visitorAnalytics';

// Real 비즈프로필 (Karrot Business Profile) visitor-count trend (itunda Hood redesign,
// 2026-08-28) -- see lib/visitorAnalytics.ts's own doc comment. Plain English copy for
// now, not yet run through this file's own translations.ts pass (that file is at its
// frozen line-count baseline; per CLAUDE.md, extracting locale dicts into their own
// files to make room is a real, separate follow-up, not something to rush here).
export default function VisitorAnalyticsScreen() {
  const [trend, setTrend] = useState<MerchantProfileView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    fetchProfileViewTrend(7)
      .then(setTrend)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Couldn't load visitor data."));
  }, []);

  const totalVisits = trend?.reduce((sum, d) => sum + d.viewCount, 0) ?? 0;
  const maxCount = Math.max(1, ...(trend ?? []).map((d) => d.viewCount));

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Visitors</h2>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          Real visits to your business profile, counted whenever a shopper opens your place on Maps.
        </p>
        {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}
        {trend === null ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>Loading…</p>
        ) : trend.length === 0 || totalVisits === 0 ? (
          <EmptyState message="No real visits yet -- once shoppers open your place on Maps, they'll show up here." />
        ) : (
          <>
            <p style={{ fontSize: '28px', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '12px' }}>{totalVisits} <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--itunda-grey-500)' }}>visits in the last 7 days</span></p>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: '6px', height: '80px', marginBottom: '14px' }}>
              {trend.map((d) => (
                <div key={d.viewDate} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                  <div style={{ width: '100%', height: `${Math.max(4, (d.viewCount / maxCount) * 100)}%`, background: 'var(--itunda-indigo)', borderRadius: '4px' }} />
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {trend.map((d) => (
                <div key={d.viewDate} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                  <span style={{ color: 'var(--itunda-grey-500)' }}>{new Date(d.viewDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}</span>
                  <span style={{ fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{d.viewCount}</span>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
