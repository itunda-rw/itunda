import { useQueue } from '../hooks/useQueue';
import { fetchAnalyticsSummary, type AnalyticsSummary } from '../lib/analytics';
import { QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

// Real, orphaned ADMIN endpoint -- see lib/analytics.ts's own doc comment. Small
// local duplicate of useSingle/StatCard (OverviewView.tsx's own, not exported),
// matching this codebase's established duplicate-small-utility convention rather
// than coupling two otherwise-independent ops-mfe views together.
function useSingle<T>(fetcher: () => Promise<T>) {
  const wrapped = () => fetcher().then((value) => [value]);
  const { items, error, refreshing, reload } = useQueue(wrapped);
  return { value: items?.[0] ?? null, error, refreshing, reload };
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="itunda-card" style={{ flex: 1, minWidth: '180px' }}>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>{label}</p>
      <p style={{ fontSize: '22px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{value}</p>
    </div>
  );
}

const EVENT_LABELS: Record<string, string> = {
  home_view: 'Home views',
  coop_rail_tap: 'Cooperative-savings rail taps',
  discover_banner_impression: 'Discover banner impressions',
};

export default function AnalyticsView() {
  const summary = useSingle<AnalyticsSummary>(() => fetchAnalyticsSummary(30));

  return (
    <div>
      <QueueHeader title="Analytics" count={null} onReload={summary.reload} refreshing={summary.refreshing} />
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        A real, deliberately minimal usage summary (last 30 days) -- see AnalyticsController.kt's own doc comment
        for the honest scope boundary. Not a general-purpose event platform.
      </p>

      {summary.error && <QueueError message={summary.error} onRetry={summary.reload} />}
      {!summary.error && summary.value === null && <QueueSkeleton />}
      {!summary.error && summary.value && (
        <>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginBottom: '20px' }}>
            <StatCard label="Total active users" value={summary.value.totalActiveUsers.toLocaleString('en-US')} />
            {Object.entries(summary.value.events).map(([eventName, counts]) => (
              <StatCard
                key={eventName}
                label={EVENT_LABELS[eventName] ?? eventName}
                value={`${counts.totalEvents.toLocaleString('en-US')} (${counts.distinctUsers.toLocaleString('en-US')} users)`}
              />
            ))}
          </div>

          <h3 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '12px' }}>
            Cooperative-savings rail return rate
          </h3>
          <div className="itunda-card">
            <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>
              Of users who tapped the coop-savings rail, the fraction who had any activity at least 24h later.
            </p>
            <p style={{ fontSize: '22px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
              {summary.value.coopRailReturnRate.rate === null
                ? 'No taps yet'
                : `${(summary.value.coopRailReturnRate.rate * 100).toFixed(1)}%`}
            </p>
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
              {summary.value.coopRailReturnRate.returnedNextDayOrLater} of {summary.value.coopRailReturnRate.usersWhoTapped} tappers returned
            </p>
          </div>
        </>
      )}
    </div>
  );
}
