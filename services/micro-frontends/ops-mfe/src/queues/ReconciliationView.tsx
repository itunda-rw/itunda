import { useCallback, useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { fetchReconciliation } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export default function ReconciliationView() {
  const [date, setDate] = useState(todayIso());
  const fetcher = useCallback(() => fetchReconciliation(date), [date]);
  const { items, error, refreshing, reload } = useQueue(fetcher);

  return (
    <div>
      <QueueHeader title="Reconciliation" count={null} onReload={reload} refreshing={refreshing}>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          style={{ padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </QueueHeader>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
        Reconciles itunda's own provider-attempt log against itself, per rail, for the selected day.
        There's no external settlement file to diff against yet, so this is one-sided.
      </p>
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No provider attempts recorded for this date." />}
      {!error && items !== null && items.length > 0 && (
        <div className="toss-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--toss-grey-100)', textAlign: 'left' }}>
                {['Rail', 'Attempts', 'Success', 'Failure', 'Success rate', 'Avg latency'].map((h) => (
                  <th key={h} style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.railId} style={{ borderTop: '1px solid var(--toss-grey-200)' }}>
                  <td style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>{row.displayName}</td>
                  <td style={{ padding: '12px 16px' }}>{row.totalAttempts}</td>
                  <td style={{ padding: '12px 16px', color: 'var(--toss-green)' }}>{row.successCount}</td>
                  <td style={{ padding: '12px 16px', color: row.failureCount > 0 ? '#E53935' : 'var(--toss-grey-500)' }}>
                    {row.failureCount}
                  </td>
                  <td style={{ padding: '12px 16px' }}>{(row.successRate * 100).toFixed(1)}%</td>
                  <td style={{ padding: '12px 16px' }}>{row.avgLatencyMs.toFixed(0)}ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
