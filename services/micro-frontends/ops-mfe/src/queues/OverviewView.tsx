import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import {
  fetchPaymentRails, fetchSystemDashboard, testMtnMomoConnectivity,
  type MtnMomoConnectivityResult, type PaymentRail, type SystemDashboard,
} from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

// Real system overview + per-rail health -- see queues.ts's own doc comment: both
// endpoints already computed real data (SystemController.kt) but had no caller anywhere
// in bank-mfe/merchant-mfe/Android/iOS/ops-mfe until this fresh endpoint-coverage sweep
// found them. useQueue expects a fetcher returning an array, so both single-object
// endpoints are wrapped to fit that shape (a 1-element array), reusing the same
// skeleton/error/reload plumbing every other tab already has instead of hand-rolling a
// separate loading pattern just for this view.
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

function RailRow({ rail }: { rail: PaymentRail }) {
  const isHealthy = rail.status === 'HEALTHY';
  return (
    <div
      className="itunda-card"
      style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px' }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
        <span
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: isHealthy ? 'var(--itunda-green)' : 'var(--itunda-red)',
            display: 'inline-block',
          }}
        />
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{rail.displayName}</p>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
        {rail.totalAttempts} attempts · {(rail.successRate * 100).toFixed(1)}% success · {rail.avgLatencyMs}ms avg
      </p>
      <span style={{ fontSize: '13px', fontWeight: 600, color: isHealthy ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>
        {isHealthy ? 'Healthy' : 'Incident'}
      </span>
    </div>
  );
}

// Real, live external diagnostic -- see lib/queues.ts's own doc comment: fully built
// on the backend (SystemController.testMtnMomoConnectivity), found via
// scripts/uncalled-endpoint-sweep.py with zero caller anywhere. An admin previously
// had no way to run this check except raw curl/Postman.
function MtnMomoConnectivityCard() {
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<MtnMomoConnectivityResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const runTest = async () => {
    setTesting(true);
    setError(null);
    setResult(null);
    try {
      const res = await testMtnMomoConnectivity();
      setResult(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reach the MTN MoMo sandbox.');
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="itunda-card" style={{ marginBottom: '28px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>MTN MoMo sandbox connectivity</p>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            Makes a real, live call to the sandbox -- not wired into any real transfer/bill flow.
          </p>
        </div>
        <button
          className="itunda-btn itunda-btn-secondary"
          onClick={runTest}
          disabled={testing}
          style={{ padding: '8px 16px', fontSize: '13px' }}
        >
          {testing ? 'Testing…' : 'Run test'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginTop: '12px' }} role="alert">{error}</p>}
      {result && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-green)', marginTop: '12px' }}>
          {result.status} · {result.latencyMs}ms · ref {result.referenceId}
        </p>
      )}
    </div>
  );
}

export default function OverviewView() {
  const dashboard = useSingle<SystemDashboard>(fetchSystemDashboard);
  const rails = useQueue(fetchPaymentRails);

  return (
    <div>
      <QueueHeader
        title="Overview"
        count={null}
        onReload={() => {
          dashboard.reload();
          rails.reload();
        }}
        refreshing={dashboard.refreshing || rails.refreshing}
      />

      {dashboard.error && <QueueError message={dashboard.error} onRetry={dashboard.reload} />}
      {!dashboard.error && dashboard.value === null && <QueueSkeleton />}
      {!dashboard.error && dashboard.value && (
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', marginBottom: '28px' }}>
          <StatCard label="Today's volume" value={`${dashboard.value.operations.todayVolume.toLocaleString('en-US')} ${dashboard.value.currency}`} />
          <StatCard label="Today's completed transactions" value={dashboard.value.operations.todayCompletedTransactionCount.toLocaleString('en-US')} />
          <StatCard label="Active linked-account consents" value={dashboard.value.operatingLayer.activeConsents.toLocaleString('en-US')} />
        </div>
      )}

      <MtnMomoConnectivityCard />

      <h3 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '12px' }}>Payment rail health</h3>
      {rails.error && <QueueError message={rails.error} onRetry={rails.reload} />}
      {!rails.error && rails.items === null && <QueueSkeleton />}
      {!rails.error && rails.items !== null && rails.items.length === 0 && <QueueEmpty label="No rail activity recorded yet." />}
      {!rails.error && rails.items !== null && rails.items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {rails.items.map((rail) => (
            <RailRow key={rail.railId} rail={rail} />
          ))}
        </div>
      )}
    </div>
  );
}
